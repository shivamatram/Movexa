package com.example.movexa.ui.fleet

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.enums.VerificationStatus
import com.example.movexa.data.remote.FirebaseProvider
import com.example.movexa.data.repository.contracts.DriverRepository
import com.example.movexa.data.repository.contracts.VehicleRepository
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for the Drivers tab in Fleet Management.
 *
 * Responsibilities:
 * - Real-time driver list observation via Firestore snapshots
 * - Client-side search and status group filter
 * - Driver verification (approve/reject)
 * - Driver blocking/unblocking
 * - Bidirectional driver↔vehicle assignment/unassignment
 * - Driver name resolution from users collection
 *
 * Status groups: All, Active (approved+!blocked), Pending (pending/under_review),
 *                Blocked (blocked=true), Unassigned (approved+!blocked+no vehicle)
 */
class DriverListViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────
    private val driverRepository: DriverRepository = DriverRepositoryImpl()
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    /** Raw driver list from Firestore (unfiltered). */
    private val _allDrivers = MutableStateFlow<ResultState<List<Driver>>>(ResultState.Loading)

    /** Filtered drivers displayed in the UI. */
    private val _drivers = MutableStateFlow<ResultState<List<Driver>>>(ResultState.Loading)
    val drivers: StateFlow<ResultState<List<Driver>>> = _drivers.asStateFlow()

    /** Current status group filter. */
    private val _driverFilter = MutableStateFlow(DriverFilterGroup.ALL)
    val driverFilter: StateFlow<DriverFilterGroup> = _driverFilter.asStateFlow()

    /** Current search query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Single-shot operation result. */
    private val _operationResult = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val operationResult: StateFlow<ResultState<String>> = _operationResult.asStateFlow()

    /** Driver count after filtering. */
    private val _driverCount = MutableStateFlow(0)
    val driverCount: StateFlow<Int> = _driverCount.asStateFlow()

    /** Cache of driver userId → user displayName for UI display. */
    private val _driverNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val driverNames: StateFlow<Map<String, String>> = _driverNames.asStateFlow()

    private var currentCompanyId: String? = null

    // ── Filter Groups ───────────────────────────────────────────

    enum class DriverFilterGroup {
        ALL,
        ACTIVE,      // approved + !blocked
        PENDING,     // PENDING or UNDER_REVIEW status
        BLOCKED,     // blocked = true
        UNASSIGNED   // approved + !blocked + no assignedVehicleId
    }

    // ── Initialization ──────────────────────────────────────────

    /**
     * Start observing drivers. Call from fragment's initViews().
     * Fetches all drivers regardless of companyId so both admin and manager
     * can see pending driver accounts for verification.
     */
    fun loadDrivers() {
        viewModelScope.launch {
            // Cache userId for vehicle-assignment queries (getAvailableVehicles)
            currentCompanyId = SessionManager.getInstance().getCachedUserId()
            observeAllDrivers()
        }
    }

    /**
     * Refresh driver data (pull-to-refresh).
     */
    fun refreshDrivers() {
        _allDrivers.value = ResultState.Loading
        _drivers.value = ResultState.Loading
        observeAllDrivers()
    }

    // ── Real-Time Observation ───────────────────────────────────

    private fun observeAllDrivers() {
        viewModelScope.launch {
            driverRepository.observeAllDrivers()
                .catch { e ->
                    _drivers.value = ResultState.Error(
                        message = e.message ?: "Failed to load drivers",
                        exception = e
                    )
                }
                .collect { result ->
                    _allDrivers.value = result
                    applyFilters()

                    // Resolve driver names from users collection
                    if (result is ResultState.Success) {
                        resolveDriverNames(result.data)
                    }
                }
        }
    }

    // ── Name Resolution ─────────────────────────────────────────

    /**
     * Fetch user display names for all drivers.
     * Uses Firestore users collection since Driver model doesn't include name.
     */
    private fun resolveDriverNames(drivers: List<Driver>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nameMap = mutableMapOf<String, String>()
                val existingNames = _driverNames.value

                for (driver in drivers) {
                    if (driver.userId.isBlank()) continue
                    // Skip if already cached
                    if (existingNames.containsKey(driver.driverId)) {
                        nameMap[driver.driverId] = existingNames[driver.driverId]!!
                        continue
                    }

                    try {
                        val doc = FirebaseProvider.firestore
                            .collection(User.COLLECTION_NAME)
                            .document(driver.userId)
                            .get()
                            .await()

                        if (doc.exists()) {
                            val user = User.fromMap(doc.data ?: emptyMap())
                            nameMap[driver.driverId] = user.displayName
                        } else {
                            nameMap[driver.driverId] = "Driver ${driver.driverId.take(6)}"
                        }
                    } catch (e: Exception) {
                        nameMap[driver.driverId] = "Driver ${driver.driverId.take(6)}"
                    }
                }

                _driverNames.value = nameMap
            } catch (e: Exception) {
                // Silently fail name resolution, use fallback
            }
        }
    }

    /**
     * Get display name for a driver (from cache or fallback).
     */
    fun getDriverName(driverId: String): String {
        return _driverNames.value[driverId] ?: "Driver"
    }

    // ── Filtering ───────────────────────────────────────────────

    /**
     * Set the status group filter.
     */
    fun setDriverFilter(filter: DriverFilterGroup) {
        _driverFilter.value = filter
        applyFilters()
    }

    /**
     * Set the search query.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    /**
     * Apply search + status group filter to the raw driver list.
     */
    private fun applyFilters() {
        val currentState = _allDrivers.value
        if (currentState !is ResultState.Success) {
            _drivers.value = currentState
            return
        }

        val allDrivers = currentState.data
        val filter = _driverFilter.value
        val searchQuery = _searchQuery.value.trim().lowercase()
        val names = _driverNames.value

        val filtered = allDrivers.filter { driver ->
            // Status group filter
            val matchesFilter = when (filter) {
                DriverFilterGroup.ALL -> true
                DriverFilterGroup.ACTIVE ->
                    driver.verificationStatus.isApproved() && !driver.blocked
                DriverFilterGroup.PENDING ->
                    driver.verificationStatus == VerificationStatus.PENDING ||
                            driver.verificationStatus == VerificationStatus.UNDER_REVIEW
                DriverFilterGroup.BLOCKED -> driver.blocked
                DriverFilterGroup.UNASSIGNED ->
                    driver.verificationStatus.isApproved() &&
                            !driver.blocked &&
                            driver.assignedVehicleId.isNullOrBlank()
            }

            // Search filter (license, name, blood group)
            val driverName = names[driver.driverId]?.lowercase() ?: ""
            val matchesSearch = searchQuery.isBlank() ||
                    driver.licenseNumber.lowercase().contains(searchQuery) ||
                    driverName.contains(searchQuery) ||
                    driver.bloodGroup.lowercase().contains(searchQuery)

            matchesFilter && matchesSearch
        }

        _driverCount.value = filtered.size
        _drivers.value = ResultState.Success(filtered)
    }

    // ── Verification Actions ────────────────────────────────────

    /**
     * Approve a driver's verification.
     */
    fun verifyDriver(driverId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = driverRepository.updateVerificationStatus(
                    driverId, VerificationStatus.APPROVED
                )) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Driver verified successfully")
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to verify driver", e
                )
            }
        }
    }

    /**
     * Reject a driver's verification.
     */
    fun rejectDriver(driverId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = driverRepository.updateVerificationStatus(
                    driverId, VerificationStatus.REJECTED
                )) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Driver rejected")
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to reject driver", e
                )
            }
        }
    }

    // ── Block/Unblock ───────────────────────────────────────────

    /**
     * Block a driver.
     */
    fun blockDriver(driverId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Also unassign vehicle when blocking
                val driverResult = driverRepository.getDriverById(driverId)
                val driver = (driverResult as? ResultState.Success)?.data
                if (!driver?.assignedVehicleId.isNullOrBlank()) {
                    vehicleRepository.unassignDriverFromVehicle(driver!!.assignedVehicleId!!)
                    driverRepository.unassignVehicleFromDriver(driverId)
                }

                when (val result = driverRepository.blockDriver(driverId)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Driver blocked successfully")
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to block driver", e
                )
            }
        }
    }

    /**
     * Unblock a driver.
     */
    fun unblockDriver(driverId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = driverRepository.unblockDriver(driverId)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Driver unblocked successfully")
                    }
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to unblock driver", e
                )
            }
        }
    }

    // ── Assignment ──────────────────────────────────────────────

    /**
     * Assign a vehicle to a driver (bidirectional).
     */
    fun assignVehicleToDriver(driverId: String, vehicleId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Unassign previous vehicle from this driver if any
                val driverResult = driverRepository.getDriverById(driverId)
                val driver = (driverResult as? ResultState.Success)?.data
                if (driver?.assignedVehicleId != null && driver.assignedVehicleId != vehicleId) {
                    vehicleRepository.unassignDriverFromVehicle(driver.assignedVehicleId)
                }

                // Unassign previous driver from this vehicle if any
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                val vehicle = (vehicleResult as? ResultState.Success)?.data
                if (vehicle?.assignedDriverId != null && vehicle.assignedDriverId != driverId) {
                    driverRepository.unassignVehicleFromDriver(vehicle.assignedDriverId)
                }

                // Create bidirectional assignment
                val driverAssign = driverRepository.assignVehicleToDriver(driverId, vehicleId)
                val vehicleAssign = vehicleRepository.assignDriverToVehicle(vehicleId, driverId)

                if (driverAssign is ResultState.Success && vehicleAssign is ResultState.Success) {
                    _operationResult.value = ResultState.Success("Vehicle assigned successfully")
                } else {
                    val errorMsg = (driverAssign as? ResultState.Error)?.message
                        ?: (vehicleAssign as? ResultState.Error)?.message
                        ?: "Assignment failed"
                    _operationResult.value = ResultState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to assign vehicle", e
                )
            }
        }
    }

    /**
     * Unassign a vehicle from a driver (bidirectional).
     */
    fun unassignVehicleFromDriver(driverId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val driverResult = driverRepository.getDriverById(driverId)
                val driver = (driverResult as? ResultState.Success)?.data
                val vehicleId = driver?.assignedVehicleId

                driverRepository.unassignVehicleFromDriver(driverId)

                if (!vehicleId.isNullOrBlank()) {
                    vehicleRepository.unassignDriverFromVehicle(vehicleId)
                }

                _operationResult.value = ResultState.Success("Vehicle unassigned successfully")
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to unassign vehicle", e
                )
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Get available vehicles for the assignment bottom sheet.
     * Queries all vehicles without companyId filter since vehicle documents may have empty companyId.
     */
    fun getAvailableVehicles(callback: (List<AssignmentBottomSheet.AssignmentOption>) -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                when (val result = vehicleRepository.getAllVehicles()) {
                    is ResultState.Success -> {
                        val options = result.data
                            .filter { it.assignedDriverId.isNullOrBlank() }
                            .map { vehicle ->
                                AssignmentBottomSheet.AssignmentOption(
                                    id = vehicle.vehicleId,
                                    title = vehicle.number,
                                    subtitle = "${vehicle.type.displayName} · ${vehicle.make} ${vehicle.model}".trim()
                                )
                            }
                        callback(options)
                    }
                    else -> callback(emptyList())
                }
            } catch (e: Exception) {
                callback(emptyList())
            }
        }
    }

    /**
     * Clear the operation result after it's been consumed.
     */
    fun clearOperationResult() {
        _operationResult.value = ResultState.Idle
    }
}
