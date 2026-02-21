package com.example.movexa.ui.trips

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.TripEvent
import com.example.movexa.data.model.enums.TripEventType
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.model.UserRole
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
 * ViewModel for the Trip Details screen.
 *
 * Provides:
 * - Real-time single-trip observation
 * - Real-time timeline events
 * - Resolved names for vehicle, driver, assignedBy
 * - Role-aware action dispatching (manager can cancel; driver can accept/reject/start/complete)
 */
class TripDetailsViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────
    private val tripRepository: TripRepository = TripRepositoryImpl()
    private val tripEventRepository: TripEventRepository = TripEventRepositoryImpl()
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()
    private val driverRepository: DriverRepository = DriverRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    /** The trip being viewed. */
    private val _trip = MutableStateFlow<ResultState<Trip?>>(ResultState.Loading)
    val trip: StateFlow<ResultState<Trip?>> = _trip.asStateFlow()

    /** Timeline events for this trip, ordered chronologically. */
    private val _events = MutableStateFlow<ResultState<List<TripEvent>>>(ResultState.Loading)
    val events: StateFlow<ResultState<List<TripEvent>>> = _events.asStateFlow()

    /** Resolved display names. */
    private val _vehicleName = MutableStateFlow<String?>(null)
    val vehicleName: StateFlow<String?> = _vehicleName.asStateFlow()

    private val _driverName = MutableStateFlow<String?>(null)
    val driverName: StateFlow<String?> = _driverName.asStateFlow()

    private val _assignedByName = MutableStateFlow<String?>(null)
    val assignedByName: StateFlow<String?> = _assignedByName.asStateFlow()

    /** Single-shot operation result for actions. */
    private val _operationResult = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val operationResult: StateFlow<ResultState<String>> = _operationResult.asStateFlow()

    /** Current user role. */
    private val _userRole = MutableStateFlow<UserRole?>(null)
    val userRole: StateFlow<UserRole?> = _userRole.asStateFlow()

    private var currentTripId: String? = null
    private var currentUserId: String? = null
    private var currentDriverId: String? = null

    // ── Initialization ──────────────────────────────────────────

    /**
     * Load trip details and begin observing.
     *
     * @param tripId The trip document ID to observe.
     */
    fun loadTripDetails(tripId: String) {
        currentTripId = tripId

        viewModelScope.launch {
            currentUserId = SessionManager.getInstance().getCachedUserId()
            _userRole.value = SessionManager.getInstance().getCachedUserRole()

            // If driver role, resolve driverId for action validation
            if (_userRole.value == UserRole.DRIVER) {
                resolveDriverId()
            }

            observeTrip(tripId)
            observeEvents(tripId)
        }
    }

    /**
     * Refresh trip and events.
     */
    fun refresh() {
        val tripId = currentTripId ?: return
        _trip.value = ResultState.Loading
        _events.value = ResultState.Loading
        observeTrip(tripId)
        observeEvents(tripId)
    }

    // ── Real-Time Observation ───────────────────────────────────

    private fun observeTrip(tripId: String) {
        viewModelScope.launch {
            tripRepository.observeTrip(tripId)
                .catch { e ->
                    _trip.value = ResultState.Error(
                        e.message ?: "Failed to load trip details", e
                    )
                }
                .collect { result ->
                    _trip.value = result
                    // Resolve names when trip data arrives
                    if (result is ResultState.Success && result.data != null) {
                        resolveNames(result.data)
                    }
                }
        }
    }

    private fun observeEvents(tripId: String) {
        viewModelScope.launch {
            tripEventRepository.observeTripEvents(tripId)
                .catch { e ->
                    _events.value = ResultState.Error(
                        e.message ?: "Failed to load timeline", e
                    )
                }
                .collect { result ->
                    _events.value = result
                }
        }
    }

    // ── Name Resolution ─────────────────────────────────────────

    private fun resolveNames(trip: Trip) {
        viewModelScope.launch(Dispatchers.IO) {
            // Resolve vehicle name
            if (trip.vehicleId.isNotBlank() && _vehicleName.value == null) {
                try {
                    val result = vehicleRepository.getVehicleById(trip.vehicleId)
                    if (result is ResultState.Success && result.data != null) {
                        val v = result.data
                        _vehicleName.value = "${v.number} — ${v.make} ${v.model}"
                    }
                } catch (_: Exception) {}
            }

            // Resolve driver name
            if (trip.driverId.isNotBlank() && _driverName.value == null) {
                try {
                    val result = driverRepository.getDriverById(trip.driverId)
                    if (result is ResultState.Success && result.data != null) {
                        _driverName.value = result.data.licenseNumber
                    }
                } catch (_: Exception) {}
            }

            // Resolve assignedBy (this is a userId — look up driver or use raw)
            if (trip.assignedBy.isNotBlank() && _assignedByName.value == null) {
                _assignedByName.value = trip.assignedBy
            }
        }
    }

    private suspend fun resolveDriverId() {
        val userId = currentUserId ?: return
        try {
            val result = driverRepository.getDriverByUserId(userId)
            if (result is ResultState.Success && result.data != null) {
                currentDriverId = result.data.driverId
            }
        } catch (_: Exception) {}
    }

    // ── Actions ─────────────────────────────────────────────────

    /**
     * Accept trip (Driver action: ASSIGNED → ACCEPTED).
     */
    fun acceptTrip() {
        val tripId = currentTripId ?: return
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()

                when (val r = tripRepository.updateTripStatus(tripId, TripStatus.ACCEPTED)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(r.message, r.exception)
                        return@launch
                    }
                    else -> {}
                }

                tripEventRepository.createEvent(
                    TripEvent(
                        tripId = tripId,
                        type = TripEventType.DRIVER_ASSIGNED,
                        description = "Trip accepted by driver",
                        createdBy = currentUserId ?: "",
                        timestamp = now
                    )
                )

                _operationResult.value = ResultState.Success("Trip accepted")
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to accept trip", e
                )
            }
        }
    }

    /**
     * Reject trip (Driver action: ASSIGNED → REJECTED/CREATED).
     */
    fun rejectTrip() {
        val tripId = currentTripId ?: return
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()

                val tripResult = tripRepository.getTripById(tripId)
                val trip = (tripResult as? ResultState.Success)?.data
                if (trip == null) {
                    _operationResult.value = ResultState.Error("Trip not found")
                    return@launch
                }

                val updated = trip.copy(
                    status = TripStatus.CREATED,
                    vehicleId = "",
                    driverId = "",
                    assignedBy = "",
                    updatedAt = now,
                    metadata = trip.metadata + mapOf(
                        "lastRejectedBy" to (currentUserId ?: "")
                    )
                )

                when (val r = tripRepository.updateTrip(updated)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(r.message, r.exception)
                        return@launch
                    }
                    else -> {}
                }

                if (trip.vehicleId.isNotBlank()) {
                    vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
                }

                tripEventRepository.createEvent(
                    TripEvent(
                        tripId = tripId,
                        type = TripEventType.CANCELLED,
                        description = "Trip rejected by driver — returned to unassigned",
                        createdBy = currentUserId ?: "",
                        timestamp = now
                    )
                )

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
     * Start trip (Driver action: ACCEPTED → STARTED).
     */
    fun startTrip() {
        val tripId = currentTripId ?: return
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()

                when (val r = tripRepository.startTrip(tripId)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(r.message, r.exception)
                        return@launch
                    }
                    else -> {}
                }

                tripEventRepository.createEvent(
                    TripEvent(
                        tripId = tripId,
                        type = TripEventType.STARTED,
                        description = "Trip started",
                        createdBy = currentUserId ?: "",
                        timestamp = now
                    )
                )

                _operationResult.value = ResultState.Success("Trip started")
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to start trip", e
                )
            }
        }
    }

    /**
     * Complete trip (Driver action: STARTED → COMPLETED).
     */
    fun completeTrip(actualDistance: Double = 0.0) {
        val tripId = currentTripId ?: return
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()

                val tripResult = tripRepository.getTripById(tripId)
                val trip = (tripResult as? ResultState.Success)?.data
                val duration = if (trip != null && trip.startTime > 0)
                    now - trip.startTime else 0L
                val distance = if (actualDistance > 0) actualDistance
                    else trip?.estimatedDistance ?: 0.0

                when (val r = tripRepository.completeTrip(tripId, distance, duration)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(r.message, r.exception)
                        return@launch
                    }
                    else -> {}
                }

                // Return vehicle
                if (trip != null && trip.vehicleId.isNotBlank()) {
                    vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
                }

                // Increment driver trip count
                if (currentDriverId != null && currentDriverId!!.isNotBlank()) {
                    driverRepository.incrementTripCount(currentDriverId!!)
                }

                tripEventRepository.createEvent(
                    TripEvent(
                        tripId = tripId,
                        type = TripEventType.COMPLETED,
                        description = "Trip completed",
                        createdBy = currentUserId ?: "",
                        timestamp = now
                    )
                )

                _operationResult.value = ResultState.Success("Trip completed")
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to complete trip", e
                )
            }
        }
    }

    /**
     * Cancel trip (Manager action: any active → CANCELLED).
     */
    fun cancelTrip(reason: String? = null) {
        val tripId = currentTripId ?: return
        _operationResult.value = ResultState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()

                val tripResult = tripRepository.getTripById(tripId)
                val trip = (tripResult as? ResultState.Success)?.data

                when (val r = tripRepository.cancelTrip(tripId, reason)) {
                    is ResultState.Error -> {
                        _operationResult.value = ResultState.Error(r.message, r.exception)
                        return@launch
                    }
                    else -> {}
                }

                // Return vehicle
                if (trip != null && trip.vehicleId.isNotBlank()) {
                    vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
                }

                tripEventRepository.createEvent(
                    TripEvent(
                        tripId = tripId,
                        type = TripEventType.CANCELLED,
                        description = reason ?: "Trip cancelled by manager",
                        createdBy = currentUserId ?: "",
                        timestamp = now
                    )
                )

                _operationResult.value = ResultState.Success("Trip cancelled")
            } catch (e: Exception) {
                _operationResult.value = ResultState.Error(
                    e.message ?: "Failed to cancel trip", e
                )
            }
        }
    }

    /**
     * Add a note event to the trip timeline.
     */
    fun addNote(note: String) {
        val tripId = currentTripId ?: return
        if (note.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                tripEventRepository.createEvent(
                    TripEvent(
                        tripId = tripId,
                        type = TripEventType.NOTE_ADDED,
                        description = note,
                        createdBy = currentUserId ?: "",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun clearOperationResult() {
        _operationResult.value = ResultState.Idle
    }
}
