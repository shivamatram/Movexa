package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.ActivityLogType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for ActivityLog repository operations.
 * Handles audit logging, activity tracking, and system event history.
 */
interface ActivityLogRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createLog(log: ActivityLog): ResultState<String>
    suspend fun createBatchLogs(logs: List<ActivityLog>): ResultState<List<String>>

    /**
     * Convenience method to quickly log an activity.
     */
    suspend fun logActivity(
        type: ActivityLogType,
        message: String,
        userId: String,
        companyId: String,
        referenceId: String? = null,
        referenceType: String? = null
    ): ResultState<String>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getLogById(logId: String): ResultState<ActivityLog?>
    suspend fun getAllLogs(): ResultState<List<ActivityLog>>
    suspend fun getLogsByCompany(companyId: String): ResultState<List<ActivityLog>>
    suspend fun getLogsByUser(userId: String): ResultState<List<ActivityLog>>
    suspend fun getLogsByType(
        companyId: String,
        type: ActivityLogType
    ): ResultState<List<ActivityLog>>
    suspend fun getLogsByReference(
        referenceId: String,
        referenceType: String
    ): ResultState<List<ActivityLog>>
    suspend fun getLogsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<ActivityLog>>
    suspend fun getRecentLogs(
        companyId: String,
        limit: Int = 50
    ): ResultState<List<ActivityLog>>
    suspend fun getLogsPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastLogId: String? = null
    ): ResultState<List<ActivityLog>>
    suspend fun getLogCount(companyId: String): ResultState<Int>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteLog(logId: String): ResultState<Unit>
    suspend fun deleteOldLogs(
        companyId: String,
        olderThanTimestamp: Long
    ): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeRecentLogs(companyId: String): Flow<ResultState<List<ActivityLog>>>
    fun observeUserLogs(userId: String): Flow<ResultState<List<ActivityLog>>>
}
