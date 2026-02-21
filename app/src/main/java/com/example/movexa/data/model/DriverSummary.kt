package com.example.movexa.data.model

/**
 * Driver performance summary persisted in Firestore.
 *
 * Firestore path: driver_summary/{driverId}
 *
 * ═══════════════════════════════════════════════════════════════
 * FIELDS
 * ═══════════════════════════════════════════════════════════════
 *
 * @property driverId         Unique driver identifier (document ID)
 * @property companyId        Company the driver belongs to
 * @property score            Current performance score (0–100)
 * @property grade            Human-readable grade derived from score
 * @property completedTrips   Total trips completed successfully
 * @property totalDistance     Total distance driven in km
 * @property totalDrivingHours Total driving hours (stored in minutes)
 * @property violationsCount  Lifetime count of behavioural violations
 * @property overspeedCount   Count of overspeed events
 * @property harshBrakingCount Count of harsh braking events
 * @property harshAccelCount  Count of harsh acceleration events
 * @property longIdleCount    Count of long idle events
 * @property routeDeviationCount Route deviation incidents
 * @property accidentCount    Accident suspected events
 * @property fuelEfficiencyBonus Bonus points earned from fuel efficiency
 * @property tripCompletionBonus Bonus points earned from trip completions
 * @property totalPenalties   Total penalty points deducted
 * @property averageMileage   Average fuel mileage (km/L)
 * @property scoreHistory     Recent score snapshots for trend line
 * @property lastTripId       Last trip that affected the score
 * @property lastUpdated      Timestamp of last score update
 * @property createdAt        When the summary was first created
 *
 * ═══════════════════════════════════════════════════════════════
 * GRADE TABLE
 * ═══════════════════════════════════════════════════════════════
 *
 *  90–100 → Excellent
 *  75–89  → Good
 *  60–74  → Average
 *   0–59  → Risky
 */
data class DriverSummary(
    val driverId: String = "",
    val companyId: String = "",
    val score: Int = 100,
    val grade: String = GRADE_EXCELLENT,
    val completedTrips: Int = 0,
    val totalDistance: Double = 0.0,
    val totalDrivingHours: Long = 0L,
    val violationsCount: Int = 0,
    val overspeedCount: Int = 0,
    val harshBrakingCount: Int = 0,
    val harshAccelCount: Int = 0,
    val longIdleCount: Int = 0,
    val routeDeviationCount: Int = 0,
    val accidentCount: Int = 0,
    val fuelEfficiencyBonus: Int = 0,
    val tripCompletionBonus: Int = 0,
    val totalPenalties: Int = 0,
    val averageMileage: Double = 0.0,
    val scoreHistory: List<Map<String, Any>> = emptyList(),
    val lastTripId: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {

    // ─── Computed Properties ────────────────────────────────────

    /**
     * Formatted driving hours from stored minutes.
     */
    val drivingHoursDisplay: String
        get() {
            val hours = totalDrivingHours / 60
            val mins = totalDrivingHours % 60
            return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }

    /**
     * Formatted distance (e.g. "1,245.3 km").
     */
    val distanceDisplay: String
        get() = "%,.1f km".format(totalDistance)

    /**
     * Formatted mileage (e.g. "12.5 km/L").
     */
    val mileageDisplay: String
        get() = if (averageMileage > 0) "%.1f km/L".format(averageMileage) else "—"

    /**
     * Score as a normalized float 0.0–1.0 for progress indicators.
     */
    val scoreProgress: Float
        get() = score.coerceIn(0, 100) / 100f

    /**
     * Whether the score is trending up, down, or stable.
     * Uses the last two entries in [scoreHistory].
     */
    val trend: ScoreTrend
        get() {
            if (scoreHistory.size < 2) return ScoreTrend.STABLE
            val lastTwo = scoreHistory.takeLast(2)
            val prev = (lastTwo[0]["score"] as? Number)?.toInt() ?: score
            val curr = (lastTwo[1]["score"] as? Number)?.toInt() ?: score
            return when {
                curr > prev -> ScoreTrend.UP
                curr < prev -> ScoreTrend.DOWN
                else -> ScoreTrend.STABLE
            }
        }

    /**
     * Check if the driver's performance is concerning.
     */
    val isRisky: Boolean
        get() = score < 60

    /**
     * Check if the summary has any data (not a fresh default).
     */
    val hasData: Boolean
        get() = completedTrips > 0 || violationsCount > 0

    // ─── Serialization ──────────────────────────────────────────

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "driverId" to driverId,
            "companyId" to companyId,
            "score" to score,
            "grade" to grade,
            "completedTrips" to completedTrips,
            "totalDistance" to totalDistance,
            "totalDrivingHours" to totalDrivingHours,
            "violationsCount" to violationsCount,
            "overspeedCount" to overspeedCount,
            "harshBrakingCount" to harshBrakingCount,
            "harshAccelCount" to harshAccelCount,
            "longIdleCount" to longIdleCount,
            "routeDeviationCount" to routeDeviationCount,
            "accidentCount" to accidentCount,
            "fuelEfficiencyBonus" to fuelEfficiencyBonus,
            "tripCompletionBonus" to tripCompletionBonus,
            "totalPenalties" to totalPenalties,
            "averageMileage" to averageMileage,
            "scoreHistory" to scoreHistory,
            "lastTripId" to lastTripId,
            "lastUpdated" to lastUpdated,
            "createdAt" to createdAt
        )
    }

    companion object {
        const val COLLECTION_NAME = "driver_summary"

        // Grade constants
        const val GRADE_EXCELLENT = "Excellent"
        const val GRADE_GOOD = "Good"
        const val GRADE_AVERAGE = "Average"
        const val GRADE_RISKY = "Risky"

        /**
         * Compute grade from a numeric score.
         */
        fun gradeFromScore(score: Int): String {
            return when {
                score >= 90 -> GRADE_EXCELLENT
                score >= 75 -> GRADE_GOOD
                score >= 60 -> GRADE_AVERAGE
                else -> GRADE_RISKY
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): DriverSummary {
            return DriverSummary(
                driverId = map["driverId"] as? String ?: "",
                companyId = map["companyId"] as? String ?: "",
                score = (map["score"] as? Number)?.toInt() ?: 100,
                grade = map["grade"] as? String ?: GRADE_EXCELLENT,
                completedTrips = (map["completedTrips"] as? Number)?.toInt() ?: 0,
                totalDistance = (map["totalDistance"] as? Number)?.toDouble() ?: 0.0,
                totalDrivingHours = (map["totalDrivingHours"] as? Number)?.toLong() ?: 0L,
                violationsCount = (map["violationsCount"] as? Number)?.toInt() ?: 0,
                overspeedCount = (map["overspeedCount"] as? Number)?.toInt() ?: 0,
                harshBrakingCount = (map["harshBrakingCount"] as? Number)?.toInt() ?: 0,
                harshAccelCount = (map["harshAccelCount"] as? Number)?.toInt() ?: 0,
                longIdleCount = (map["longIdleCount"] as? Number)?.toInt() ?: 0,
                routeDeviationCount = (map["routeDeviationCount"] as? Number)?.toInt() ?: 0,
                accidentCount = (map["accidentCount"] as? Number)?.toInt() ?: 0,
                fuelEfficiencyBonus = (map["fuelEfficiencyBonus"] as? Number)?.toInt() ?: 0,
                tripCompletionBonus = (map["tripCompletionBonus"] as? Number)?.toInt() ?: 0,
                totalPenalties = (map["totalPenalties"] as? Number)?.toInt() ?: 0,
                averageMileage = (map["averageMileage"] as? Number)?.toDouble() ?: 0.0,
                scoreHistory = (map["scoreHistory"] as? List<Map<String, Any>>) ?: emptyList(),
                lastTripId = map["lastTripId"] as? String ?: "",
                lastUpdated = (map["lastUpdated"] as? Number)?.toLong()
                    ?: System.currentTimeMillis(),
                createdAt = (map["createdAt"] as? Number)?.toLong()
                    ?: System.currentTimeMillis()
            )
        }
    }

    /**
     * Score trend direction for UI indicators.
     */
    enum class ScoreTrend {
        UP,
        DOWN,
        STABLE
    }
}
