package com.example.movexa.ui.dashboard.driver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.service.LocationTrackingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the Driver Navigation / GPS Tracking screen.
 *
 * Responsibilities:
 *  ● Start / Stop the LocationTrackingService foreground service
 *  ● Observe isTracking + lastLocation from the service's static state
 *  ● Resolve the driver's assigned vehicle and active trip
 *  ● Compute tracking duration
 *  ● Provide GPS status indicators
 *
 * Uses AndroidViewModel for application context to start/stop the foreground service.
 */
class DriverNavigationViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Repositories ───────────────────────────────────────────
    private val driverRepository = DriverRepositoryImpl()
    private val tripRepository = TripRepositoryImpl()

    // ─── Tracking State (mirrors service statics) ───────────────
    val isTracking: StateFlow<Boolean> = LocationTrackingService.isTracking
    val lastLocation: StateFlow<TrackingLocation?> = LocationTrackingService.lastLocation
    val trackingStartTime: StateFlow<Long> = LocationTrackingService.trackingStartTime

    // ─── Driver & Vehicle Info ──────────────────────────────────
    private val _driverInfo = MutableStateFlow<ResultState<Driver>>(ResultState.Idle)
    val driverInfo: StateFlow<ResultState<Driver>> = _driverInfo.asStateFlow()

    private val _activeTrip = MutableStateFlow<Trip?>(null)
    val activeTrip: StateFlow<Trip?> = _activeTrip.asStateFlow()

    private val _vehicleId = MutableStateFlow<String?>(null)
    val vehicleId: StateFlow<String?> = _vehicleId.asStateFlow()

    // ─── Tracking Duration (seconds elapsed since start) ────────
    private val _trackingDuration = MutableStateFlow(0L)
    val trackingDuration: StateFlow<Long> = _trackingDuration.asStateFlow()

    // ─── GPS Status ─────────────────────────────────────────────
    enum class GpsStatus { INACTIVE, ACQUIRING, ACTIVE, POOR_SIGNAL }

    private val _gpsStatus = MutableStateFlow(GpsStatus.INACTIVE)
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    // ─── Loading ────────────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ─── Error Events ───────────────────────────────────────────
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    // ─── Permission Event ───────────────────────────────────────
    private val _requestPermissions = MutableSharedFlow<Unit>()
    val requestPermissions: SharedFlow<Unit> = _requestPermissions.asSharedFlow()

    // ─── Internal state ─────────────────────────────────────────
    private var companyId: String? = null
    private var driverId: String? = null
    private var durationJob: Job? = null
    private var gpsStatusJob: Job? = null

    // ═══════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════

    /**
     * Load driver info and resolve vehicle assignment.
     * Call from fragment's initViews().
     */
    fun initialize() {
        viewModelScope.launch {
            _isLoading.value = true

            // Get current user ID (driver's userId)
            val userId = SessionManager.getInstance().getCachedUserId()
            if (userId.isNullOrBlank()) {
                _errorEvent.emit("Not logged in. Please sign in again.")
                _isLoading.value = false
                return@launch
            }

            // Look up (or auto-create) the driver record
            val driverResult = driverRepository.getOrCreateDriverByUserId(userId)
            if (driverResult is ResultState.Success) {
                val driver = driverResult.data
                _driverInfo.value = ResultState.Success(driver)
                driverId = driver.driverId
                companyId = driver.companyId
                _vehicleId.value = driver.assignedVehicleId

                // Find active trip for this driver
                loadActiveTrip(driver.driverId)
            } else if (driverResult is ResultState.Error) {
                _driverInfo.value = ResultState.Error(driverResult.message)
                _errorEvent.emit(driverResult.message)
            }

            _isLoading.value = false

            // If tracking is already running (service survived fragment recreation), sync UI
            if (LocationTrackingService.isTracking.value) {
                startDurationTimer()
                startGpsStatusMonitor()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Start / Stop Tracking
    // ═══════════════════════════════════════════════════════════

    /**
     * Toggle tracking on/off.
     * Fragment must ensure location permission is granted before calling this.
     */
    fun toggleTracking() {
        if (LocationTrackingService.isTracking.value) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    /**
     * Start the foreground tracking service.
     */
    fun startTracking() {
        val company = companyId
        val vehicle = _vehicleId.value
        val driver = driverId

        if (company.isNullOrBlank() || vehicle.isNullOrBlank() || driver.isNullOrBlank()) {
            viewModelScope.launch {
                _errorEvent.emit(
                    when {
                        vehicle.isNullOrBlank() -> "No vehicle assigned. Contact your manager."
                        company.isNullOrBlank() -> "Company not found. Please log in again."
                        else -> "Driver profile incomplete."
                    }
                )
            }
            return
        }

        val tripId = _activeTrip.value?.tripId ?: ""

        LocationTrackingService.start(
            context = getApplication(),
            companyId = company,
            vehicleId = vehicle,
            driverId = driver,
            tripId = tripId
        )

        startDurationTimer()
        startGpsStatusMonitor()
    }

    /**
     * Stop the foreground tracking service.
     */
    fun stopTracking() {
        LocationTrackingService.stop(getApplication())
        durationJob?.cancel()
        gpsStatusJob?.cancel()
        _trackingDuration.value = 0L
        _gpsStatus.value = GpsStatus.INACTIVE
    }

    /**
     * Request location permissions from fragment.
     */
    fun requestLocationPermission() {
        viewModelScope.launch {
            _requestPermissions.emit(Unit)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Active Trip Resolution
    // ═══════════════════════════════════════════════════════════

    private suspend fun loadActiveTrip(driverId: String) {
        val result = tripRepository.getTripsByDriver(driverId)
        if (result is ResultState.Success) {
            // Find an ongoing trip (ACCEPTED or STARTED)
            val activeTrip = result.data.firstOrNull { trip ->
                trip.status == TripStatus.ACCEPTED || trip.status == TripStatus.STARTED
            }
            _activeTrip.value = activeTrip
        }
    }

    /**
     * Refresh active trip info (call when trip status changes).
     */
    fun refreshActiveTrip() {
        val driver = driverId ?: return
        viewModelScope.launch {
            loadActiveTrip(driver)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Duration Timer
    // ═══════════════════════════════════════════════════════════

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (isActive && LocationTrackingService.isTracking.value) {
                val startTime = LocationTrackingService.trackingStartTime.value
                if (startTime > 0) {
                    _trackingDuration.value = (System.currentTimeMillis() - startTime) / 1000L
                }
                delay(1000L)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GPS Status Monitor
    // ═══════════════════════════════════════════════════════════

    private fun startGpsStatusMonitor() {
        gpsStatusJob?.cancel()
        gpsStatusJob = viewModelScope.launch {
            _gpsStatus.value = GpsStatus.ACQUIRING
            while (isActive && LocationTrackingService.isTracking.value) {
                val location = LocationTrackingService.lastLocation.value
                _gpsStatus.value = when {
                    location == null -> GpsStatus.ACQUIRING
                    location.accuracy > 50f -> GpsStatus.POOR_SIGNAL
                    else -> GpsStatus.ACTIVE
                }
                delay(2000L)
            }
            if (!LocationTrackingService.isTracking.value) {
                _gpsStatus.value = GpsStatus.INACTIVE
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════

    /**
     * Format tracking duration as HH:mm:ss string.
     */
    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    /**
     * Format heading degrees to compass direction.
     */
    fun formatHeading(degrees: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((degrees + 22.5f) / 45f).toInt() % 8
        return "${degrees.toInt()}° ${directions[index]}"
    }

    // ═══════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════

    override fun onCleared() {
        durationJob?.cancel()
        gpsStatusJob?.cancel()
        super.onCleared()
    }
}
