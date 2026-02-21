package com.example.movexa.ui.fleet

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.model.enums.VehicleType
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

/**
 * ViewModel for the Vehicles tab in Fleet Management.
 *
 * Responsibilities:
 * - Real-time fleet observation via Firestore snapshots
 * - Client-side search and status filter
 * - Vehicle CRUD (create, update, delete)
 * - Vehicle status changes
 * - Bidirectional driver↔vehicle assignment/unassignment
 * - Duplicate vehicle number validation
 */
class VehicleListViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val driverRepository: DriverRepository = DriverRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    /** Raw vehicle list from Firestore (unfiltered). */
    private val _allVehicles = MutableStateFlow<ResultState<List<Vehicle>>>(ResultState.Loading)

    /** Filtered vehicles displayed in the UI. */
    private val _vehicles = MutableStateFlow<ResultState<List<Vehicle>>>(ResultState.Loading)
    val vehicles: StateFlow<ResultState<List<Vehicle>>> = _vehicles.asStateFlow()

    /** Currently selected status filter. Null = "All". */
    private val _statusFilter = MutableStateFlow<VehicleStatus?>(null)
    val statusFilter: StateFlow<VehicleStatus?> = _statusFilter.asStateFlow()

    /** Current search query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Single-shot operation result (add/edit/delete/assign). */
    private val _operationResult = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val operationResult: StateFlow<ResultState<String>> = _operationResult.asStateFlow()

    /** Total vehicle count (for display). */
    private val _vehicleCount = MutableStateFlow(0)
    val vehicleCount: StateFlow<Int> = _vehicleCount.asStateFlow()

    private var currentCompanyId: String? = null

    // ── Initialization ──────────────────────────────────────────

    /**
     * Start observing the fleet. Call from fragment's initViews().
     */
    fun loadVehicles() {
        viewModelScope.launch {
            val companyId = SessionManager.getInstance().getCachedUserId()
            if (companyId.isNullOrBlank()) {
                _vehicles.value = ResultState.Error("No company ID found. Please log in again.")
                return@launch
            }
            currentCompanyId = companyId
            observeFleet(companyId)
        }
    }

    /**
     * Refresh fleet data (pull-to-refresh).
     */
    fun refreshVehicles() {
        val companyId = currentCompanyId ?: return
        _allVehicles.value = ResultState.Loading
        _vehicles.value = ResultState.Loading
        observeFleet(companyId)
    }

    // ── Real-Time Observation ───────────────────────────────────

    private fun observeFleet(companyId: String) {
        viewModelScope.launch {
            vehicleRepository.observeFleet(companyId)
                .catch { e ->
                    _vehicles.value = ResultState.Error(
                        message = e.message ?: "Failed to load vehicles",
                        exception = e
                    )
                }
                .collect { result ->
                    _allVehicles.value = result
                    applyFilters()
                }
        }
    }

    // ── Filtering ───────────────────────────────────────────────

    /**
     * Set the status filter chip. Null = "All".
     */
    fun setStatusFilter(status: VehicleStatus?) {
        _statusFilter.value = status
        applyFilters()
    }

    /**
     * Set the search query from the search bar.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    /**
     * Apply both search and status filter to the raw vehicle list.
     */
    private fun applyFilters() {
        val currentState = _allVehicles.value
        if (currentState !is ResultState.Success) {
            _vehicles.value = currentState
            return
        }

        val allVehicles = currentState.data
        val statusFilter = _statusFilter.value
        val searchQuery = _searchQuery.value.trim().lowercase()

        val filtered = allVehicles.filter { vehicle ->
            // Status filter
            val matchesStatus = statusFilter == null || vehicle.status == statusFilter

            // Search filter (number, make, model, type)
            val matchesSearch = searchQuery.isBlank() ||
                    vehicle.number.lowercase().contains(searchQuery) ||
                    vehicle.make.lowercase().contains(searchQuery) ||
                    vehicle.model.lowercase().contains(searchQuery) ||
                    vehicle.type.displayName.lowercase().contains(searchQuery)

            matchesStatus && matchesSearch
        }

        _vehicleCount.value = filtered.size
        _vehicles.value = ResultState.Success(filtered)
    }

    // ── CRUD Operations ─────────────────────────────────────────

    /**
     * Add a new vehicle from form data.
     */
    fun addVehicle(data: Map<String, Any?>) {
        val companyId = currentCompanyId ?: return
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vehicle = Vehicle(
                    number = data["number"] as? String ?: "",
                    type = data["type"] as? VehicleType ?: VehicleType.OTHER,
                    capacity = data["capacity"] as? Int ?: 0,
                    make = data["make"] as? String ?: "",
                    model = data["model"] as? String ?: "",
                    year = data["year"] as? Int ?: 0,
                    fuelType = data["fuelType"] as? String ?: "",
                    companyId = companyId,
                    status = VehicleStatus.AVAILABLE,
                    documentsValid = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                when (val result = vehicleRepository.createVehicle(vehicle)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Vehicle added successfully")
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
                    e.message ?: "Failed to add vehicle", e
                )
            }
        }
    }

    /**
     * Update an existing vehicle from form data.
     */
    fun updateVehicle(data: Map<String, Any?>) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vehicleId = data["vehicleId"] as? String ?: return@launch
                val existingResult = vehicleRepository.getVehicleById(vehicleId)
                val existing = (existingResult as? ResultState.Success)?.data ?: return@launch

                val updated = existing.copy(
                    type = data["type"] as? VehicleType ?: existing.type,
                    capacity = data["capacity"] as? Int ?: existing.capacity,
                    make = data["make"] as? String ?: existing.make,
                    model = data["model"] as? String ?: existing.model,
                    year = data["year"] as? Int ?: existing.year,
                    fuelType = data["fuelType"] as? String ?: existing.fuelType,
                    updatedAt = System.currentTimeMillis()
                )

                when (val result = vehicleRepository.updateVehicle(updated)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Vehicle updated successfully")
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
                    e.message ?: "Failed to update vehicle", e
                )
            }
        }
    }

    /**
     * Delete a vehicle by ID.
     */
    fun deleteVehicle(vehicleId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Unassign driver first if assigned
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                val vehicle = (vehicleResult as? ResultState.Success)?.data
                if (vehicle?.assignedDriverId != null) {
                    driverRepository.unassignVehicleFromDriver(vehicle.assignedDriverId)
                }

                when (val result = vehicleRepository.deleteVehicle(vehicleId)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success("Vehicle deleted successfully")
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
                    e.message ?: "Failed to delete vehicle", e
                )
            }
        }
    }

    // ── Status Change ───────────────────────────────────────────

    /**
     * Change the status of a vehicle.
     */
    fun changeVehicleStatus(vehicleId: String, newStatus: VehicleStatus) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = vehicleRepository.updateVehicleStatus(vehicleId, newStatus)) {
                    is ResultState.Success -> {
                        _operationResult.value = ResultState.Success(
                            "Status changed to ${newStatus.displayName}"
                        )
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
                    e.message ?: "Failed to change status", e
                )
            }
        }
    }

    // ── Assignment ──────────────────────────────────────────────

    /**
     * Assign a driver to a vehicle (bidirectional update).
     */
    fun assignDriverToVehicle(vehicleId: String, driverId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Unassign previous driver from this vehicle if any
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                val vehicle = (vehicleResult as? ResultState.Success)?.data
                if (vehicle?.assignedDriverId != null && vehicle.assignedDriverId != driverId) {
                    driverRepository.unassignVehicleFromDriver(vehicle.assignedDriverId)
                }

                // Step 2: Unassign previous vehicle from this driver if any
                val driverResult = driverRepository.getDriverById(driverId)
                val driver = (driverResult as? ResultState.Success)?.data
                if (driver?.assignedVehicleId != null && driver.assignedVehicleId != vehicleId) {
                    vehicleRepository.unassignDriverFromVehicle(driver.assignedVehicleId)
                }

                // Step 3: Create bidirectional assignment
                val vehicleAssign = vehicleRepository.assignDriverToVehicle(vehicleId, driverId)
                val driverAssign = driverRepository.assignVehicleToDriver(driverId, vehicleId)

                if (vehicleAssign is ResultState.Success && driverAssign is ResultState.Success) {
                    _operationResult.value = ResultState.Success("Driver assigned successfully")
                } else {
                    val errorMsg = (vehicleAssign as? ResultState.Error)?.message
                        ?: (driverAssign as? ResultState.Error)?.message
                        ?: "Assignment failed"
                    _operationResult.value = ResultState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to assign driver", e
                )
            }
        }
    }

    /**
     * Unassign a driver from a vehicle (bidirectional update).
     */
    fun unassignDriverFromVehicle(vehicleId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Find the currently assigned driver
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                val vehicle = (vehicleResult as? ResultState.Success)?.data
                val driverId = vehicle?.assignedDriverId

                // Unassign from vehicle
                vehicleRepository.unassignDriverFromVehicle(vehicleId)

                // Unassign from driver
                if (!driverId.isNullOrBlank()) {
                    driverRepository.unassignVehicleFromDriver(driverId)
                }

                _operationResult.value = ResultState.Success("Driver unassigned successfully")
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to unassign driver", e
                )
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Check if a vehicle number already exists (for add form).
     */
    fun checkVehicleNumberExists(number: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = vehicleRepository.vehicleNumberExists(number)) {
                    is ResultState.Success -> callback(result.data)
                    else -> callback(false)
                }
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    /**
     * Get list of unassigned drivers for the assignment bottom sheet.
     */
    fun getUnassignedDrivers(callback: (List<AssignmentBottomSheet.AssignmentOption>) -> Unit) {
        val companyId = currentCompanyId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = driverRepository.getUnassignedDrivers(companyId)) {
                    is ResultState.Success -> {
                        val options = result.data.map { driver ->
                            AssignmentBottomSheet.AssignmentOption(
                                id = driver.driverId,
                                title = driver.licenseNumber,
                                subtitle = "Rating: ${driver.ratingDisplay} · Trips: ${driver.totalTrips}"
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
