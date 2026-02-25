package com.example.movexa.ui.trips

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.TripEvent
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.enums.TripEventType
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.repository.contracts.DriverRepository
import com.example.movexa.data.repository.contracts.TripEventRepository
import com.example.movexa.data.repository.contracts.TripRepository
import com.example.movexa.data.repository.contracts.VehicleRepository
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.TripEventRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ViewModel for the Manager Trips screen.
 *
 * Responsibilities:
 * - Real-time company trip observation via Firestore snapshots
 * - Client-side filtering into tabs: unassigned, ongoing, completed
 * - Search filtering by tracking ID and address
 * - Trip creation with auto-generated tracking ID
 * - Smart assignment: find eligible vehicles (AVAILABLE + assignedDriver verified & not blocked)
 * - Trip assignment with vehicle status update + event logging
 * - Trip cancellation
 * - Vehicle/driver name resolution for UI display
 */
class ManagerTripsViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────
    private val tripRepository: TripRepository = TripRepositoryImpl()
    private val tripEventRepository: TripEventRepository = TripEventRepositoryImpl()
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val driverRepository: DriverRepository = DriverRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    /** Raw trip list from Firestore (unfiltered, all company trips). */
    private val _allTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)

    /** Unassigned trips (status = CREATED). */
    private val _unassignedTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val unassignedTrips: StateFlow<ResultState<List<Trip>>> = _unassignedTrips.asStateFlow()

    /** Ongoing trips (status = ASSIGNED, ACCEPTED, STARTED). */
    private val _ongoingTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val ongoingTrips: StateFlow<ResultState<List<Trip>>> = _ongoingTrips.asStateFlow()

    /** Completed trips (status = COMPLETED, CANCELLED, REJECTED_BY_DRIVER). */
    private val _completedTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val completedTrips: StateFlow<ResultState<List<Trip>>> = _completedTrips.asStateFlow()

    /** Search query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Single-shot operation result (create / assign / cancel). */
    private val _operationResult = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val operationResult: StateFlow<ResultState<String>> = _operationResult.asStateFlow()

    /** Counts for header badges. */
    private val _unassignedCount = MutableStateFlow(0)
    val unassignedCount: StateFlow<Int> = _unassignedCount.asStateFlow()

    private val _ongoingCount = MutableStateFlow(0)
    val ongoingCount: StateFlow<Int> = _ongoingCount.asStateFlow()

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    // ── UI state ───────────────────────────────────────────────
    /** Selected tab index in the manager trips screen (0/1/2). */
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    /** Eligible vehicles for smart assignment. */
    private val _eligibleOptions =
        MutableStateFlow<ResultState<List<SmartAssignOptionAdapter.EligibleOption>>>(ResultState.Idle)
    val eligibleOptions: StateFlow<ResultState<List<SmartAssignOptionAdapter.EligibleOption>>> =
        _eligibleOptions.asStateFlow()

    /** Cached vehicle/driver name lookups for UI. */
    private val vehicleNameCache = mutableMapOf<String, String>()
    private val driverNameCache = mutableMapOf<String, String>()

    private var currentCompanyId: String? = null

    // ── Initialization ──────────────────────────────────────────

    /**
     * Start observing company trips. Call from fragment's initViews().
     */
    fun loadTrips() {
        viewModelScope.launch {
            val companyId = SessionManager.getInstance().getCachedUserId()
            if (companyId.isNullOrBlank()) {
                val error = ResultState.Error("No company ID found. Please log in again.")
                _unassignedTrips.value = error
                _ongoingTrips.value = error
                _completedTrips.value = error
                return@launch
            }
            currentCompanyId = companyId
            observeCompanyTrips(companyId)
        }
    }

    /**
     * Refresh all trip data.
     */
    fun refreshTrips() {
        val companyId = currentCompanyId ?: return
        _allTrips.value = ResultState.Loading
        _unassignedTrips.value = ResultState.Loading
        _ongoingTrips.value = ResultState.Loading
        _completedTrips.value = ResultState.Loading
        observeCompanyTrips(companyId)
    }

    // ── Real-Time Observation ───────────────────────────────────

    private fun observeCompanyTrips(companyId: String) {
        viewModelScope.launch {
            tripRepository.observeActiveTrips(companyId)
                .catch { e ->
                    val error = ResultState.Error(
                        message = e.message ?: "Failed to load trips",
                        exception = e
                    )
                    _unassignedTrips.value = error
                    _ongoingTrips.value = error
                    _completedTrips.value = error
                }
                .collect { result ->
                    _allTrips.value = result
                    applyFilters()
                    // Resolve names for display
                    if (result is ResultState.Success) {
                        resolveNames(result.data)
                    }
                }
        }

        // Also observe completed/terminal trips separately
        viewModelScope.launch {
            tripRepository.getTripsByCompany(companyId).let { result ->
                if (result is ResultState.Success) {
                    val terminalTrips = result.data.filter { it.status.isTerminal }
                    _completedTrips.value = ResultState.Success(terminalTrips)
                    _completedCount.value = terminalTrips.size
                }
            }
        }
    }

    // ── Filtering ───────────────────────────────────────────────

    /**
     * Set the search query.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    /**
     * Apply search filter and categorize trips into tabs.
     */
    private fun applyFilters() {
        val currentState = _allTrips.value
        if (currentState !is ResultState.Success) {
            // Forward loading/error state
            if (currentState is ResultState.Loading) {
                _unassignedTrips.value = ResultState.Loading
                _ongoingTrips.value = ResultState.Loading
            }
            return
        }

        val allTrips = currentState.data
        val searchQuery = _searchQuery.value.trim().lowercase()

        // Apply search filter
        val filtered = if (searchQuery.isBlank()) {
            allTrips
        } else {
            allTrips.filter { trip ->
                trip.trackingId.lowercase().contains(searchQuery) ||
                trip.pickupAddress.lowercase().contains(searchQuery) ||
                trip.dropAddress.lowercase().contains(searchQuery) ||
                trip.notes.lowercase().contains(searchQuery)
            }
        }

        // Categorize into tabs
        val unassigned = filtered.filter { it.status == TripStatus.CREATED }
        val ongoing = filtered.filter {
            it.status == TripStatus.ASSIGNED ||
            it.status == TripStatus.ACCEPTED ||
            it.status == TripStatus.STARTED
        }
        val completed = filtered.filter { it.status.isTerminal }

        _unassignedTrips.value = ResultState.Success(unassigned)
        _ongoingTrips.value = ResultState.Success(ongoing)
        _completedTrips.value = ResultState.Success(completed)

        _unassignedCount.value = unassigned.size
        _ongoingCount.value = ongoing.size
        _completedCount.value = completed.size
        _totalCount.value = allTrips.size
    }

    // ── Trip Creation ───────────────────────────────────────────

    /**
     * Create a new trip from the form data.
     *
     * @param data Map containing: pickupAddress, dropAddress, loadDescription,
     *             estimatedDistance, notes
     */
    fun createTrip(data: Map<String, Any?>) {
        val companyId = currentCompanyId ?: return
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trackingId = generateTrackingId()
                val now = System.currentTimeMillis()

                val trip = Trip(
                    pickupAddress = data["pickupAddress"] as? String ?: "",
                    dropAddress = data["dropAddress"] as? String ?: "",
                    estimatedDistance = (data["estimatedDistance"] as? Number)?.toDouble() ?: 0.0,
                    notes = data["notes"] as? String ?: "",
                    trackingId = trackingId,
                    companyId = companyId,
                    status = TripStatus.CREATED,
                    createdAt = now,
                    updatedAt = now,
                    metadata = buildMap {
                        val loadDesc = data["loadDescription"] as? String ?: ""
                        if (loadDesc.isNotBlank()) put("loadDescription", loadDesc)
                    }
                )

                when (val result = tripRepository.createTrip(trip)) {
                    is ResultState.Success -> {
                        // Create a CREATED event
                        val userId = SessionManager.getInstance().getCachedUserId() ?: ""
                        val event = TripEvent(
                            tripId = result.data,
                            type = TripEventType.CREATED,
                            description = "Trip created with tracking ID $trackingId",
                            createdBy = userId,
                            timestamp = now
                        )
                        tripEventRepository.createEvent(event)

                        _operationResult.value = ResultState.Success("Trip created successfully")
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
                    e.message ?: "Failed to create trip", e
                )
            }
        }
    }

    // ── Smart Assignment ────────────────────────────────────────

    /**
     * Load eligible vehicle+driver pairs for assignment.
     *
     * Eligibility rules:
     * 1. Vehicle status == AVAILABLE
     * 2. Vehicle.assignedDriverId != null (must have a driver)
     * 3. Vehicle.documentsValid == true
     * 4. Driver.verified (verificationStatus.isApproved())
     * 5. Driver.blocked == false
     */
    fun loadEligibleOptions() {
        val companyId = currentCompanyId ?: return
        _eligibleOptions.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Get all available vehicles
                val vehicleResult = vehicleRepository.getAvailableVehicles(companyId)
                if (vehicleResult !is ResultState.Success) {
                    _eligibleOptions.value = ResultState.Error("Failed to load vehicles")
                    return@launch
                }

                val availableVehicles = vehicleResult.data.filter { vehicle ->
                    vehicle.status == VehicleStatus.AVAILABLE &&
                    !vehicle.assignedDriverId.isNullOrBlank() &&
                    vehicle.documentsValid
                }

                if (availableVehicles.isEmpty()) {
                    _eligibleOptions.value = ResultState.Success(emptyList())
                    return@launch
                }

                // Step 2: Verify each assigned driver is eligible
                val eligibleOptions = mutableListOf<SmartAssignOptionAdapter.EligibleOption>()

                for (vehicle in availableVehicles) {
                    val driverId = vehicle.assignedDriverId ?: continue
                    val driverResult = driverRepository.getDriverById(driverId)
                    if (driverResult !is ResultState.Success) continue

                    val driver = driverResult.data ?: continue

                    // Check driver eligibility
                    if (driver.blocked) continue
                    if (!driver.verificationStatus.isApproved()) continue

                    // Resolve driver name
                    val driverName = resolveDriverName(driver)

                    eligibleOptions.add(
                        SmartAssignOptionAdapter.EligibleOption(
                            vehicleId = vehicle.vehicleId,
                            driverId = driver.driverId,
                            vehicleNumber = vehicle.number,
                            vehicleTypeCapacity = "${vehicle.type.displayName} • ${vehicle.capacity}T",
                            driverName = driverName,
                            tripCount = driver.totalTrips,
                            proximity = "" // Placeholder — GPS integration future
                        )
                    )
                }

                // Sort by trip count (most experienced first)
                val sorted = eligibleOptions.sortedByDescending { it.tripCount }
                _eligibleOptions.value = ResultState.Success(sorted)

            } catch (e: Exception) {
                _eligibleOptions.value = ResultState.Error(
                    e.message ?: "Failed to load eligible vehicles", e
                )
            }
        }
    }

    /**
     * Assign a trip to a vehicle+driver pair.
     *
     * Steps:
     * 1. Update trip: set vehicleId, driverId, status=ASSIGNED, assignedBy
     * 2. Update vehicle status to ON_TRIP
     * 3. Create DRIVER_ASSIGNED event
     */
    fun assignTrip(tripId: String, vehicleId: String, driverId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = SessionManager.getInstance().getCachedUserId() ?: ""
                val now = System.currentTimeMillis()

                // Step 1: Update trip
                val tripResult = tripRepository.getTripById(tripId)
                val trip = (tripResult as? ResultState.Success)?.data
                if (trip == null) {
                    _operationResult.value = ResultState.Error("Trip not found")
                    return@launch
                }

                val updatedTrip = trip.copy(
                    vehicleId = vehicleId,
                    driverId = driverId,
                    status = TripStatus.ASSIGNED,
                    assignedBy = userId,
                    updatedAt = now
                )

                when (val updateResult = tripRepository.updateTrip(updatedTrip)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            updateResult.message, updateResult.exception
                        )
                        return@launch
                    }
                    else -> {}
                }

                // Step 2: Update vehicle status to ON_TRIP
                vehicleRepository.updateVehicleStatus(vehicleId, VehicleStatus.ON_TRIP)

                // Step 3: Create assignment event
                val event = TripEvent(
                    tripId = tripId,
                    type = TripEventType.DRIVER_ASSIGNED,
                    description = "Trip assigned to vehicle $vehicleId",
                    createdBy = userId,
                    timestamp = now
                )
                tripEventRepository.createEvent(event)

                _operationResult.value = ResultState.Success("Trip assigned successfully")

            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to assign trip", e
                )
            }
        }
    }

    // ── Trip Cancellation ───────────────────────────────────────

    /**
     * Cancel a trip, returning the vehicle to AVAILABLE if assigned.
     */
    fun cancelTrip(tripId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = SessionManager.getInstance().getCachedUserId() ?: ""
                val now = System.currentTimeMillis()

                // Get current trip to check for assigned vehicle
                val tripResult = tripRepository.getTripById(tripId)
                val trip = (tripResult as? ResultState.Success)?.data

                // Cancel the trip
                when (val cancelResult = tripRepository.cancelTrip(tripId, "Cancelled by manager")) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            cancelResult.message, cancelResult.exception
                        )
                        return@launch
                    }
                    else -> {}
                }

                // Return vehicle to AVAILABLE if it was assigned
                if (trip != null && trip.vehicleId.isNotBlank()) {
                    vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
                }

                // Create cancellation event
                val event = TripEvent(
                    tripId = tripId,
                    type = TripEventType.CANCELLED,
                    description = "Trip cancelled by manager",
                    createdBy = userId,
                    timestamp = now
                )
                tripEventRepository.createEvent(event)

                _operationResult.value = ResultState.Success("Trip cancelled")

            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to cancel trip", e
                )
            }
        }
    }

    // ── Name Resolution ─────────────────────────────────────────

    /**
     * Resolve vehicle numbers and driver names for display in trip cards.
     * Results are cached to minimize Firestore reads.
     */
    private fun resolveNames(trips: List<Trip>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (trip in trips) {
                // Resolve vehicle number
                if (trip.vehicleId.isNotBlank() && !vehicleNameCache.containsKey(trip.vehicleId)) {
                    try {
                        val result = vehicleRepository.getVehicleById(trip.vehicleId)
                        if (result is ResultState.Success && result.data != null) {
                            vehicleNameCache[trip.vehicleId] = result.data.number
                        }
                    } catch (_: Exception) {}
                }

                // Resolve driver name
                if (trip.driverId.isNotBlank() && !driverNameCache.containsKey(trip.driverId)) {
                    try {
                        val result = driverRepository.getDriverById(trip.driverId)
                        if (result is ResultState.Success && result.data != null) {
                            driverNameCache[trip.driverId] = resolveDriverName(result.data)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Get cached vehicle number for a trip.
     */
    fun getVehicleNumber(vehicleId: String): String? {
        return vehicleNameCache[vehicleId]
    }

    /**
     * Get cached driver name for a trip.
     */
    fun getDriverName(driverId: String): String? {
        return driverNameCache[driverId]
    }

    /**
     * Resolve a driver's display name.
     * Falls back to license number if name isn't available.
     */
    private fun resolveDriverName(driver: Driver): String {
        // Driver model has licenseNumber but no name field;
        // In production this would query the User document.
        return driver.licenseNumber.ifBlank { "Driver ${driver.driverId.take(6)}" }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Generate a unique tracking ID: TRK-YYYYMMDD-XXXXXX
     */
    private fun generateTrackingId(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val random = UUID.randomUUID().toString().take(6).uppercase()
        return "TRK-$dateStr-$random"
    }

    /**
     * Clear the operation result after consumption.
     */
    fun clearOperationResult() {
        _operationResult.value = ResultState.Idle
    }
}
