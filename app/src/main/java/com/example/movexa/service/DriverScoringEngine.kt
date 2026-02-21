package com.example.movexa.service

import android.util.Log
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.FuelLog
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.AlertType
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import com.example.movexa.data.repository.impl.FuelLogRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Driver Performance Scoring Engine.
 *
 * ═══════════════════════════════════════════════════════════════
 * PURPOSE
 * ═══════════════════════════════════════════════════════════════
 *
 * Converts raw driving behaviour data (alerts, trips, fuel logs)
 * into a single numeric safety score (0–100) with a letter grade.
 *
 * The score is a CUMULATIVE metric that reflects the driver's
 * entire history, not just recent events. It starts at 100 for
 * new drivers and adjusts based on events.
 *
 * ═══════════════════════════════════════════════════════════════
 * SCORING RULES
 * ═══════════════════════════════════════════════════════════════
 *
 * PENALTIES (deductions from 100):
 *   - OVER_SPEED alert          → −5  points
 *   - HARSH_BRAKING alert       → −7  points
 *   - HARSH_ACCELERATION alert  → −4  points
 *   - LONG_IDLE alert           → −3  points
 *   - ROUTE_DEVIATION alert     → −4  points
 *   - ACCIDENT_SUSPECTED alert  → −20 points
 *   - Other behavioural alert   → −2  points
 *
 * BONUSES (added to score):
 *   - Trip completed            → +2  points
 *   - Good fuel efficiency      → +3  points (mileage > threshold)
 *   - Long streak (10+ trips
 *     without violation)        → +5  points
 *
 * Score is clamped to [0, 100].
 *
 * ═══════════════════════════════════════════════════════════════
 * GRADE TABLE
 * ═══════════════════════════════════════════════════════════════
 *
 *  90–100 → Excellent  (Green)
 *  75–89  → Good       (Teal)
 *  60–74  → Average    (Orange)
 *   0–59  → Risky      (Red)
 *
 * ═══════════════════════════════════════════════════════════════
 * PERSISTENCE
 * ═══════════════════════════════════════════════════════════════
 *
 * Results are stored in Firestore at:
 *   driver_summary/{driverId}
 *
 * The engine reads alert, trip, and fuel data; computes the score
 * from scratch; and persists the [DriverSummary] document.
 *
 * ═══════════════════════════════════════════════════════════════
 * ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════
 *
 * This engine is STATELESS — it reads all data, computes, and
 * writes. It uses a per-driver mutex to prevent concurrent
 * recalculations for the same driver.
 *
 * Trigger points (called from ViewModel or service layer):
 *   - [recalculateScore] — full recalculation from Firestore data
 *   - [applyAlertPenalty] — incremental penalty from new alert
 *   - [applyTripCompletion] — incremental bonus from trip end
 *   - [applyFuelBonus] — incremental bonus from fuel log
 *
 * ═══════════════════════════════════════════════════════════════
 * THROTTLING
 * ═══════════════════════════════════════════════════════════════
 *
 * Full recalculation is rate-limited to once per 30 seconds per
 * driver to avoid Firestore read storms. Incremental updates are
 * not throttled since they only read/write the summary doc.
 */
class DriverScoringEngine(
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "DriverScoringEngine"

        // ── Penalty Points ──────────────────────────────────────
        private const val PENALTY_OVERSPEED = 5
        private const val PENALTY_HARSH_BRAKING = 7
        private const val PENALTY_HARSH_ACCELERATION = 4
        private const val PENALTY_LONG_IDLE = 3
        private const val PENALTY_ROUTE_DEVIATION = 4
        private const val PENALTY_ACCIDENT = 20
        private const val PENALTY_OTHER_BEHAVIORAL = 2

        // ── Bonus Points ────────────────────────────────────────
        private const val BONUS_TRIP_COMPLETED = 2
        private const val BONUS_FUEL_EFFICIENCY = 3
        private const val BONUS_CLEAN_STREAK = 5
        private const val CLEAN_STREAK_THRESHOLD = 10

        // ── Thresholds ──────────────────────────────────────────
        private const val GOOD_MILEAGE_THRESHOLD = 10.0 // km/L
        private const val BASE_SCORE = 100
        private const val MIN_SCORE = 0
        private const val MAX_SCORE = 100

        // ── Throttle ────────────────────────────────────────────
        private const val RECALC_COOLDOWN_MS = 30_000L // 30 seconds

        // ── Score History ───────────────────────────────────────
        private const val MAX_HISTORY_ENTRIES = 30
    }

    // ─── Repositories ───────────────────────────────────────────
    private val alertRepository = AlertRepositoryImpl()
    private val tripRepository = TripRepositoryImpl()
    private val fuelLogRepository = FuelLogRepositoryImpl()
    private val firestore = FirebaseFirestore.getInstance()

    // ─── Concurrency ────────────────────────────────────────────
    private val driverMutexMap = mutableMapOf<String, Mutex>()
    private val lastRecalcTime = mutableMapOf<String, Long>()

    /**
     * Get or create a Mutex for a specific driver.
     */
    @Synchronized
    private fun getMutex(driverId: String): Mutex {
        return driverMutexMap.getOrPut(driverId) { Mutex() }
    }

    // ═══════════════════════════════════════════════════════════
    //  FULL RECALCULATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Perform a complete score recalculation from all Firestore data.
     *
     * Reads all alerts, trips, and fuel logs for the driver,
     * computes the score from scratch, and persists the summary.
     *
     * @param driverId The driver to recalculate
     * @param companyId The driver's company
     * @return The computed [DriverSummary], or null on failure
     */
    suspend fun recalculateScore(
        driverId: String,
        companyId: String
    ): DriverSummary? {
        // Throttle check
        val now = System.currentTimeMillis()
        val lastTime = lastRecalcTime[driverId] ?: 0L
        if (now - lastTime < RECALC_COOLDOWN_MS) {
            Log.d(TAG, "Recalculation throttled for driver=$driverId")
            return getExistingSummary(driverId)
        }

        return getMutex(driverId).withLock {
            try {
                lastRecalcTime[driverId] = now

                // ── 1. Fetch all driver alerts ──────────────────
                val alerts = fetchDriverAlerts(driverId)

                // ── 2. Fetch all driver trips ───────────────────
                val trips = fetchDriverTrips(driverId)

                // ── 3. Fetch all driver fuel logs ───────────────
                val fuelLogs = fetchDriverFuelLogs(driverId)

                // ── 4. Compute violation counts ─────────────────
                val violationBreakdown = countViolations(alerts)

                // ── 5. Compute trip stats ───────────────────────
                val completedTrips = trips.filter { it.status == TripStatus.COMPLETED }
                val totalDistance = completedTrips.sumOf { it.distance }
                val totalDurationMinutes = completedTrips.sumOf { it.durationMinutes }

                // ── 6. Compute fuel efficiency ──────────────────
                val avgMileage = computeAverageMileage(fuelLogs)
                val fuelBonusCount = countFuelBonuses(fuelLogs)

                // ── 7. Compute clean streak bonus ───────────────
                val cleanStreakBonus = computeCleanStreakBonus(
                    trips = completedTrips,
                    alerts = alerts
                )

                // ── 8. Calculate total penalties ────────────────
                val totalPenalties = computeTotalPenalties(violationBreakdown)

                // ── 9. Calculate total bonuses ──────────────────
                val tripBonus = completedTrips.size * BONUS_TRIP_COMPLETED
                val fuelBonus = fuelBonusCount * BONUS_FUEL_EFFICIENCY
                val totalBonuses = tripBonus + fuelBonus + cleanStreakBonus

                // ── 10. Compute final score ─────────────────────
                val rawScore = BASE_SCORE - totalPenalties + totalBonuses
                val finalScore = rawScore.coerceIn(MIN_SCORE, MAX_SCORE)
                val grade = DriverSummary.gradeFromScore(finalScore)

                // ── 11. Build score history entry ───────────────
                val existingSummary = getExistingSummary(driverId)
                val updatedHistory = buildScoreHistory(
                    existingHistory = existingSummary?.scoreHistory ?: emptyList(),
                    newScore = finalScore
                )

                // ── 12. Build and persist summary ───────────────
                val summary = DriverSummary(
                    driverId = driverId,
                    companyId = companyId,
                    score = finalScore,
                    grade = grade,
                    completedTrips = completedTrips.size,
                    totalDistance = totalDistance,
                    totalDrivingHours = totalDurationMinutes,
                    violationsCount = violationBreakdown.total,
                    overspeedCount = violationBreakdown.overspeed,
                    harshBrakingCount = violationBreakdown.harshBraking,
                    harshAccelCount = violationBreakdown.harshAccel,
                    longIdleCount = violationBreakdown.longIdle,
                    routeDeviationCount = violationBreakdown.routeDeviation,
                    accidentCount = violationBreakdown.accident,
                    fuelEfficiencyBonus = fuelBonus,
                    tripCompletionBonus = tripBonus,
                    totalPenalties = totalPenalties,
                    averageMileage = avgMileage,
                    scoreHistory = updatedHistory,
                    lastTripId = completedTrips.maxByOrNull { it.endTime }?.tripId ?: "",
                    lastUpdated = now,
                    createdAt = existingSummary?.createdAt ?: now
                )

                persistSummary(summary)

                Log.i(TAG, "Score recalculated: driver=$driverId score=$finalScore " +
                        "grade=$grade penalties=$totalPenalties bonuses=$totalBonuses")

                summary
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recalculate score for driver=$driverId", e)
                null
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INCREMENTAL UPDATES
    // ═══════════════════════════════════════════════════════════

    /**
     * Apply an incremental penalty from a new behavioural alert.
     *
     * Does NOT do a full recalculation — reads the existing summary,
     * decrements the score, increments the violation counter, and
     * writes back. This is fast and lightweight.
     *
     * @param driverId The driver who triggered the alert
     * @param companyId The driver's company
     * @param alertType The type of alert to penalize
     */
    suspend fun applyAlertPenalty(
        driverId: String,
        companyId: String,
        alertType: AlertType
    ) {
        getMutex(driverId).withLock {
            try {
                val existing = getExistingSummary(driverId) ?: createDefaultSummary(
                    driverId, companyId
                )

                val penalty = getPenaltyForType(alertType)
                if (penalty <= 0) return

                val newScore = (existing.score - penalty).coerceIn(MIN_SCORE, MAX_SCORE)
                val newGrade = DriverSummary.gradeFromScore(newScore)

                val updatedHistory = buildScoreHistory(existing.scoreHistory, newScore)

                val updated = existing.copy(
                    score = newScore,
                    grade = newGrade,
                    violationsCount = existing.violationsCount + 1,
                    overspeedCount = existing.overspeedCount +
                            if (alertType == AlertType.OVER_SPEED) 1 else 0,
                    harshBrakingCount = existing.harshBrakingCount +
                            if (alertType == AlertType.HARSH_BRAKING) 1 else 0,
                    harshAccelCount = existing.harshAccelCount +
                            if (alertType == AlertType.HARSH_ACCELERATION) 1 else 0,
                    longIdleCount = existing.longIdleCount +
                            if (alertType == AlertType.LONG_IDLE) 1 else 0,
                    routeDeviationCount = existing.routeDeviationCount +
                            if (alertType == AlertType.ROUTE_DEVIATION) 1 else 0,
                    accidentCount = existing.accidentCount +
                            if (alertType == AlertType.ACCIDENT_SUSPECTED) 1 else 0,
                    totalPenalties = existing.totalPenalties + penalty,
                    scoreHistory = updatedHistory,
                    lastUpdated = System.currentTimeMillis()
                )

                persistSummary(updated)

                Log.d(TAG, "Alert penalty applied: driver=$driverId type=$alertType " +
                        "penalty=-$penalty newScore=$newScore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply alert penalty for driver=$driverId", e)
            }
        }
    }

    /**
     * Apply an incremental bonus when a trip is completed.
     *
     * @param driverId The driver who completed the trip
     * @param companyId The driver's company
     * @param trip The completed trip (used for distance/duration)
     */
    suspend fun applyTripCompletion(
        driverId: String,
        companyId: String,
        trip: Trip
    ) {
        getMutex(driverId).withLock {
            try {
                val existing = getExistingSummary(driverId) ?: createDefaultSummary(
                    driverId, companyId
                )

                val newScore = (existing.score + BONUS_TRIP_COMPLETED)
                    .coerceIn(MIN_SCORE, MAX_SCORE)
                val newGrade = DriverSummary.gradeFromScore(newScore)

                val updatedHistory = buildScoreHistory(existing.scoreHistory, newScore)

                val updated = existing.copy(
                    score = newScore,
                    grade = newGrade,
                    completedTrips = existing.completedTrips + 1,
                    totalDistance = existing.totalDistance + trip.distance,
                    totalDrivingHours = existing.totalDrivingHours + trip.durationMinutes,
                    tripCompletionBonus = existing.tripCompletionBonus + BONUS_TRIP_COMPLETED,
                    scoreHistory = updatedHistory,
                    lastTripId = trip.tripId,
                    lastUpdated = System.currentTimeMillis()
                )

                persistSummary(updated)

                Log.d(TAG, "Trip completion bonus applied: driver=$driverId " +
                        "bonus=+$BONUS_TRIP_COMPLETED newScore=$newScore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply trip completion bonus for driver=$driverId", e)
            }
        }
    }

    /**
     * Apply fuel efficiency bonus when a fuel log shows good mileage.
     *
     * @param driverId The driver who logged fuel
     * @param companyId The driver's company
     * @param fuelLog The fuel log entry
     */
    suspend fun applyFuelBonus(
        driverId: String,
        companyId: String,
        fuelLog: FuelLog
    ) {
        getMutex(driverId).withLock {
            try {
                // Only grant bonus if mileage exceeds threshold
                if (fuelLog.mileage < GOOD_MILEAGE_THRESHOLD) {
                    Log.d(TAG, "Fuel mileage ${fuelLog.mileage} below threshold " +
                            "$GOOD_MILEAGE_THRESHOLD, no bonus")
                    return
                }

                val existing = getExistingSummary(driverId) ?: createDefaultSummary(
                    driverId, companyId
                )

                val newScore = (existing.score + BONUS_FUEL_EFFICIENCY)
                    .coerceIn(MIN_SCORE, MAX_SCORE)
                val newGrade = DriverSummary.gradeFromScore(newScore)

                // Recalculate average mileage
                val totalLogCount = existing.completedTrips.coerceAtLeast(1)
                val newAvgMileage = if (existing.averageMileage > 0) {
                    (existing.averageMileage * (totalLogCount - 1) + fuelLog.mileage) /
                            totalLogCount
                } else {
                    fuelLog.mileage
                }

                val updatedHistory = buildScoreHistory(existing.scoreHistory, newScore)

                val updated = existing.copy(
                    score = newScore,
                    grade = newGrade,
                    fuelEfficiencyBonus = existing.fuelEfficiencyBonus + BONUS_FUEL_EFFICIENCY,
                    averageMileage = newAvgMileage,
                    scoreHistory = updatedHistory,
                    lastUpdated = System.currentTimeMillis()
                )

                persistSummary(updated)

                Log.d(TAG, "Fuel efficiency bonus applied: driver=$driverId " +
                        "mileage=${fuelLog.mileage} bonus=+$BONUS_FUEL_EFFICIENCY")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply fuel bonus for driver=$driverId", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PENALTY CALCULATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the penalty points for a given alert type.
     */
    fun getPenaltyForType(alertType: AlertType): Int {
        return when (alertType) {
            AlertType.OVER_SPEED -> PENALTY_OVERSPEED
            AlertType.HARSH_BRAKING -> PENALTY_HARSH_BRAKING
            AlertType.HARSH_ACCELERATION -> PENALTY_HARSH_ACCELERATION
            AlertType.LONG_IDLE -> PENALTY_LONG_IDLE
            AlertType.ROUTE_DEVIATION -> PENALTY_ROUTE_DEVIATION
            AlertType.ACCIDENT_SUSPECTED -> PENALTY_ACCIDENT
            else -> if (alertType.isBehavioral) PENALTY_OTHER_BEHAVIORAL else 0
        }
    }

    /**
     * Count violations by type from alert list.
     */
    private fun countViolations(alerts: List<Alert>): ViolationBreakdown {
        var overspeed = 0
        var harshBraking = 0
        var harshAccel = 0
        var longIdle = 0
        var routeDeviation = 0
        var accident = 0

        for (alert in alerts) {
            if (!alert.type.isBehavioral) continue
            when (alert.type) {
                AlertType.OVER_SPEED -> overspeed++
                AlertType.HARSH_BRAKING -> harshBraking++
                AlertType.HARSH_ACCELERATION -> harshAccel++
                AlertType.LONG_IDLE -> longIdle++
                AlertType.ROUTE_DEVIATION -> routeDeviation++
                AlertType.ACCIDENT_SUSPECTED -> accident++
                else -> { /* non-behavioral, skip */ }
            }
        }

        val total = overspeed + harshBraking + harshAccel + longIdle +
                routeDeviation + accident

        return ViolationBreakdown(
            overspeed = overspeed,
            harshBraking = harshBraking,
            harshAccel = harshAccel,
            longIdle = longIdle,
            routeDeviation = routeDeviation,
            accident = accident,
            total = total
        )
    }

    /**
     * Compute total penalty points from violation breakdown.
     */
    private fun computeTotalPenalties(breakdown: ViolationBreakdown): Int {
        return (breakdown.overspeed * PENALTY_OVERSPEED) +
                (breakdown.harshBraking * PENALTY_HARSH_BRAKING) +
                (breakdown.harshAccel * PENALTY_HARSH_ACCELERATION) +
                (breakdown.longIdle * PENALTY_LONG_IDLE) +
                (breakdown.routeDeviation * PENALTY_ROUTE_DEVIATION) +
                (breakdown.accident * PENALTY_ACCIDENT)
    }

    // ═══════════════════════════════════════════════════════════
    //  BONUS CALCULATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Count fuel logs that qualify for the efficiency bonus.
     */
    private fun countFuelBonuses(fuelLogs: List<FuelLog>): Int {
        return fuelLogs.count { it.mileage >= GOOD_MILEAGE_THRESHOLD }
    }

    /**
     * Compute average mileage from fuel logs.
     */
    private fun computeAverageMileage(fuelLogs: List<FuelLog>): Double {
        val validLogs = fuelLogs.filter { it.mileage > 0 }
        if (validLogs.isEmpty()) return 0.0
        return validLogs.sumOf { it.mileage } / validLogs.size
    }

    /**
     * Compute bonus points for clean driving streaks.
     *
     * A "clean streak" is a sequence of N consecutive trips
     * (by start time) with no behavioural alerts during that trip.
     * Each complete [CLEAN_STREAK_THRESHOLD] trip streak earns
     * [BONUS_CLEAN_STREAK] points.
     */
    private fun computeCleanStreakBonus(
        trips: List<Trip>,
        alerts: List<Alert>
    ): Int {
        if (trips.isEmpty()) return 0

        // Build set of trip IDs that have behavioural violations
        val violatedTripIds = alerts
            .filter { it.type.isBehavioral && !it.tripId.isNullOrBlank() }
            .map { it.tripId }
            .toSet()

        // Count consecutive clean trips
        val sortedTrips = trips.sortedBy { it.startTime }
        var currentStreak = 0
        var maxStreakBonuses = 0

        for (trip in sortedTrips) {
            if (trip.tripId !in violatedTripIds) {
                currentStreak++
                if (currentStreak >= CLEAN_STREAK_THRESHOLD) {
                    maxStreakBonuses++
                    currentStreak = 0 // Reset after earning bonus
                }
            } else {
                currentStreak = 0
            }
        }

        return maxStreakBonuses * BONUS_CLEAN_STREAK
    }

    // ═══════════════════════════════════════════════════════════
    //  SCORE HISTORY
    // ═══════════════════════════════════════════════════════════

    /**
     * Build updated score history by appending a new entry.
     * Keeps only the last [MAX_HISTORY_ENTRIES] entries.
     */
    private fun buildScoreHistory(
        existingHistory: List<Map<String, Any>>,
        newScore: Int
    ): List<Map<String, Any>> {
        val entry = mapOf<String, Any>(
            "score" to newScore,
            "timestamp" to System.currentTimeMillis()
        )

        val updated = existingHistory.toMutableList()
        updated.add(entry)

        // Trim to max size
        while (updated.size > MAX_HISTORY_ENTRIES) {
            updated.removeAt(0)
        }

        return updated
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA FETCHING
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch all behavioural alerts for a driver.
     */
    private suspend fun fetchDriverAlerts(driverId: String): List<Alert> {
        return when (val result = alertRepository.getAlertsByDriver(driverId)) {
            is ResultState.Success -> result.data.filter { it.type.isBehavioral }
            else -> {
                Log.w(TAG, "Failed to fetch alerts for driver=$driverId")
                emptyList()
            }
        }
    }

    /**
     * Fetch all trips for a driver.
     */
    private suspend fun fetchDriverTrips(driverId: String): List<Trip> {
        return when (val result = tripRepository.getTripsByDriver(driverId)) {
            is ResultState.Success -> result.data
            else -> {
                Log.w(TAG, "Failed to fetch trips for driver=$driverId")
                emptyList()
            }
        }
    }

    /**
     * Fetch all fuel logs for a driver.
     */
    private suspend fun fetchDriverFuelLogs(driverId: String): List<FuelLog> {
        return when (val result = fuelLogRepository.getFuelLogsByDriver(driverId)) {
            is ResultState.Success -> result.data
            else -> {
                Log.w(TAG, "Failed to fetch fuel logs for driver=$driverId")
                emptyList()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════════════════════

    /**
     * Save [DriverSummary] to Firestore at driver_summary/{driverId}.
     */
    private suspend fun persistSummary(summary: DriverSummary) {
        try {
            firestore.collection(DriverSummary.COLLECTION_NAME)
                .document(summary.driverId)
                .set(summary.toMap())
                .await()

            Log.d(TAG, "Summary persisted: driver=${summary.driverId} " +
                    "score=${summary.score} grade=${summary.grade}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist summary for driver=${summary.driverId}", e)
            throw e
        }
    }

    /**
     * Read existing [DriverSummary] from Firestore, or null if not found.
     */
    suspend fun getExistingSummary(driverId: String): DriverSummary? {
        return try {
            val doc = firestore.collection(DriverSummary.COLLECTION_NAME)
                .document(driverId)
                .get()
                .await()

            if (doc.exists()) {
                doc.data?.let { DriverSummary.fromMap(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read existing summary for driver=$driverId", e)
            null
        }
    }

    /**
     * Create a default empty summary for a new driver.
     */
    private suspend fun createDefaultSummary(
        driverId: String,
        companyId: String
    ): DriverSummary {
        val summary = DriverSummary(
            driverId = driverId,
            companyId = companyId,
            score = BASE_SCORE,
            grade = DriverSummary.gradeFromScore(BASE_SCORE),
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )
        persistSummary(summary)
        return summary
    }

    // ═══════════════════════════════════════════════════════════
    //  ASYNC WRAPPERS (fire-and-forget from service layer)
    // ═══════════════════════════════════════════════════════════

    /**
     * Fire-and-forget recalculation.
     * Suitable for calling from [BehaviorAnalysisEngine] or service callbacks.
     */
    fun recalculateScoreAsync(driverId: String, companyId: String) {
        scope.launch(Dispatchers.IO) {
            recalculateScore(driverId, companyId)
        }
    }

    /**
     * Fire-and-forget alert penalty.
     */
    fun applyAlertPenaltyAsync(
        driverId: String,
        companyId: String,
        alertType: AlertType
    ) {
        scope.launch(Dispatchers.IO) {
            applyAlertPenalty(driverId, companyId, alertType)
        }
    }

    /**
     * Fire-and-forget trip completion bonus.
     */
    fun applyTripCompletionAsync(
        driverId: String,
        companyId: String,
        trip: Trip
    ) {
        scope.launch(Dispatchers.IO) {
            applyTripCompletion(driverId, companyId, trip)
        }
    }

    /**
     * Fire-and-forget fuel bonus.
     */
    fun applyFuelBonusAsync(
        driverId: String,
        companyId: String,
        fuelLog: FuelLog
    ) {
        scope.launch(Dispatchers.IO) {
            applyFuelBonus(driverId, companyId, fuelLog)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SUGGESTIONS ENGINE
    // ═══════════════════════════════════════════════════════════

    /**
     * Generate dynamic driving suggestions based on the driver's
     * violation pattern and score.
     *
     * @return List of [DrivingSuggestion] sorted by relevance
     */
    fun generateSuggestions(summary: DriverSummary): List<DrivingSuggestion> {
        val suggestions = mutableListOf<DrivingSuggestion>()

        // ── Overspeed suggestions ───────────────────────────────
        if (summary.overspeedCount > 0) {
            val severity = when {
                summary.overspeedCount >= 10 -> DrivingSuggestion.Severity.HIGH
                summary.overspeedCount >= 5 -> DrivingSuggestion.Severity.MEDIUM
                else -> DrivingSuggestion.Severity.LOW
            }
            suggestions.add(
                DrivingSuggestion(
                    title = "Reduce Speed",
                    message = "You've had ${summary.overspeedCount} overspeed events. " +
                            "Maintaining speed limits improves safety and saves fuel.",
                    icon = SuggestionIcon.SPEED,
                    severity = severity,
                    penaltyImpact = summary.overspeedCount * PENALTY_OVERSPEED
                )
            )
        }

        // ── Harsh braking suggestions ───────────────────────────
        if (summary.harshBrakingCount > 0) {
            val severity = when {
                summary.harshBrakingCount >= 8 -> DrivingSuggestion.Severity.HIGH
                summary.harshBrakingCount >= 4 -> DrivingSuggestion.Severity.MEDIUM
                else -> DrivingSuggestion.Severity.LOW
            }
            suggestions.add(
                DrivingSuggestion(
                    title = "Smoother Braking",
                    message = "You've had ${summary.harshBrakingCount} harsh braking events. " +
                            "Anticipate stops and brake gradually for safer driving.",
                    icon = SuggestionIcon.BRAKING,
                    severity = severity,
                    penaltyImpact = summary.harshBrakingCount * PENALTY_HARSH_BRAKING
                )
            )
        }

        // ── Harsh acceleration suggestions ──────────────────────
        if (summary.harshAccelCount > 0) {
            suggestions.add(
                DrivingSuggestion(
                    title = "Gentle Acceleration",
                    message = "You've had ${summary.harshAccelCount} harsh acceleration events. " +
                            "Accelerate smoothly to reduce wear and improve fuel economy.",
                    icon = SuggestionIcon.ACCELERATION,
                    severity = if (summary.harshAccelCount >= 5)
                        DrivingSuggestion.Severity.MEDIUM else DrivingSuggestion.Severity.LOW,
                    penaltyImpact = summary.harshAccelCount * PENALTY_HARSH_ACCELERATION
                )
            )
        }

        // ── Long idle suggestions ───────────────────────────────
        if (summary.longIdleCount > 0) {
            suggestions.add(
                DrivingSuggestion(
                    title = "Reduce Idle Time",
                    message = "You've had ${summary.longIdleCount} long idle events. " +
                            "Turn off the engine when stopped for extended periods.",
                    icon = SuggestionIcon.IDLE,
                    severity = if (summary.longIdleCount >= 5)
                        DrivingSuggestion.Severity.MEDIUM else DrivingSuggestion.Severity.LOW,
                    penaltyImpact = summary.longIdleCount * PENALTY_LONG_IDLE
                )
            )
        }

        // ── Route deviation suggestions ─────────────────────────
        if (summary.routeDeviationCount > 0) {
            suggestions.add(
                DrivingSuggestion(
                    title = "Follow Assigned Routes",
                    message = "You've had ${summary.routeDeviationCount} route deviations. " +
                            "Staying on assigned routes ensures timely deliveries.",
                    icon = SuggestionIcon.ROUTE,
                    severity = if (summary.routeDeviationCount >= 3)
                        DrivingSuggestion.Severity.MEDIUM else DrivingSuggestion.Severity.LOW,
                    penaltyImpact = summary.routeDeviationCount * PENALTY_ROUTE_DEVIATION
                )
            )
        }

        // ── Accident suggestions ────────────────────────────────
        if (summary.accidentCount > 0) {
            suggestions.add(
                DrivingSuggestion(
                    title = "Safety Priority",
                    message = "There have been ${summary.accidentCount} suspected accident " +
                            "events. Please review your driving habits and take safety training.",
                    icon = SuggestionIcon.ACCIDENT,
                    severity = DrivingSuggestion.Severity.HIGH,
                    penaltyImpact = summary.accidentCount * PENALTY_ACCIDENT
                )
            )
        }

        // ── Fuel efficiency suggestions ─────────────────────────
        if (summary.averageMileage > 0 && summary.averageMileage < GOOD_MILEAGE_THRESHOLD) {
            suggestions.add(
                DrivingSuggestion(
                    title = "Improve Fuel Efficiency",
                    message = "Your average mileage is ${summary.mileageDisplay}. " +
                            "Gentle driving and proper gear usage can help reach " +
                            "${GOOD_MILEAGE_THRESHOLD} km/L.",
                    icon = SuggestionIcon.FUEL,
                    severity = DrivingSuggestion.Severity.LOW,
                    penaltyImpact = 0
                )
            )
        }

        // ── Positive reinforcement ──────────────────────────────
        if (summary.score >= 90 && summary.completedTrips > 0) {
            suggestions.add(
                DrivingSuggestion(
                    title = "Excellent Performance!",
                    message = "You're among the safest drivers. Keep up the great work! " +
                            "Your score of ${summary.score} shows outstanding driving habits.",
                    icon = SuggestionIcon.TROPHY,
                    severity = DrivingSuggestion.Severity.POSITIVE,
                    penaltyImpact = 0
                )
            )
        } else if (summary.violationsCount == 0 && summary.completedTrips > 0) {
            suggestions.add(
                DrivingSuggestion(
                    title = "Clean Record",
                    message = "No violations recorded! You're maintaining a clean driving " +
                            "record across ${summary.completedTrips} trips.",
                    icon = SuggestionIcon.TROPHY,
                    severity = DrivingSuggestion.Severity.POSITIVE,
                    penaltyImpact = 0
                )
            )
        }

        // Sort: HIGH severity first, then MEDIUM, then LOW, POSITIVE last
        return suggestions.sortedByDescending { it.severity.weight }
    }

    // ═══════════════════════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════════════════════

    /**
     * Clear all cached mutex and throttle state.
     */
    fun clearAll() {
        driverMutexMap.clear()
        lastRecalcTime.clear()
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════

    /**
     * Breakdown of violations by type.
     */
    data class ViolationBreakdown(
        val overspeed: Int = 0,
        val harshBraking: Int = 0,
        val harshAccel: Int = 0,
        val longIdle: Int = 0,
        val routeDeviation: Int = 0,
        val accident: Int = 0,
        val total: Int = 0
    )

    /**
     * Driving suggestion generated from violation patterns.
     */
    data class DrivingSuggestion(
        val title: String,
        val message: String,
        val icon: SuggestionIcon,
        val severity: Severity,
        val penaltyImpact: Int = 0
    ) {
        enum class Severity(val weight: Int) {
            POSITIVE(0),
            LOW(1),
            MEDIUM(2),
            HIGH(3)
        }
    }

    /**
     * Icons for suggestion cards.
     */
    enum class SuggestionIcon {
        SPEED,
        BRAKING,
        ACCELERATION,
        IDLE,
        ROUTE,
        ACCIDENT,
        FUEL,
        TROPHY
    }
}
