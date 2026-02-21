package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.ActivityLogType
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.ActivityLogRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [ActivityLogRepository].
 *
 * Manages the "activity_logs" collection for audit logging,
 * system event tracking, and activity history.
 */
class ActivityLogRepositoryImpl : BaseFirestoreRepository<ActivityLog>(), ActivityLogRepository {

    override val collectionName: String = ActivityLog.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): ActivityLog = ActivityLog.fromMap(map)
    override fun toMap(model: ActivityLog): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: ActivityLog): String = model.logId

    override fun getIdFieldName(): String = "logId"

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createLog(log: ActivityLog): ResultState<String> = create(log)

    override suspend fun createBatchLogs(logs: List<ActivityLog>): ResultState<List<String>> =
        createBatch(logs)

    override suspend fun logActivity(
        type: ActivityLogType,
        message: String,
        userId: String,
        companyId: String,
        referenceId: String?,
        referenceType: String?
    ): ResultState<String> = create(
        ActivityLog(
            type = type,
            message = message,
            userId = userId,
            companyId = companyId,
            referenceId = referenceId ?: "",
            referenceType = referenceType ?: "",
            timestamp = System.currentTimeMillis()
        )
    )

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getLogById(logId: String): ResultState<ActivityLog?> = getById(logId)

    override suspend fun getAllLogs(): ResultState<List<ActivityLog>> = getAll()

    override suspend fun getLogsByCompany(companyId: String): ResultState<List<ActivityLog>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getLogsByUser(userId: String): ResultState<List<ActivityLog>> =
        query { ref ->
            ref.whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getLogsByType(
        companyId: String,
        type: ActivityLogType
    ): ResultState<List<ActivityLog>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereEqualTo("type", type.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getLogsByReference(
        referenceId: String,
        referenceType: String
    ): ResultState<List<ActivityLog>> = query { ref ->
        ref.whereEqualTo("referenceId", referenceId)
            .whereEqualTo("referenceType", referenceType)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getLogsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<ActivityLog>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThanOrEqualTo("timestamp", endTimestamp)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getRecentLogs(
        companyId: String,
        limit: Int
    ): ResultState<List<ActivityLog>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
    }

    override suspend fun getLogsPaginated(
        companyId: String,
        pageSize: Int,
        lastLogId: String?
    ): ResultState<List<ActivityLog>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastLogId != null) {
            val lastDoc = collectionRef.document(lastLogId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getLogCount(companyId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("companyId", companyId) }

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteLog(logId: String): ResultState<Unit> = delete(logId)

    override suspend fun deleteOldLogs(
        companyId: String,
        olderThanTimestamp: Long
    ): ResultState<Unit> = firebaseSafeCall {
        val snapshot = collectionRef
            .whereEqualTo("companyId", companyId)
            .whereLessThan("timestamp", olderThanTimestamp)
            .get()
            .await()
        val ids = snapshot.documents.map { it.id }
        if (ids.isNotEmpty()) {
            ids.chunked(500).forEach { chunk ->
                deleteMultiple(chunk).let { result ->
                    if (result is ResultState.Error) throw Exception(result.message)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeRecentLogs(companyId: String): Flow<ResultState<List<ActivityLog>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
        }

    override fun observeUserLogs(userId: String): Flow<ResultState<List<ActivityLog>>> =
        observeCollection { ref ->
            ref.whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
        }
}
