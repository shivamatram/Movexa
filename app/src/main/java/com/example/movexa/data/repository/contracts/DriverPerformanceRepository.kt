package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.ResultState
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for the Driver Performance Scoring module.
 *
 * Manages the `driver_summary` Firestore collection where each
 * document represents a driver's cumulative performance metrics.
 *
 * Document ID = driverId, so there is exactly one summary per driver.
 */
interface DriverPerformanceRepository {

    // ═══════════════════════════════════════════════════════════
    //  CREATE / UPDATE
    // ═══════════════════════════════════════════════════════════

    /**
     * Create or overwrite a driver summary document.
     */
    suspend fun saveSummary(summary: DriverSummary): ResultState<Unit>

    /**
     * Update specific fields on an existing summary.
     */
    suspend fun updateSummaryFields(
        driverId: String,
        fields: Map<String, Any?>
    ): ResultState<Unit>

    // ═══════════════════════════════════════════════════════════
    //  READ
    // ═══════════════════════════════════════════════════════════

    /**
     * Get a single driver's performance summary.
     */
    suspend fun getSummaryByDriver(driverId: String): ResultState<DriverSummary?>

    /**
     * Get all driver summaries for a company, sorted by score descending.
     */
    suspend fun getSummariesByCompany(companyId: String): ResultState<List<DriverSummary>>

    /**
     * Get top-performing drivers (leaderboard) for a company.
     *
     * @param companyId The company to query
     * @param limit Maximum number of results
     */
    suspend fun getTopDrivers(
        companyId: String,
        limit: Int = 10
    ): ResultState<List<DriverSummary>>

    /**
     * Get risky drivers (score below 60) for a company.
     */
    suspend fun getRiskyDrivers(companyId: String): ResultState<List<DriverSummary>>

    /**
     * Get drivers by grade for a company.
     */
    suspend fun getDriversByGrade(
        companyId: String,
        grade: String
    ): ResultState<List<DriverSummary>>

    // ═══════════════════════════════════════════════════════════
    //  REAL-TIME OBSERVATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe real-time updates to a specific driver's summary.
     */
    fun observeDriverSummary(driverId: String): Flow<ResultState<DriverSummary?>>

    /**
     * Observe all driver summaries for a company in real time.
     */
    fun observeCompanySummaries(companyId: String): Flow<ResultState<List<DriverSummary>>>

    // ═══════════════════════════════════════════════════════════
    //  DELETE
    // ═══════════════════════════════════════════════════════════

    /**
     * Delete a driver's performance summary.
     */
    suspend fun deleteSummary(driverId: String): ResultState<Unit>
}
