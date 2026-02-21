package com.example.movexa.service

import android.util.Log
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.GeoPoint
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.AlertStatus
import com.example.movexa.data.model.enums.AlertType
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Driver Behaviour Monitoring & Alert System — Analysis Engine.
 *
 * Analyzes real-time GPS tracking data to detect unsafe driving patterns and
 * automatically generates safety alerts stored in Firestore.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * DETECTION CAPABILITIES
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. OVERSPEED
 *    - Triggers when speed > configured limit for [OVERSPEED_DURATION_THRESHOLD_MS]
 *    - Configurable speed limit per vehicle type (default 80 km/h)
 *    - Severity escalates with magnitude (80–100 = HIGH, 100+ = CRITICAL)
 *
 * 2. HARSH_BRAKING
 *    - Detects rapid deceleration (speed drop > threshold within window)
 *    - Uses acceleration magnitude from consecutive readings
 *    - Threshold: deceleration > 4 m/s² (configurable)
 *
 * 3. HARSH_ACCELERATION
 *    - Detects aggressive acceleration (speed gain > threshold within window)
 *    - Threshold: acceleration > 4 m/s² (configurable)
 *
 * 4. LONG_IDLE
 *    - Triggers when speed ≈ 0 for extended duration while trip is active
 *    - Default threshold: 10 minutes of continuous idle
 *    - Ignores short stops (traffic lights, toll booths)
 *
 * 5. ROUTE_DEVIATION
 *    - Measures perpendicular distance from vehicle to planned route polyline
 *    - Triggers when off-route distance exceeds threshold (default 500m)
 *    - Requires route waypoints to be set via [setRoutePolyline]
 *
 * 6. ACCIDENT_SUSPECTED
 *    - Detects sudden stop from high speed (violent deceleration)
 *    - Combines: high speed → near-zero speed in < 2 seconds
 *    - Critical priority — requires immediate manager attention
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * THROTTLING & DEDUPLICATION
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Each alert type has a configurable cooldown period per vehicle.
 * The engine will NOT generate duplicate alerts within the cooldown window.
 *
 * Default cooldowns:
 *   OVERSPEED          = 60 seconds
 *   HARSH_BRAKING      = 30 seconds
 *   HARSH_ACCELERATION = 30 seconds
 *   LONG_IDLE          = 300 seconds (5 min)
 *   ROUTE_DEVIATION    = 120 seconds (2 min)
 *   ACCIDENT_SUSPECTED = 600 seconds (10 min)
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * LocationTrackingService
 *       ↓ (each location update)
 * BehaviorAnalysisEngine.analyze(location)
 *       ↓ (if alert detected)
 * AlertRepositoryImpl.createAlert()
 *       ↓
 * Firestore alerts/{alertId}
 *       ↓ (real-time listener)
 * ManagerAlertsFragment observes and displays
 *
 * The engine maintains per-vehicle state using [VehicleAnalysisState] which
 * stores a ring buffer of recent locations and timing data for each detector.
 *
 * @param scope Coroutine scope for alert persistence (from service)
 */
class BehaviorAnalysisEngine(
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "BehaviorAnalysisEngine"

        // ─── Speed Thresholds ───────────────────────────────────
        /** Default maximum speed in km/h before overspeed alert */
        const val DEFAULT_SPEED_LIMIT_KMH = 80f

        /** High-speed overspeed threshold for CRITICAL priority */
        const val CRITICAL_SPEED_LIMIT_KMH = 120f

        /** Duration vehicle must exceed speed limit before alert (ms) */
        const val OVERSPEED_DURATION_THRESHOLD_MS = 5_000L

        // ─── Acceleration / Braking Thresholds ──────────────────
        /** Harsh braking deceleration threshold in m/s² (absolute) */
        const val HARSH_BRAKING_THRESHOLD_MS2 = 4.0f

        /** Harsh acceleration threshold in m/s² */
        const val HARSH_ACCELERATION_THRESHOLD_MS2 = 4.0f

        /** Minimum speed before braking to consider it harsh (km/h) */
        const val MIN_SPEED_FOR_HARSH_BRAKING_KMH = 15.0f

        // ─── Idle Thresholds ────────────────────────────────────
        /** Speed below which vehicle is considered idle (m/s) */
        const val IDLE_SPEED_THRESHOLD_MS = 0.5f

        /** Duration of idling before alert (ms) — 10 minutes */
        const val LONG_IDLE_DURATION_MS = 10 * 60 * 1_000L

        // ─── Route Deviation Thresholds ─────────────────────────
        /** Maximum allowed distance from route in meters */
        const val ROUTE_DEVIATION_THRESHOLD_M = 500.0

        /** Minimum speed to consider route deviation (km/h) — ignores stationary */
        const val MIN_SPEED_FOR_DEVIATION_KMH = 5.0f

        // ─── Accident Detection Thresholds ──────────────────────
        /** Minimum speed before suspected accident (km/h) */
        const val ACCIDENT_MIN_SPEED_KMH = 40.0f

        /** Maximum final speed after suspected accident (km/h) */
        const val ACCIDENT_MAX_FINAL_SPEED_KMH = 5.0f

        /** Maximum time window for speed drop (ms) — 3 seconds */
        const val ACCIDENT_TIME_WINDOW_MS = 3_000L

        /** Deceleration magnitude for accident detection (m/s²) */
        const val ACCIDENT_DECELERATION_THRESHOLD_MS2 = 8.0f

        // ─── Alert Cooldown Periods (ms) ────────────────────────
        private val ALERT_COOLDOWNS = mapOf(
            AlertType.OVER_SPEED to 60_000L,
            AlertType.HARSH_BRAKING to 30_000L,
            AlertType.HARSH_ACCELERATION to 30_000L,
            AlertType.LONG_IDLE to 300_000L,
            AlertType.ROUTE_DEVIATION to 120_000L,
            AlertType.ACCIDENT_SUSPECTED to 600_000L
        )

        // ─── Ring Buffer Size ───────────────────────────────────
        /** Number of recent locations to keep per vehicle for analysis */
        const val LOCATION_BUFFER_SIZE = 30

        /** Earth radius in meters for Haversine calculations */
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    // ═══════════════════════════════════════════════════════════
    //  Dependencies
    // ═══════════════════════════════════════════════════════════

    private val alertRepository = AlertRepositoryImpl()

    // ═══════════════════════════════════════════════════════════
    //  Per-Vehicle State
    // ═══════════════════════════════════════════════════════════

    /**
     * Encapsulates all analysis state for a single vehicle.
     * Maintains a ring buffer of recent locations and timing data
     * for each detection algorithm.
     */
    data class VehicleAnalysisState(
        /** Ring buffer of recent location readings */
        val locationBuffer: ArrayDeque<TrackingLocation> = ArrayDeque(),

        /** Timestamp when overspeed condition started (0 = not overspeeding) */
        var overspeedStartTime: Long = 0L,

        /** Whether overspeed alert has already been fired for current episode */
        var overspeedAlertFired: Boolean = false,

        /** Peak speed during current overspeed episode (km/h) */
        var overspeedPeakKmh: Float = 0f,

        /** Timestamp when idle condition started (0 = not idle) */
        var idleStartTime: Long = 0L,

        /** Whether idle alert has already been fired for current episode */
        var idleAlertFired: Boolean = false,

        /** Last alert timestamps per type for cooldown enforcement */
        val lastAlertTimes: MutableMap<AlertType, Long> = mutableMapOf(),

        /** Total alerts generated for this vehicle (session counter) */
        var totalAlertsGenerated: Int = 0,

        /** Configured speed limit for this vehicle (overridable) */
        var speedLimitKmh: Float = DEFAULT_SPEED_LIMIT_KMH
    )

    /** Thread-safe map of vehicle analysis states */
    private val vehicleStates = ConcurrentHashMap<String, VehicleAnalysisState>()

    /** Optional route polylines per vehicle for deviation detection */
    private val routePolylines = ConcurrentHashMap<String, List<GeoPoint>>()

    /** Company ID for alert creation */
    private var companyId: String = ""

    // ═══════════════════════════════════════════════════════════
    //  Configuration
    // ═══════════════════════════════════════════════════════════

    /**
     * Initialize the engine with company context.
     * Must be called before [analyze].
     *
     * @param companyId The company identifier for alert association.
     */
    fun initialize(companyId: String) {
        this.companyId = companyId
        Log.i(TAG, "BehaviorAnalysisEngine initialized for company: $companyId")
    }

    /**
     * Set a custom speed limit for a specific vehicle.
     * Overrides the default [DEFAULT_SPEED_LIMIT_KMH].
     *
     * @param vehicleId Vehicle to configure.
     * @param speedLimitKmh Maximum allowed speed in km/h.
     */
    fun setVehicleSpeedLimit(vehicleId: String, speedLimitKmh: Float) {
        getOrCreateState(vehicleId).speedLimitKmh = speedLimitKmh
        Log.d(TAG, "Speed limit for $vehicleId set to $speedLimitKmh km/h")
    }

    /**
     * Set the planned route polyline for a vehicle.
     * Required for route deviation detection.
     *
     * @param vehicleId Vehicle identifier.
     * @param waypoints Ordered list of route waypoints.
     */
    fun setRoutePolyline(vehicleId: String, waypoints: List<GeoPoint>) {
        if (waypoints.size >= 2) {
            routePolylines[vehicleId] = waypoints
            Log.d(TAG, "Route polyline set for $vehicleId: ${waypoints.size} waypoints")
        }
    }

    /**
     * Clear route polyline for a vehicle (e.g., trip completed).
     */
    fun clearRoutePolyline(vehicleId: String) {
        routePolylines.remove(vehicleId)
    }

    /**
     * Clear all state for a vehicle (e.g., when tracking stops).
     */
    fun clearVehicleState(vehicleId: String) {
        vehicleStates.remove(vehicleId)
        routePolylines.remove(vehicleId)
        Log.d(TAG, "Cleared analysis state for $vehicleId")
    }

    /**
     * Clear all engine state (e.g., when service stops).
     */
    fun clearAll() {
        vehicleStates.clear()
        routePolylines.clear()
        Log.i(TAG, "All analysis states cleared")
    }

    // ═══════════════════════════════════════════════════════════
    //  Main Analysis Entry Point
    // ═══════════════════════════════════════════════════════════

    /**
     * Analyze a new tracking location for behavioral anomalies.
     *
     * This is the primary entry point called from [LocationTrackingService]
     * each time a new GPS fix is obtained. It runs all detection algorithms
     * and creates alerts in Firestore when anomalies are detected.
     *
     * The method is designed to be fast and non-blocking — alert persistence
     * is dispatched to a coroutine and does not block the caller.
     *
     * @param location The new tracking location to analyze.
     */
    fun analyze(location: TrackingLocation) {
        if (companyId.isBlank()) {
            Log.w(TAG, "Engine not initialized — skipping analysis")
            return
        }

        if (!location.isValid) {
            return // Skip invalid coordinates
        }

        val vehicleId = location.vehicleId
        if (vehicleId.isBlank()) return

        val state = getOrCreateState(vehicleId)

        // Add to ring buffer
        addToBuffer(state, location)

        // Need at least 2 readings for comparison
        if (state.locationBuffer.size < 2) return

        val previousLocation = state.locationBuffer.let { buffer ->
            if (buffer.size >= 2) buffer.elementAt(buffer.size - 2) else null
        } ?: return

        // ─── Run All Detectors ──────────────────────────────────
        checkOverspeed(state, location)
        checkHarshBraking(state, location, previousLocation)
        checkHarshAcceleration(state, location, previousLocation)
        checkLongIdle(state, location)
        checkRouteDeviation(state, location)
        checkAccidentSuspected(state, location, previousLocation)
    }

    // ═══════════════════════════════════════════════════════════
    //  Detection Algorithms
    // ═══════════════════════════════════════════════════════════

    // ─── 1. Overspeed Detection ─────────────────────────────────

    /**
     * Detects sustained overspeeding.
     *
     * Algorithm:
     * 1. Convert speed from m/s to km/h
     * 2. If speed > vehicle speed limit → start/continue overspeed timer
     * 3. If timer exceeds [OVERSPEED_DURATION_THRESHOLD_MS] → fire alert
     * 4. Alert priority based on magnitude:
     *    - limit to 100 km/h → HIGH
     *    - > 100 km/h → CRITICAL
     * 5. Track peak speed during episode for alert message
     * 6. Reset when speed drops below limit
     */
    private fun checkOverspeed(state: VehicleAnalysisState, location: TrackingLocation) {
        val currentSpeedKmh = location.speedKmh
        val limit = state.speedLimitKmh

        if (currentSpeedKmh > limit) {
            // Track peak speed
            if (currentSpeedKmh > state.overspeedPeakKmh) {
                state.overspeedPeakKmh = currentSpeedKmh
            }

            if (state.overspeedStartTime == 0L) {
                // Start timing the overspeed episode
                state.overspeedStartTime = location.timestamp
                state.overspeedAlertFired = false
                Log.d(TAG, "Overspeed started: ${currentSpeedKmh.toInt()} km/h > ${limit.toInt()} km/h")
            } else if (!state.overspeedAlertFired) {
                val duration = location.timestamp - state.overspeedStartTime
                if (duration >= OVERSPEED_DURATION_THRESHOLD_MS) {
                    // Sustained overspeed — generate alert
                    val priority = when {
                        state.overspeedPeakKmh >= CRITICAL_SPEED_LIMIT_KMH -> AlertPriority.CRITICAL
                        state.overspeedPeakKmh >= limit + 30 -> AlertPriority.HIGH
                        state.overspeedPeakKmh >= limit + 15 -> AlertPriority.HIGH
                        else -> AlertPriority.MEDIUM
                    }

                    fireAlert(
                        type = AlertType.OVER_SPEED,
                        vehicleId = location.vehicleId,
                        driverId = location.driverId,
                        tripId = location.tripId,
                        priority = priority,
                        title = "Overspeed Detected",
                        message = "Vehicle traveling at ${state.overspeedPeakKmh.toInt()} km/h " +
                                "(limit: ${limit.toInt()} km/h) for ${duration / 1000}s",
                        location = location.toGeoPoint(),
                        metadata = mapOf(
                            "peakSpeedKmh" to state.overspeedPeakKmh,
                            "speedLimitKmh" to limit,
                            "durationMs" to duration,
                            "currentSpeedKmh" to currentSpeedKmh
                        )
                    )
                    state.overspeedAlertFired = true
                }
            }
        } else {
            // Speed back to normal — reset episode
            if (state.overspeedStartTime != 0L) {
                Log.d(TAG, "Overspeed ended: speed back to ${currentSpeedKmh.toInt()} km/h")
                state.overspeedStartTime = 0L
                state.overspeedAlertFired = false
                state.overspeedPeakKmh = 0f
            }
        }
    }

    // ─── 2. Harsh Braking Detection ─────────────────────────────

    /**
     * Detects harsh braking events.
     *
     * Algorithm:
     * 1. Calculate deceleration from consecutive readings:
     *    acceleration = (v2 - v1) / Δt
     * 2. If deceleration exceeds threshold AND previous speed was significant:
     *    → fire alert
     * 3. Severity based on deceleration magnitude:
     *    - 4-6 m/s² → MEDIUM (hard braking)
     *    - 6-8 m/s² → HIGH (harsh braking)
     *    - > 8 m/s² → CRITICAL (emergency stop / near-accident)
     */
    private fun checkHarshBraking(
        state: VehicleAnalysisState,
        current: TrackingLocation,
        previous: TrackingLocation
    ) {
        val timeDeltaMs = current.timestamp - previous.timestamp
        if (timeDeltaMs <= 0 || timeDeltaMs > 15_000) return // Skip stale data

        val timeDeltaSec = timeDeltaMs / 1000.0f
        val speedDifference = current.speed - previous.speed // m/s
        val acceleration = speedDifference / timeDeltaSec // m/s²

        // Deceleration is negative acceleration
        val deceleration = -acceleration

        // Check: previous speed must be significant (avoid noise from stationary)
        val previousSpeedKmh = previous.speedKmh
        if (previousSpeedKmh < MIN_SPEED_FOR_HARSH_BRAKING_KMH) return

        if (deceleration >= HARSH_BRAKING_THRESHOLD_MS2) {
            val priority = when {
                deceleration >= ACCIDENT_DECELERATION_THRESHOLD_MS2 -> AlertPriority.CRITICAL
                deceleration >= 6.0f -> AlertPriority.HIGH
                else -> AlertPriority.MEDIUM
            }

            fireAlert(
                type = AlertType.HARSH_BRAKING,
                vehicleId = current.vehicleId,
                driverId = current.driverId,
                tripId = current.tripId,
                priority = priority,
                title = "Harsh Braking Detected",
                message = "Rapid deceleration of %.1f m/s² detected. ".format(deceleration) +
                        "Speed dropped from ${previousSpeedKmh.toInt()} to ${current.speedKmh.toInt()} km/h " +
                        "in %.1f seconds".format(timeDeltaSec),
                location = current.toGeoPoint(),
                metadata = mapOf(
                    "decelerationMs2" to deceleration,
                    "previousSpeedKmh" to previousSpeedKmh,
                    "currentSpeedKmh" to current.speedKmh,
                    "timeDeltaMs" to timeDeltaMs
                )
            )
        }
    }

    // ─── 3. Harsh Acceleration Detection ────────────────────────

    /**
     * Detects aggressive acceleration events.
     *
     * Algorithm:
     * 1. Calculate acceleration from consecutive readings
     * 2. If acceleration exceeds threshold → fire alert
     * 3. Severity based on magnitude:
     *    - 4-6 m/s² → LOW (firm acceleration)
     *    - 6-8 m/s² → MEDIUM (aggressive)
     *    - > 8 m/s² → HIGH (dangerous)
     */
    private fun checkHarshAcceleration(
        state: VehicleAnalysisState,
        current: TrackingLocation,
        previous: TrackingLocation
    ) {
        val timeDeltaMs = current.timestamp - previous.timestamp
        if (timeDeltaMs <= 0 || timeDeltaMs > 15_000) return

        val timeDeltaSec = timeDeltaMs / 1000.0f
        val speedDifference = current.speed - previous.speed // m/s
        val acceleration = speedDifference / timeDeltaSec // m/s²

        if (acceleration >= HARSH_ACCELERATION_THRESHOLD_MS2) {
            val priority = when {
                acceleration >= 8.0f -> AlertPriority.HIGH
                acceleration >= 6.0f -> AlertPriority.MEDIUM
                else -> AlertPriority.LOW
            }

            fireAlert(
                type = AlertType.HARSH_ACCELERATION,
                vehicleId = current.vehicleId,
                driverId = current.driverId,
                tripId = current.tripId,
                priority = priority,
                title = "Harsh Acceleration Detected",
                message = "Rapid acceleration of %.1f m/s² detected. ".format(acceleration) +
                        "Speed increased from ${previous.speedKmh.toInt()} to ${current.speedKmh.toInt()} km/h " +
                        "in %.1f seconds".format(timeDeltaSec),
                location = current.toGeoPoint(),
                metadata = mapOf(
                    "accelerationMs2" to acceleration,
                    "previousSpeedKmh" to previous.speedKmh,
                    "currentSpeedKmh" to current.speedKmh,
                    "timeDeltaMs" to timeDeltaMs
                )
            )
        }
    }

    // ─── 4. Long Idle Detection ─────────────────────────────────

    /**
     * Detects prolonged vehicle idling while a trip is active.
     *
     * Algorithm:
     * 1. If speed < idle threshold → start/continue idle timer
     * 2. If idle timer exceeds [LONG_IDLE_DURATION_MS] → fire alert
     * 3. Only triggers when tripId is present (ignores parked vehicles)
     * 4. Priority based on duration:
     *    - 10-20 min → LOW
     *    - 20-30 min → MEDIUM
     *    - > 30 min → HIGH
     * 5. Only fires once per idle episode (resets on movement)
     */
    private fun checkLongIdle(state: VehicleAnalysisState, location: TrackingLocation) {
        // Only check idle during active trips
        if (location.tripId.isBlank()) {
            state.idleStartTime = 0L
            state.idleAlertFired = false
            return
        }

        val isIdle = location.speed < IDLE_SPEED_THRESHOLD_MS

        if (isIdle) {
            if (state.idleStartTime == 0L) {
                // Start idle timing
                state.idleStartTime = location.timestamp
                state.idleAlertFired = false
            } else if (!state.idleAlertFired) {
                val idleDuration = location.timestamp - state.idleStartTime
                if (idleDuration >= LONG_IDLE_DURATION_MS) {
                    val idleMinutes = idleDuration / 60_000

                    val priority = when {
                        idleMinutes >= 30 -> AlertPriority.HIGH
                        idleMinutes >= 20 -> AlertPriority.MEDIUM
                        else -> AlertPriority.LOW
                    }

                    fireAlert(
                        type = AlertType.LONG_IDLE,
                        vehicleId = location.vehicleId,
                        driverId = location.driverId,
                        tripId = location.tripId,
                        priority = priority,
                        title = "Long Idle Detected",
                        message = "Vehicle has been stationary for $idleMinutes minutes " +
                                "while trip is active",
                        location = location.toGeoPoint(),
                        metadata = mapOf(
                            "idleDurationMs" to idleDuration,
                            "idleMinutes" to idleMinutes,
                            "tripId" to location.tripId
                        )
                    )
                    state.idleAlertFired = true
                }
            }
        } else {
            // Vehicle is moving — reset idle tracker
            if (state.idleStartTime != 0L) {
                state.idleStartTime = 0L
                state.idleAlertFired = false
            }
        }
    }

    // ─── 5. Route Deviation Detection ───────────────────────────

    /**
     * Detects when a vehicle deviates from its planned route.
     *
     * Algorithm:
     * 1. Requires route polyline to be set via [setRoutePolyline]
     * 2. Calculate minimum perpendicular distance from current position
     *    to each segment of the route polyline
     * 3. If minimum distance > [ROUTE_DEVIATION_THRESHOLD_M] → fire alert
     * 4. Only checks when vehicle is moving (speed > threshold)
     * 5. Priority based on deviation distance:
     *    - 500m-1km → MEDIUM
     *    - 1km-2km → HIGH
     *    - > 2km → CRITICAL
     */
    private fun checkRouteDeviation(state: VehicleAnalysisState, location: TrackingLocation) {
        val route = routePolylines[location.vehicleId] ?: return

        // Only check when vehicle is moving
        if (location.speedKmh < MIN_SPEED_FOR_DEVIATION_KMH) return

        // Must have at least 2 waypoints to form a segment
        if (route.size < 2) return

        val currentPoint = location.toGeoPoint()

        // Find minimum distance to any route segment
        var minDistance = Double.MAX_VALUE
        for (i in 0 until route.size - 1) {
            val segmentStart = route[i]
            val segmentEnd = route[i + 1]
            val distance = pointToSegmentDistance(currentPoint, segmentStart, segmentEnd)
            if (distance < minDistance) {
                minDistance = distance
            }
        }

        if (minDistance > ROUTE_DEVIATION_THRESHOLD_M) {
            val deviationKm = minDistance / 1000.0

            val priority = when {
                minDistance > 2000 -> AlertPriority.CRITICAL
                minDistance > 1000 -> AlertPriority.HIGH
                else -> AlertPriority.MEDIUM
            }

            fireAlert(
                type = AlertType.ROUTE_DEVIATION,
                vehicleId = location.vehicleId,
                driverId = location.driverId,
                tripId = location.tripId,
                priority = priority,
                title = "Route Deviation Detected",
                message = "Vehicle is %.1f km off the planned route".format(deviationKm),
                location = location.toGeoPoint(),
                metadata = mapOf(
                    "deviationMeters" to minDistance,
                    "deviationKm" to deviationKm,
                    "routeWaypointCount" to route.size,
                    "speedKmh" to location.speedKmh
                )
            )
        }
    }

    // ─── 6. Accident Suspected Detection ────────────────────────

    /**
     * Detects suspected accident conditions.
     *
     * Algorithm:
     * 1. Look at recent location buffer for rapid speed-to-zero transition
     * 2. Conditions (ALL must be met):
     *    a. Previous reading had speed > [ACCIDENT_MIN_SPEED_KMH]
     *    b. Current reading has speed < [ACCIDENT_MAX_FINAL_SPEED_KMH]
     *    c. Time between readings < [ACCIDENT_TIME_WINDOW_MS]
     *    d. Implied deceleration > [ACCIDENT_DECELERATION_THRESHOLD_MS2]
     * 3. OR: Check 3-reading pattern for impact detection:
     *    - High speed → very high deceleration → near-zero speed
     * 4. Always CRITICAL priority — requires immediate response
     *
     * False positive mitigation:
     * - Requires minimum starting speed (40 km/h)
     * - Requires extreme deceleration (> 8 m/s²)
     * - 10-minute cooldown between accident alerts
     */
    private fun checkAccidentSuspected(
        state: VehicleAnalysisState,
        current: TrackingLocation,
        previous: TrackingLocation
    ) {
        val timeDeltaMs = current.timestamp - previous.timestamp
        if (timeDeltaMs <= 0 || timeDeltaMs > ACCIDENT_TIME_WINDOW_MS) return

        val previousSpeedKmh = previous.speedKmh
        val currentSpeedKmh = current.speedKmh

        // Check basic conditions
        if (previousSpeedKmh < ACCIDENT_MIN_SPEED_KMH) return
        if (currentSpeedKmh > ACCIDENT_MAX_FINAL_SPEED_KMH) return

        // Calculate deceleration
        val timeDeltaSec = timeDeltaMs / 1000.0f
        val speedDropMs = previous.speed - current.speed // m/s
        val deceleration = speedDropMs / timeDeltaSec // m/s²

        if (deceleration >= ACCIDENT_DECELERATION_THRESHOLD_MS2) {
            Log.w(TAG, "ACCIDENT SUSPECTED: Vehicle ${current.vehicleId} — " +
                    "speed dropped from ${previousSpeedKmh.toInt()} to ${currentSpeedKmh.toInt()} km/h " +
                    "in ${timeDeltaMs}ms (deceleration: ${"%.1f".format(deceleration)} m/s²)")

            fireAlert(
                type = AlertType.ACCIDENT_SUSPECTED,
                vehicleId = current.vehicleId,
                driverId = current.driverId,
                tripId = current.tripId,
                priority = AlertPriority.CRITICAL,
                title = "⚠ Accident Suspected",
                message = "Sudden stop detected — vehicle speed dropped from " +
                        "${previousSpeedKmh.toInt()} km/h to ${currentSpeedKmh.toInt()} km/h " +
                        "in less than ${(timeDeltaMs / 1000.0).let { "%.1f".format(it) }} seconds. " +
                        "Immediate attention required.",
                location = current.toGeoPoint(),
                metadata = mapOf(
                    "previousSpeedKmh" to previousSpeedKmh,
                    "currentSpeedKmh" to currentSpeedKmh,
                    "decelerationMs2" to deceleration,
                    "timeDeltaMs" to timeDeltaMs,
                    "lat" to current.lat,
                    "lng" to current.lng
                )
            )
        }

        // Also check 3-reading pattern if buffer has enough data
        if (state.locationBuffer.size >= 3) {
            checkThreePointAccidentPattern(state, current)
        }
    }

    /**
     * Supplementary 3-point accident pattern check.
     *
     * Looks at the last 3 readings for a pattern:
     * [HIGH SPEED] → [DECELERATING] → [NEAR ZERO]
     *
     * This catches cases where the GPS update interval doesn't
     * capture the exact moment of impact.
     */
    private fun checkThreePointAccidentPattern(
        state: VehicleAnalysisState,
        current: TrackingLocation
    ) {
        val buffer = state.locationBuffer
        if (buffer.size < 3) return

        val reading3 = buffer.elementAt(buffer.size - 1) // current
        val reading2 = buffer.elementAt(buffer.size - 2) // previous
        val reading1 = buffer.elementAt(buffer.size - 3) // two readings ago

        val totalTimeMs = reading3.timestamp - reading1.timestamp
        if (totalTimeMs <= 0 || totalTimeMs > ACCIDENT_TIME_WINDOW_MS * 2) return

        val startSpeedKmh = reading1.speedKmh
        val endSpeedKmh = reading3.speedKmh
        val midSpeedKmh = reading2.speedKmh

        // Pattern: high → mid (may still be decelerating) → near zero
        if (startSpeedKmh >= ACCIDENT_MIN_SPEED_KMH &&
            endSpeedKmh <= ACCIDENT_MAX_FINAL_SPEED_KMH &&
            midSpeedKmh < startSpeedKmh // confirming deceleration
        ) {
            val totalTimeSec = totalTimeMs / 1000.0f
            val speedDropMs = reading1.speed - reading3.speed
            val avgDeceleration = speedDropMs / totalTimeSec

            if (avgDeceleration >= ACCIDENT_DECELERATION_THRESHOLD_MS2 * 0.75f) {
                // Don't double-fire if 2-point check already caught it
                // (cooldown will handle this)
                fireAlert(
                    type = AlertType.ACCIDENT_SUSPECTED,
                    vehicleId = current.vehicleId,
                    driverId = current.driverId,
                    tripId = current.tripId,
                    priority = AlertPriority.CRITICAL,
                    title = "⚠ Accident Suspected (Pattern)",
                    message = "Impact pattern detected — vehicle speed dropped from " +
                            "${startSpeedKmh.toInt()} km/h to ${endSpeedKmh.toInt()} km/h " +
                            "over ${(totalTimeMs / 1000.0).let { "%.1f".format(it) }} seconds. " +
                            "Immediate attention required.",
                    location = current.toGeoPoint(),
                    metadata = mapOf(
                        "patternType" to "three_point",
                        "startSpeedKmh" to startSpeedKmh,
                        "midSpeedKmh" to midSpeedKmh,
                        "endSpeedKmh" to endSpeedKmh,
                        "avgDecelerationMs2" to avgDeceleration,
                        "totalTimeMs" to totalTimeMs
                    )
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Alert Firing with Throttle / Dedup
    // ═══════════════════════════════════════════════════════════

    /**
     * Fire an alert with automatic throttling and deduplication.
     *
     * Checks the per-vehicle cooldown for the given alert type.
     * If within cooldown period, the alert is silently dropped.
     * Otherwise, persists to Firestore asynchronously.
     *
     * @param type Alert type
     * @param vehicleId Vehicle identifier
     * @param driverId Driver identifier
     * @param tripId Active trip identifier (may be blank)
     * @param priority Alert priority level
     * @param title Short alert title
     * @param message Detailed alert message
     * @param location GPS coordinates where alert occurred
     * @param metadata Additional key-value data for analytics
     */
    private fun fireAlert(
        type: AlertType,
        vehicleId: String,
        driverId: String,
        tripId: String,
        priority: AlertPriority,
        title: String,
        message: String,
        location: GeoPoint,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val state = getOrCreateState(vehicleId)
        val now = System.currentTimeMillis()

        // ── Cooldown Check ──────────────────────────────────────
        val lastAlertTime = state.lastAlertTimes[type] ?: 0L
        val cooldown = ALERT_COOLDOWNS[type] ?: 60_000L

        if (now - lastAlertTime < cooldown) {
            Log.d(TAG, "Alert throttled: $type for $vehicleId " +
                    "(cooldown: ${(cooldown - (now - lastAlertTime)) / 1000}s remaining)")
            return
        }

        // Update last alert time
        state.lastAlertTimes[type] = now
        state.totalAlertsGenerated++

        // ── Create Alert Object ─────────────────────────────────
        val alert = Alert(
            alertId = "", // Will be auto-generated by Firestore
            type = type,
            vehicleId = vehicleId,
            driverId = driverId,
            tripId = tripId.ifBlank { null },
            companyId = companyId,
            priority = priority,
            status = AlertStatus.ACTIVE,
            title = title,
            message = message,
            timestamp = now,
            location = location,
            actionRequired = priority.isUrgent(),
            autoGenerated = true,
            metadata = metadata.toMutableMap().also {
                it["generatedBy"] = "BehaviorAnalysisEngine"
                it["engineVersion"] = "1.0.0"
            }
        )

        // ── Persist Asynchronously ──────────────────────────────
        scope.launch {
            try {
                val result = alertRepository.createAlert(alert)
                when (result) {
                    is ResultState.Success -> {
                        Log.i(TAG, "Alert created: $type for vehicle $vehicleId " +
                                "(priority: $priority, id: ${result.data})")
                    }
                    is ResultState.Error -> {
                        Log.e(TAG, "Failed to persist alert: ${result.message}")
                        // Alert failed to persist — roll back cooldown so it can retry
                        state.lastAlertTimes.remove(type)
                        state.totalAlertsGenerated--
                    }
                    else -> { /* Idle/Loading — ignore */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception persisting alert", e)
                state.lastAlertTimes.remove(type)
                state.totalAlertsGenerated--
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility Methods
    // ═══════════════════════════════════════════════════════════

    /**
     * Get or create the analysis state for a vehicle.
     */
    private fun getOrCreateState(vehicleId: String): VehicleAnalysisState {
        return vehicleStates.getOrPut(vehicleId) { VehicleAnalysisState() }
    }

    /**
     * Add a location to the vehicle's ring buffer, evicting oldest if full.
     */
    private fun addToBuffer(state: VehicleAnalysisState, location: TrackingLocation) {
        state.locationBuffer.addLast(location)
        while (state.locationBuffer.size > LOCATION_BUFFER_SIZE) {
            state.locationBuffer.removeFirst()
        }
    }

    // ─── Geo Math ───────────────────────────────────────────────

    /**
     * Calculate the Haversine distance between two geographic points.
     *
     * @return Distance in meters.
     */
    private fun haversineDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLng = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLng / 2).pow(2)
        val c = 2 * asin(sqrt(a))

        return EARTH_RADIUS_M * c
    }

    /**
     * Calculate the perpendicular distance from a point to a line segment.
     *
     * Uses the cross-track distance formula for great circle arcs
     * approximated to flat-earth for short distances (< 50km).
     *
     * @param point The current vehicle position.
     * @param segStart Start of the route segment.
     * @param segEnd End of the route segment.
     * @return Distance in meters from point to the nearest point on the segment.
     */
    private fun pointToSegmentDistance(
        point: GeoPoint,
        segStart: GeoPoint,
        segEnd: GeoPoint
    ): Double {
        val d1 = haversineDistance(segStart, point)
        val d2 = haversineDistance(segEnd, point)
        val segLength = haversineDistance(segStart, segEnd)

        // If segment is essentially a point, return distance to that point
        if (segLength < 1.0) return d1

        // Project the point onto the segment using dot product approach
        // (flat-earth approximation suitable for short segments)
        val cosLat = cos(Math.toRadians((segStart.latitude + segEnd.latitude) / 2))

        val dx1 = (point.longitude - segStart.longitude) * cosLat
        val dy1 = point.latitude - segStart.latitude
        val dx2 = (segEnd.longitude - segStart.longitude) * cosLat
        val dy2 = segEnd.latitude - segStart.latitude

        val dot = dx1 * dx2 + dy1 * dy2
        val lenSq = dx2 * dx2 + dy2 * dy2
        val t = (dot / lenSq).coerceIn(0.0, 1.0)

        // Nearest point on segment
        val nearestLat = segStart.latitude + t * dy2
        val nearestLng = segStart.longitude + t * (segEnd.longitude - segStart.longitude)
        val nearestPoint = GeoPoint(nearestLat, nearestLng)

        return haversineDistance(point, nearestPoint)
    }

    // ═══════════════════════════════════════════════════════════
    //  Statistics (for debugging / monitoring)
    // ═══════════════════════════════════════════════════════════

    /**
     * Get total alerts generated across all vehicles since engine start.
     */
    fun getTotalAlertsGenerated(): Int {
        return vehicleStates.values.sumOf { it.totalAlertsGenerated }
    }

    /**
     * Get the number of vehicles currently being monitored.
     */
    fun getMonitoredVehicleCount(): Int {
        return vehicleStates.size
    }

    /**
     * Get a summary of alerts generated per type.
     */
    fun getAlertSummary(): Map<String, Int> {
        val summary = mutableMapOf<String, Int>()
        vehicleStates.values.forEach { state ->
            state.lastAlertTimes.keys.forEach { type ->
                summary[type.name] = (summary[type.name] ?: 0) + 1
            }
        }
        return summary
    }
}
