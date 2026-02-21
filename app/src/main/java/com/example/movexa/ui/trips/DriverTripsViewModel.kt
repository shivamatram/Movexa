package com.example.movexa.ui.trips

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.TripEvent
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

/**
 * ViewModel for the Driver Trips screen.
 *
 * Responsibilities:
 * - Resolve driver document from current userId
 * - Real-time observation of driver's assigned trips
 * - Client-side filtering into tabs: New Requests, Ongoing, History
 * - Search filtering by tracking ID and address
 * - Accept trip (ASSIGNED → ACCEPTED)
 * - Reject trip (ASSIGNED → REJECTED_BY_DRIVER, returns trip to CREATED + frees vehicle)
 * - Start trip (ACCEPTED → STARTED)
 * - Complete trip (STARTED → COMPLETED, returns vehicle to AVAILABLE, increments trip count)
 * - Event logging for every status transition
 */
class DriverTripsViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────
    private val tripRepository: TripRepository = TripRepositoryImpl()
    private val tripEventRepository: TripEventRepository = TripEventRepositoryImpl()
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val driverRepository: DriverRepository = DriverRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    /** Raw trip list from Firestore (unfiltered, all driver's trips). */
    private val _allTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)

    /** New requests (status = ASSIGNED — awaiting driver action). */
    private val _newRequests = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val newRequests: StateFlow<ResultState<List<Trip>>> = _newRequests.asStateFlow()

    /** Ongoing trips (status = ACCEPTED or STARTED). */
    private val _ongoingTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val ongoingTrips: StateFlow<ResultState<List<Trip>>> = _ongoingTrips.asStateFlow()

    /** History (status = COMPLETED, REJECTED_BY_DRIVER, CANCELLED). */
    private val _historyTrips = MutableStateFlow<ResultState<List<Trip>>>(ResultState.Loading)
    val historyTrips: StateFlow<ResultState<List<Trip>>> = _historyTrips.asStateFlow()

    /** Search query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Single-shot operation result. */
    private val _operationResult = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val operationResult: StateFlow<ResultState<String>> = _operationResult.asStateFlow()

    /** Tab counts. */
    private val _newRequestsCount = MutableStateFlow(0)
    val newRequestsCount: StateFlow<Int> = _newRequestsCount.asStateFlow()

    private val _ongoingCount = MutableStateFlow(0)
    val ongoingCount: StateFlow<Int> = _ongoingCount.asStateFlow()

    private val _historyCount = MutableStateFlow(0)
    val historyCount: StateFlow<Int> = _historyCount.asStateFlow()

    /** Cached vehicle name lookup. */
    private val vehicleNameCache = mutableMapOf<String, String>()

    private var currentDriverId: String? = null
    private var currentUserId: String? = null

    // ── Initialization ──────────────────────────────────────────

    /**
     * Start loading driver trips.
     * First resolves the driver document from the current user's ID,
     * then observes their assigned trips.
     */
    fun loadTrips() {
        viewModelScope.launch {
            val userId = SessionManager.getInstance().getCachedUserId()
            if (userId.isNullOrBlank()) {
                val error = ResultState.Error("No user ID found. Please log in again.")
                _newRequests.value = error
                _ongoingTrips.value = error
                _historyTrips.value = error
                return@launch
            }
            currentUserId = userId

            // Resolve driver document from userId
            resolveDriverId(userId)
        }
    }

    /**
     * Look up the driver document that matches this userId.
     */
    private suspend fun resolveDriverId(userId: String) {
        try {
            when (val result = driverRepository.getDriverByUserId(userId)) {
                is ResultState.Success -> {
                    val driver = result.data
                    if (driver != null) {
                        currentDriverId = driver.driverId
                        observeDriverTrips(driver.driverId)
                    } else {
                        val error = ResultState.Error("Driver profile not found")
                        _newRequests.value = error
                        _ongoingTrips.value = error
                        _historyTrips.value = error
                    }
                }
                is ResultState.Error -> {
                    _newRequests.value = ResultState.Error(result.message)
                    _ongoingTrips.value = ResultState.Error(result.message)
                    _historyTrips.value = ResultState.Error(result.message)
                }
                else -> {}
            }
        } catch (e: Exception) {
            val error = ResultState.Error(
                e.message ?: "Failed to resolve driver profile", e
            )
            _newRequests.value = error
            _ongoingTrips.value = error
            _historyTrips.value = error
        }
    }

    /**
     * Refresh trip data.
     */
    fun refreshTrips() {
        val driverId = currentDriverId ?: return
        _allTrips.value = ResultState.Loading
        _newRequests.value = ResultState.Loading
        _ongoingTrips.value = ResultState.Loading
        observeDriverTrips(driverId)
    }

    // ── Real-Time Observation ───────────────────────────────────

    private fun observeDriverTrips(driverId: String) {
        viewModelScope.launch {
            tripRepository.observeDriverTrips(driverId)
                .catch { e ->
                    val error = ResultState.Error(
                        message = e.message ?: "Failed to load trips",
                        exception = e
                    )
                    _newRequests.value = error
                    _ongoingTrips.value = error
                    _historyTrips.value = error
                }
                .collect { result ->
                    _allTrips.value = result
                    applyFilters()
                    // Resolve vehicle names
                    if (result is ResultState.Success) {
                        resolveVehicleNames(result.data)
                    }
                }
        }
    }

    // ── Filtering ───────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    private fun applyFilters() {
        val currentState = _allTrips.value
        if (currentState !is ResultState.Success) {
            if (currentState is ResultState.Loading) {
                _newRequests.value = ResultState.Loading
                _ongoingTrips.value = ResultState.Loading
            }
            return
        }

        val allTrips = currentState.data
        val searchQuery = _searchQuery.value.trim().lowercase()

        val filtered = if (searchQuery.isBlank()) {
            allTrips
        } else {
            allTrips.filter { trip ->
                trip.trackingId.lowercase().contains(searchQuery) ||
                trip.pickupAddress.lowercase().contains(searchQuery) ||
                trip.dropAddress.lowercase().contains(searchQuery)
            }
        }

        // Categorize into tabs
        val newReqs = filtered.filter { it.status == TripStatus.ASSIGNED }
        val ongoing = filtered.filter {
            it.status == TripStatus.ACCEPTED || it.status == TripStatus.STARTED
        }
        val history = filtered.filter { it.status.isTerminal }

        _newRequests.value = ResultState.Success(newReqs)
        _ongoingTrips.value = ResultState.Success(ongoing)
        _historyTrips.value = ResultState.Success(history)

        _newRequestsCount.value = newReqs.size
        _ongoingCount.value = ongoing.size
        _historyCount.value = history.size
    }

    // ── Trip Actions ────────────────────────────────────────────

    /**
     * Accept a trip assignment (ASSIGNED → ACCEPTED).
     */
    fun acceptTrip(tripId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: ""
                val now = System.currentTimeMillis()

                // Update trip status
                when (val result = tripRepository.updateTripStatus(tripId, TripStatus.ACCEPTED)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                        return@launch
                    }
                    else -> {}
                }

                // Log event
                val event = TripEvent(
                    tripId = tripId,
                    type = TripEventType.DRIVER_ASSIGNED,
                    description = "Trip accepted by driver",
                    createdBy = userId,
                    timestamp = now
                )
                tripEventRepository.createEvent(event)

                _operationResult.value = ResultState.Success("Trip accepted")

            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to accept trip", e
                )
            }
        }
    }

    /**
     * Reject a trip assignment (ASSIGNED → REJECTED_BY_DRIVER).
     *
     * This also:
     * - Returns the trip status to CREATED (so manager can reassign)
     * - Clears vehicleId and driverId from the trip
     * - Returns the vehicle to AVAILABLE
     */
    fun rejectTrip(tripId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: ""
                val now = System.currentTimeMillis()

                // Get current trip to find vehicleId
                val tripResult = tripRepository.getTripById(tripId)
                val trip = (tripResult as? ResultState.Success)?.data
                if (trip == null) {
                    _operationResult.value = ResultState.Error("Trip not found")
                    return@launch
                }

                // Update trip: clear assignment and revert to CREATED
                val updatedTrip = trip.copy(
                    status = TripStatus.CREATED,
                    vehicleId = "",
                    driverId = "",
                    assignedBy = "",
                    updatedAt = now,
                    metadata = trip.metadata + mapOf("lastRejectedBy" to userId)
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

                // Return vehicle to AVAILABLE
                if (trip.vehicleId.isNotBlank()) {
                    vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
                }

                // Log rejection event
                val event = TripEvent(
                    tripId = tripId,
                    type = TripEventType.CANCELLED,
                    description = "Trip rejected by driver — returned to unassigned",
                    createdBy = userId,
                    timestamp = now
                )
                tripEventRepository.createEvent(event)

                _operationResult.value = ResultState.Success(
                    "Trip rejected — returned to unassigned"
                )

            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to reject trip", e
                )
            }
        }
    }

    /**
     * Start a trip (ACCEPTED → STARTED).
     * Sets startTime to now.
     */
    fun startTrip(tripId: String) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: ""
                val now = System.currentTimeMillis()

                when (val result = tripRepository.startTrip(tripId)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                        return@launch
                    }
                    else -> {}
                }

                // Log event
                val event = TripEvent(
                    tripId = tripId,
                    type = TripEventType.STARTED,
                    description = "Trip started by driver",
                    createdBy = userId,
                    timestamp = now
                )
                tripEventRepository.createEvent(event)

                _operationResult.value = ResultState.Success("Trip started")

            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to start trip", e
                )
            }
        }
    }

    /**
     * Complete a trip (STARTED → COMPLETED).
     *
     * Also:
     * - Returns vehicle to AVAILABLE
     * - Increments driver's trip count
     * - Sets endTime and actual distance
     */
    fun completeTrip(tripId: String, actualDistance: Double = 0.0) {
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: ""
                val driverId = currentDriverId ?: ""
                val now = System.currentTimeMillis()

                // Get trip for duration calculation
                val tripResult = tripRepository.getTripById(tripId)
                val trip = (tripResult as? ResultState.Success)?.data
                val duration = if (trip != null && trip.startTime > 0)
                    now - trip.startTime else 0L

                // Complete the trip
                val distance = if (actualDistance > 0) actualDistance
                    else trip?.estimatedDistance ?: 0.0
                when (val result = tripRepository.completeTrip(tripId, distance, duration)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(
                            result.message, result.exception
                        )
                        return@launch
                    }
                    else -> {}
                }

                // Return vehicle to AVAILABLE
                if (trip != null && trip.vehicleId.isNotBlank()) {
                    vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
                }

                // Increment driver trip count
                if (driverId.isNotBlank()) {
                    driverRepository.incrementTripCount(driverId)
                }

                // Log event
                val event = TripEvent(
                    tripId = tripId,
                    type = TripEventType.COMPLETED,
                    description = "Trip completed by driver",
                    createdBy = userId,
                    timestamp = now
                )
                tripEventRepository.createEvent(event)

                _operationResult.value = ResultState.Success("Trip completed")

            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to complete trip", e
                )
            }
        }
    }

    // ── Name Resolution ─────────────────────────────────────────

    private fun resolveVehicleNames(trips: List<Trip>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (trip in trips) {
                if (trip.vehicleId.isNotBlank() && !vehicleNameCache.containsKey(trip.vehicleId)) {
                    try {
                        val result = vehicleRepository.getVehicleById(trip.vehicleId)
                        if (result is ResultState.Success && result.data != null) {
                            vehicleNameCache[trip.vehicleId] = result.data.number
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun getVehicleNumber(vehicleId: String): String? {
        return vehicleNameCache[vehicleId]
    }

    fun clearOperationResult() {
        _operationResult.value = ResultState.Idle
    }
}
