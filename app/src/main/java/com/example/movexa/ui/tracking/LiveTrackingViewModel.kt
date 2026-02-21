package com.example.movexa.ui.tracking

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.TrackingRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ViewModel for the Live Tracking screen (customer / manager viewing a single vehicle).
 *
 * Responsibilities:
 *  ● Observe a single vehicle's live location via TrackingRepository
 *  ● Compute ETA based on distance remaining and average speed
 *  ● Resolve trip, vehicle, and driver details for display
 *  ● Track route history (list of LatLng points for polyline)
 *  ● Compute distance remaining to destination
 */
class LiveTrackingViewModel : BaseViewModel() {

    // ─── Repositories ───────────────────────────────────────────
    private val trackingRepository = TrackingRepositoryImpl()
    private val tripRepository = TripRepositoryImpl()
    private val vehicleRepository = VehicleRepositoryImpl()
    private val driverRepository = DriverRepositoryImpl()

    // ─── Live Location ──────────────────────────────────────────
    private val _vehicleLocation = MutableStateFlow<ResultState<TrackingLocation?>>(ResultState.Idle)
    val vehicleLocation: StateFlow<ResultState<TrackingLocation?>> = _vehicleLocation.asStateFlow()

    // ─── Trip Details ───────────────────────────────────────────
    private val _tripDetails = MutableStateFlow<Trip?>(null)
    val tripDetails: StateFlow<Trip?> = _tripDetails.asStateFlow()

    // ─── Vehicle Details ────────────────────────────────────────
    private val _vehicleDetails = MutableStateFlow<Vehicle?>(null)
    val vehicleDetails: StateFlow<Vehicle?> = _vehicleDetails.asStateFlow()

    // ─── Driver Details ─────────────────────────────────────────
    private val _driverDetails = MutableStateFlow<Driver?>(null)
    val driverDetails: StateFlow<Driver?> = _driverDetails.asStateFlow()

    // ─── ETA ────────────────────────────────────────────────────
    private val _etaMinutes = MutableStateFlow<Int?>(null)
    val etaMinutes: StateFlow<Int?> = _etaMinutes.asStateFlow()

    private val _distanceRemainingKm = MutableStateFlow<Double?>(null)
    val distanceRemainingKm: StateFlow<Double?> = _distanceRemainingKm.asStateFlow()

    // ─── Route History (for polyline) ───────────────────────────
    data class LatLng(val lat: Double, val lng: Double)

    private val _routeHistory = MutableStateFlow<List<LatLng>>(emptyList())
    val routeHistory: StateFlow<List<LatLng>> = _routeHistory.asStateFlow()
    private val routePoints = mutableListOf<LatLng>()

    // ─── Tracking Status ────────────────────────────────────────
    enum class TrackingStatus { WAITING, ACTIVE, OFFLINE }

    private val _trackingStatus = MutableStateFlow(TrackingStatus.WAITING)
    val trackingStatus: StateFlow<TrackingStatus> = _trackingStatus.asStateFlow()

    // ─── Status Message ─────────────────────────────────────────
    private val _statusMessage = MutableStateFlow("Waiting for vehicle location...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // ─── Internal ───────────────────────────────────────────────
    private var companyId: String = ""
    private var vehicleId: String = ""
    private var tripId: String = ""
    private var observeJob: Job? = null
    private var lastLocationTimestamp = 0L

    // ═══════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════

    /**
     * Start observing a vehicle's live location.
     *
     * @param companyId The company that owns the vehicle
     * @param vehicleId The vehicle to track
     * @param tripId Optional trip ID for context
     */
    fun startTracking(companyId: String, vehicleId: String, tripId: String = "") {
        this.companyId = companyId
        this.vehicleId = vehicleId
        this.tripId = tripId

        // Load related details
        loadTripDetails()
        loadVehicleDetails()

        // Start live observation
        observeVehicle()
    }

    /**
     * Stop observing and clean up.
     */
    fun stopTracking() {
        observeJob?.cancel()
        _vehicleLocation.value = ResultState.Idle
        _trackingStatus.value = TrackingStatus.WAITING
        routePoints.clear()
        _routeHistory.value = emptyList()
    }

    // ═══════════════════════════════════════════════════════════
    //  Real-Time Observation
    // ═══════════════════════════════════════════════════════════

    private fun observeVehicle() {
        observeJob?.cancel()
        _trackingStatus.value = TrackingStatus.WAITING

        observeJob = viewModelScope.launch {
            trackingRepository.observeVehicleLocation(companyId, vehicleId)
                .catch { e ->
                    _vehicleLocation.value = ResultState.Error(
                        message = e.message ?: "Failed to observe vehicle location",
                        exception = e
                    )
                    _trackingStatus.value = TrackingStatus.OFFLINE
                    _statusMessage.value = "Connection lost. Retrying..."
                }
                .collect { result ->
                    _vehicleLocation.value = result

                    when (result) {
                        is ResultState.Success -> {
                            val location = result.data
                            if (location != null && location.isValid) {
                                handleLocationUpdate(location)
                            } else {
                                _trackingStatus.value = TrackingStatus.OFFLINE
                                _statusMessage.value = "Vehicle is currently offline"
                            }
                        }
                        is ResultState.Error -> {
                            _trackingStatus.value = TrackingStatus.OFFLINE
                            _statusMessage.value = "Unable to get vehicle location"
                        }
                        is ResultState.Loading -> {
                            _trackingStatus.value = TrackingStatus.WAITING
                            _statusMessage.value = "Connecting..."
                        }
                        is ResultState.Idle -> { /* ignore */ }
                    }
                }
        }
    }

    /**
     * Process a new location update.
     */
    private fun handleLocationUpdate(location: TrackingLocation) {
        // Update status
        val timeSinceLastUpdate = System.currentTimeMillis() - location.timestamp
        _trackingStatus.value = if (timeSinceLastUpdate > 60_000) {
            TrackingStatus.OFFLINE
        } else {
            TrackingStatus.ACTIVE
        }

        // Update status message
        _statusMessage.value = when {
            location.isMoving -> "Vehicle is moving • ${String.format("%.0f", location.speedKmh)} km/h"
            else -> "Vehicle is stopped"
        }

        // Append to route history (avoid duplicates)
        if (location.timestamp != lastLocationTimestamp) {
            lastLocationTimestamp = location.timestamp
            routePoints.add(LatLng(location.lat, location.lng))
            // Keep last 500 points to avoid memory issues
            if (routePoints.size > 500) {
                routePoints.removeAt(0)
            }
            _routeHistory.value = routePoints.toList()
        }

        // Compute ETA if we have a destination
        computeEta(location)
    }

    // ═══════════════════════════════════════════════════════════
    //  ETA Computation
    // ═══════════════════════════════════════════════════════════

    /**
     * Compute ETA based on:
     *   - Haversine distance from current location to drop-off
     *   - Current speed (or average 30 km/h if stationary)
     */
    private fun computeEta(location: TrackingLocation) {
        val trip = _tripDetails.value ?: return
        val dropLat = trip.dropLocation.latitude
        val dropLng = trip.dropLocation.longitude

        if (dropLat == 0.0 && dropLng == 0.0) return

        val distanceKm = haversineDistance(
            location.lat, location.lng,
            dropLat, dropLng
        )
        _distanceRemainingKm.value = distanceKm

        // Use current speed if moving, otherwise assume 30 km/h average
        val speedKmh = if (location.speedKmh > 5f) location.speedKmh.toDouble() else 30.0
        val etaHours = distanceKm / speedKmh
        val etaMins = (etaHours * 60).toInt().coerceAtLeast(1)
        _etaMinutes.value = etaMins
    }

    /**
     * Haversine formula for great-circle distance in kilometers.
     */
    private fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * asin(sqrt(a))
        return r * c
    }

    // ═══════════════════════════════════════════════════════════
    //  Detail Loading
    // ═══════════════════════════════════════════════════════════

    private fun loadTripDetails() {
        if (tripId.isBlank()) return
        viewModelScope.launch {
            val result = tripRepository.getTripById(tripId)
            if (result is ResultState.Success) {
                _tripDetails.value = result.data

                // Load driver if available
                result.data?.driverId?.let { dId ->
                    if (dId.isNotBlank()) loadDriverDetails(dId)
                }
            }
        }
    }

    private fun loadVehicleDetails() {
        if (vehicleId.isBlank()) return
        viewModelScope.launch {
            val result = vehicleRepository.getVehicleById(vehicleId)
            if (result is ResultState.Success) {
                _vehicleDetails.value = result.data
            }
        }
    }

    private fun loadDriverDetails(driverId: String) {
        viewModelScope.launch {
            val result = driverRepository.getDriverById(driverId)
            if (result is ResultState.Success) {
                _driverDetails.value = result.data
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Formatters
    // ═══════════════════════════════════════════════════════════

    /**
     * Format ETA as human-readable string ("5 min", "1 hr 20 min").
     */
    fun formatEta(minutes: Int): String {
        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            else -> {
                val hrs = minutes / 60
                val mins = minutes % 60
                if (mins == 0) "$hrs hr" else "$hrs hr $mins min"
            }
        }
    }

    /**
     * Format distance remaining ("0.5 km", "12.3 km").
     */
    fun formatDistance(km: Double): String {
        return if (km < 1.0) {
            "${(km * 1000).toInt()} m"
        } else {
            "%.1f km".format(km)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
