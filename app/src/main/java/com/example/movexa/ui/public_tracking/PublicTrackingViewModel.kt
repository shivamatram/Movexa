package com.example.movexa.ui.public_tracking

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.LiveConnectionState
import com.example.movexa.data.model.PublicDriverInfo
import com.example.movexa.data.model.PublicLiveLocation
import com.example.movexa.data.model.PublicTripInfo
import com.example.movexa.data.model.PublicTrackingState
import com.example.movexa.data.model.PublicVehicleInfo
import com.example.movexa.data.model.RecentSearch
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TimelineEvent
import com.example.movexa.data.repository.impl.PublicTrackingRepositoryImpl
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ═══════════════════════════════════════════════════════════════════
 *  PUBLIC TRACKING VIEW MODEL
 * ═══════════════════════════════════════════════════════════════════
 *
 * Shared ViewModel for the three public customer tracking screens:
 *
 *  ● EnterTrackingFragment  – search by tracking ID
 *  ● PublicLiveTrackingFragment – real-time map
 *  ● DeliveryDetailsFragment – driver/vehicle info + timeline
 *
 * Architecture:
 *  ● Uses [PublicTrackingRepositoryImpl] for all data access
 *  ● Stores recent searches via SharedPreferences (no auth needed)
 *  ● Computes ETA via Haversine distance + speed
 *  ● Tracks route history for polyline drawing
 *  ● Observes trip status changes in real-time
 *
 * Security:
 *  ● No write operations
 *  ● No access to raw Trip / Driver / Vehicle objects
 *  ● Phone is always masked
 *  ● Internal IDs are kept private (inside PublicTripInfo.internal*)
 *
 * Lifecycle:
 *  ● Shared across fragments via activityViewModels()
 *  ● Cleaned up when PublicTrackingActivity finishes
 *
 * @since 2026-02-22
 */
class PublicTrackingViewModel : BaseViewModel() {

    // ─── Repository ─────────────────────────────────────────────
    private val repository = PublicTrackingRepositoryImpl()

    // ═══════════════════════════════════════════════════════════
    //  SCREEN STATE
    // ═══════════════════════════════════════════════════════════

    /** Overall tracking state — drives Enter screen UI */
    private val _trackingState = MutableStateFlow<PublicTrackingState>(PublicTrackingState.Idle)
    val trackingState: StateFlow<PublicTrackingState> = _trackingState.asStateFlow()

    /** The currently resolved trip info (persists across screens) */
    private val _tripInfo = MutableStateFlow<PublicTripInfo?>(null)
    val tripInfo: StateFlow<PublicTripInfo?> = _tripInfo.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  DRIVER & VEHICLE INFO
    // ═══════════════════════════════════════════════════════════

    private val _driverInfo = MutableStateFlow<PublicDriverInfo?>(null)
    val driverInfo: StateFlow<PublicDriverInfo?> = _driverInfo.asStateFlow()

    private val _vehicleInfo = MutableStateFlow<PublicVehicleInfo?>(null)
    val vehicleInfo: StateFlow<PublicVehicleInfo?> = _vehicleInfo.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  LIVE LOCATION
    // ═══════════════════════════════════════════════════════════

    /** Latest sanitised vehicle position */
    private val _liveLocation = MutableStateFlow<PublicLiveLocation?>(null)
    val liveLocation: StateFlow<PublicLiveLocation?> = _liveLocation.asStateFlow()

    /** Connection state for the live location stream */
    private val _connectionState = MutableStateFlow(LiveConnectionState.CONNECTING)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    /** Status text shown below the map */
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  ETA & DISTANCE
    // ═══════════════════════════════════════════════════════════

    /** Estimated time of arrival in minutes (null if not computable) */
    private val _etaMinutes = MutableStateFlow<Int?>(null)
    val etaMinutes: StateFlow<Int?> = _etaMinutes.asStateFlow()

    /** Remaining distance to drop-off in km */
    private val _distanceRemainingKm = MutableStateFlow<Double?>(null)
    val distanceRemainingKm: StateFlow<Double?> = _distanceRemainingKm.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  ROUTE HISTORY (for polyline)
    // ═══════════════════════════════════════════════════════════

    data class LatLng(val lat: Double, val lng: Double)

    private val _routeHistory = MutableStateFlow<List<LatLng>>(emptyList())
    val routeHistory: StateFlow<List<LatLng>> = _routeHistory.asStateFlow()
    private val routePoints = mutableListOf<LatLng>()
    private var lastLocationTimestamp = 0L

    // ═══════════════════════════════════════════════════════════
    //  TIMELINE
    // ═══════════════════════════════════════════════════════════

    private val _timeline = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timeline: StateFlow<List<TimelineEvent>> = _timeline.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  RECENT SEARCHES
    // ═══════════════════════════════════════════════════════════

    private val _recentSearches = MutableStateFlow<List<RecentSearch>>(emptyList())
    val recentSearches: StateFlow<List<RecentSearch>> = _recentSearches.asStateFlow()

    private var prefs: SharedPreferences? = null

    // ─── Internal state ─────────────────────────────────────────
    private var locationObserveJob: Job? = null
    private var tripObserveJob: Job? = null
    private var currentTrackingId: String = ""

    // ═══════════════════════════════════════════════════════════
    //  INITIALISATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Initialize with application context to access SharedPreferences.
     * Must be called once from the Activity before fragments use the VM.
     */
    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE
            )
            loadRecentSearches()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  TRACKING ID SEARCH
    // ═══════════════════════════════════════════════════════════

    /**
     * Search for a trip by tracking ID.
     *
     * Flow:
     *  1. Validate input format
     *  2. Query Firestore
     *  3. If found → emit Found state
     *  4. If not found → emit TrackingIdInvalid
     *  5. Save to recent searches
     */
    fun searchTrackingId(trackingId: String) {
        val sanitised = trackingId.trim().uppercase()

        // ── Input validation ────────────────────────────────
        if (sanitised.isBlank()) {
            _trackingState.value = PublicTrackingState.TrackingIdInvalid(
                "Please enter a tracking ID"
            )
            return
        }

        if (sanitised.length < 4) {
            _trackingState.value = PublicTrackingState.TrackingIdInvalid(
                "Tracking ID must be at least 4 characters"
            )
            return
        }

        // ── Already searching ───────────────────────────────
        if (_trackingState.value is PublicTrackingState.Searching) return

        currentTrackingId = sanitised
        _trackingState.value = PublicTrackingState.Searching

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.findTripByTrackingId(sanitised)

            when (result) {
                is ResultState.Success -> {
                    val tripInfo = result.data
                    if (tripInfo != null) {
                        handleTripFound(tripInfo, sanitised)
                    } else {
                        _trackingState.value = PublicTrackingState.TrackingIdInvalid(
                            "No delivery found with tracking ID \"$sanitised\". " +
                            "Please check the ID and try again."
                        )
                    }
                }
                is ResultState.Error -> {
                    _trackingState.value = PublicTrackingState.NetworkError(
                        result.message.ifBlank {
                            "Unable to connect. Please check your internet and try again."
                        }
                    )
                }
                else -> {
                    // Loading / Idle — shouldn't happen here
                }
            }
        }
    }

    /**
     * Process a successfully found trip.
     */
    private suspend fun handleTripFound(tripInfo: PublicTripInfo, trackingId: String) {
        _tripInfo.value = tripInfo

        // Build timeline
        _timeline.value = tripInfo.buildTimeline()

        // Check if trip is expired (completed/cancelled long ago)
        if (tripInfo.status.isTerminal) {
            val endTime = if (tripInfo.endTime > 0L) tripInfo.endTime else tripInfo.createdAt
            val daysSinceEnd = (System.currentTimeMillis() - endTime) / 86_400_000L
            if (daysSinceEnd > 30) {
                _trackingState.value = PublicTrackingState.TrackingExpired(
                    "This delivery was ${tripInfo.statusDisplayName.lowercase()} " +
                    "more than 30 days ago. Tracking data is no longer available."
                )
                saveRecentSearch(trackingId, "Expired")
                return
            }
        }

        _trackingState.value = PublicTrackingState.Found(tripInfo)

        // Save recent search
        saveRecentSearch(trackingId, tripInfo.statusDisplayName)

        // Load ancillary data in parallel
        loadDriverInfo(tripInfo.internalDriverId)
        loadVehicleInfo(tripInfo.internalVehicleId)
    }

    /**
     * Reset tracking state back to Idle.
     */
    fun resetState() {
        stopLiveTracking()
        _trackingState.value = PublicTrackingState.Idle
        _tripInfo.value = null
        _driverInfo.value = null
        _vehicleInfo.value = null
        _liveLocation.value = null
        _connectionState.value = LiveConnectionState.CONNECTING
        _etaMinutes.value = null
        _distanceRemainingKm.value = null
        _timeline.value = emptyList()
        _statusMessage.value = ""
        routePoints.clear()
        _routeHistory.value = emptyList()
        lastLocationTimestamp = 0L
        currentTrackingId = ""
    }

    /**
     * Retry the last search.
     */
    fun retry() {
        if (currentTrackingId.isNotBlank()) {
            _trackingState.value = PublicTrackingState.Idle
            searchTrackingId(currentTrackingId)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LIVE TRACKING
    // ═══════════════════════════════════════════════════════════

    /**
     * Start observing the vehicle's live location.
     * Called when the user navigates to the LiveTracking screen.
     *
     * Also starts observing the trip document for status changes.
     */
    fun startLiveTracking() {
        val trip = _tripInfo.value ?: return
        val companyId = trip.internalCompanyId
        val vehicleId = trip.internalVehicleId

        if (companyId.isBlank() || vehicleId.isBlank()) {
            _connectionState.value = LiveConnectionState.ERROR
            _statusMessage.value = "Vehicle tracking not available for this delivery"
            return
        }

        // ── Observe vehicle location ────────────────────────
        startLocationObservation(companyId, vehicleId)

        // ── Observe trip status changes ─────────────────────
        startTripObservation(trip.trackingId)
    }

    /**
     * Stop all live observation jobs.
     */
    fun stopLiveTracking() {
        locationObserveJob?.cancel()
        locationObserveJob = null
        tripObserveJob?.cancel()
        tripObserveJob = null
    }

    /**
     * Start observing vehicle location from RTDB.
     */
    private fun startLocationObservation(companyId: String, vehicleId: String) {
        locationObserveJob?.cancel()
        _connectionState.value = LiveConnectionState.CONNECTING
        _statusMessage.value = "Connecting to live tracking..."

        locationObserveJob = viewModelScope.launch {
            repository.observeVehicleLocation(companyId, vehicleId)
                .catch { e ->
                    _connectionState.value = LiveConnectionState.ERROR
                    _statusMessage.value = "Connection lost. Please check your internet."
                    emitError(e.message ?: "Tracking connection error")
                }
                .collect { result ->
                    when (result) {
                        is ResultState.Success -> {
                            val location = result.data
                            if (location != null && location.isValid) {
                                handleLocationUpdate(location)
                            } else {
                                _connectionState.value = LiveConnectionState.VEHICLE_OFFLINE
                                _statusMessage.value = "Vehicle is currently offline. " +
                                    "Location will update when the driver reconnects."
                            }
                        }
                        is ResultState.Loading -> {
                            _connectionState.value = LiveConnectionState.CONNECTING
                            _statusMessage.value = "Connecting..."
                        }
                        is ResultState.Error -> {
                            _connectionState.value = LiveConnectionState.ERROR
                            _statusMessage.value = result.message.ifBlank {
                                "Unable to get live location"
                            }
                        }
                        is ResultState.Idle -> { /* ignore */ }
                    }
                }
        }
    }

    /**
     * Start observing trip status for real-time updates.
     */
    private fun startTripObservation(trackingId: String) {
        tripObserveJob?.cancel()

        tripObserveJob = viewModelScope.launch {
            repository.observeTripByTrackingId(trackingId)
                .catch { /* non-critical, we still have the initial snapshot */ }
                .collect { result ->
                    if (result is ResultState.Success && result.data != null) {
                        val updatedTrip = result.data
                        _tripInfo.value = updatedTrip
                        _timeline.value = updatedTrip.buildTimeline()

                        // If trip just completed, update status
                        if (updatedTrip.isCompleted) {
                            _statusMessage.value = "Your delivery has been completed! 🎉"
                        } else if (updatedTrip.isCancelled) {
                            _statusMessage.value = "This delivery has been cancelled."
                        }
                    }
                }
        }
    }

    /**
     * Process a new live location update.
     */
    private fun handleLocationUpdate(location: PublicLiveLocation) {
        _liveLocation.value = location

        // Check freshness — if last update > 60s ago, mark offline
        val timeSince = System.currentTimeMillis() - location.timestamp
        if (timeSince > 120_000) { // 2 minutes
            _connectionState.value = LiveConnectionState.VEHICLE_OFFLINE
            _statusMessage.value = "Vehicle location may be outdated. " +
                "Last update: ${formatTimeSince(timeSince)}"
        } else {
            _connectionState.value = LiveConnectionState.CONNECTED
            _statusMessage.value = when {
                location.isMoving -> "Vehicle is moving • ${
                    "%.0f".format(location.speedKmh)
                } km/h"
                else -> "Vehicle is stopped"
            }
        }

        // Track route history
        if (location.timestamp != lastLocationTimestamp) {
            lastLocationTimestamp = location.timestamp
            routePoints.add(LatLng(location.lat, location.lng))
            // Keep last 500 points
            if (routePoints.size > MAX_ROUTE_POINTS) {
                routePoints.removeAt(0)
            }
            _routeHistory.value = routePoints.toList()
        }

        // Compute ETA
        computeEta(location)
    }

    // ═══════════════════════════════════════════════════════════
    //  ETA COMPUTATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Compute estimated time of arrival using:
     *  - Haversine distance from current position to drop-off
     *  - Current speed (or assumed 30 km/h if stationary)
     */
    private fun computeEta(location: PublicLiveLocation) {
        val trip = _tripInfo.value ?: return
        if (!trip.hasDrop) return

        val distanceKm = haversineDistance(
            location.lat, location.lng,
            trip.dropLat, trip.dropLng
        )
        _distanceRemainingKm.value = distanceKm

        // Use current speed if moving, else assume 30 km/h
        val speedKmh = if (location.speedKmh > 5f) {
            location.speedKmh.toDouble()
        } else {
            DEFAULT_SPEED_KMH
        }

        val etaHours = distanceKm / speedKmh
        val etaMins = (etaHours * 60).toInt().coerceAtLeast(1)
        _etaMinutes.value = etaMins
    }

    /**
     * Haversine formula for great-circle distance in kilometres.
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
    //  DRIVER & VEHICLE
    // ═══════════════════════════════════════════════════════════

    private fun loadDriverInfo(driverId: String) {
        if (driverId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getDriverInfo(driverId)
            if (result is ResultState.Success) {
                _driverInfo.value = result.data
            }
        }
    }

    private fun loadVehicleInfo(vehicleId: String) {
        if (vehicleId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.getVehicleInfo(vehicleId)
            if (result is ResultState.Success) {
                _vehicleInfo.value = result.data
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  RECENT SEARCHES (SharedPreferences)
    // ═══════════════════════════════════════════════════════════

    /**
     * Load recent searches from SharedPreferences.
     */
    private fun loadRecentSearches() {
        val prefs = this.prefs ?: return
        val json = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return

        try {
            val array = JSONArray(json)
            val searches = mutableListOf<RecentSearch>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                searches += RecentSearch(
                    trackingId = obj.getString("trackingId"),
                    statusLabel = obj.optString("statusLabel", ""),
                    searchedAt = obj.optLong("searchedAt", 0L)
                )
            }
            _recentSearches.value = searches
        } catch (e: Exception) {
            // Corrupted data — clear it
            prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
        }
    }

    /**
     * Save a tracking ID to recent searches.
     * Deduplicates and keeps only the last [MAX_RECENT_SEARCHES].
     */
    private fun saveRecentSearch(trackingId: String, statusLabel: String) {
        val prefs = this.prefs ?: return

        val current = _recentSearches.value.toMutableList()

        // Remove existing entry with same ID
        current.removeAll { it.trackingId == trackingId }

        // Prepend new entry
        current.add(0, RecentSearch(
            trackingId = trackingId,
            statusLabel = statusLabel,
            searchedAt = System.currentTimeMillis()
        ))

        // Trim to max
        while (current.size > MAX_RECENT_SEARCHES) {
            current.removeAt(current.lastIndex)
        }

        _recentSearches.value = current

        // Persist to SharedPreferences
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                current.forEach { search ->
                    val obj = JSONObject().apply {
                        put("trackingId", search.trackingId)
                        put("statusLabel", search.statusLabel)
                        put("searchedAt", search.searchedAt)
                    }
                    array.put(obj)
                }
                prefs.edit()
                    .putString(KEY_RECENT_SEARCHES, array.toString())
                    .apply()
            } catch (e: Exception) {
                // Non-critical — just log
            }
        }
    }

    /**
     * Clear a single recent search entry.
     */
    fun removeRecentSearch(trackingId: String) {
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.trackingId == trackingId }
        _recentSearches.value = current

        // Persist
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                current.forEach { search ->
                    val obj = JSONObject().apply {
                        put("trackingId", search.trackingId)
                        put("statusLabel", search.statusLabel)
                        put("searchedAt", search.searchedAt)
                    }
                    array.put(obj)
                }
                prefs?.edit()
                    ?.putString(KEY_RECENT_SEARCHES, array.toString())
                    ?.apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * Clear all recent searches.
     */
    fun clearAllRecentSearches() {
        _recentSearches.value = emptyList()
        prefs?.edit()?.remove(KEY_RECENT_SEARCHES)?.apply()
    }

    // ═══════════════════════════════════════════════════════════
    //  FORMATTERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Format ETA as "5 min", "1 hr 20 min", etc.
     */
    fun formatEta(minutes: Int): String = when {
        minutes < 1  -> "< 1 min"
        minutes < 60 -> "$minutes min"
        else -> {
            val hrs = minutes / 60
            val mins = minutes % 60
            if (mins == 0) "$hrs hr" else "$hrs hr $mins min"
        }
    }

    /**
     * Format distance as "500 m" or "12.3 km".
     */
    fun formatDistance(km: Double): String = when {
        km < 0.01  -> "—"
        km < 1.0   -> "${(km * 1000).toInt()} m"
        else       -> "%.1f km".format(km)
    }

    /**
     * Format time-since as "2 min ago", "1 hr ago", etc.
     */
    private fun formatTimeSince(millis: Long): String {
        val minutes = millis / 60_000
        val hours = minutes / 60
        return when {
            minutes < 1  -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24   -> "$hours hr ago"
            else         -> "${hours / 24} days ago"
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════════════════════

    override fun onCleared() {
        stopLiveTracking()
        super.onCleared()
    }

    // ═══════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════

    companion object {
        private const val PREFS_NAME = "movexa_public_tracking"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MAX_RECENT_SEARCHES = 10
        private const val MAX_ROUTE_POINTS = 500
        private const val DEFAULT_SPEED_KMH = 30.0
    }
}
