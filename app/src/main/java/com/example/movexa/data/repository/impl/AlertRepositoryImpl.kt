package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.AlertStatus
import com.example.movexa.data.model.enums.AlertType
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.AlertRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [AlertRepository].
 *
 * Manages the "alerts" collection with priority-based alerting,
 * acknowledgement workflows, and resolution tracking.
 */
class AlertRepositoryImpl : BaseFirestoreRepository<Alert>(), AlertRepository {

    override val collectionName: String = Alert.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): Alert = Alert.fromMap(map)
    override fun toMap(model: Alert): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: Alert): String = model.alertId

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createAlert(alert: Alert): ResultState<String> = create(alert)

    override suspend fun createBatchAlerts(alerts: List<Alert>): ResultState<List<String>> =
        createBatch(alerts)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getAlertById(alertId: String): ResultState<Alert?> = getById(alertId)

    override suspend fun getAllAlerts(): ResultState<List<Alert>> = getAll()

    override suspend fun getAlertsByCompany(companyId: String): ResultState<List<Alert>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getActiveAlerts(companyId: String): ResultState<List<Alert>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("status", AlertStatus.ACTIVE.name)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getAlertsByType(
        companyId: String,
        type: AlertType
    ): ResultState<List<Alert>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereEqualTo("type", type.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getAlertsByPriority(
        companyId: String,
        priority: AlertPriority
    ): ResultState<List<Alert>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereEqualTo("priority", priority.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getAlertsByStatus(
        companyId: String,
        status: AlertStatus
    ): ResultState<List<Alert>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereEqualTo("status", status.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getCriticalAlerts(companyId: String): ResultState<List<Alert>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("priority", AlertPriority.CRITICAL.name)
                .whereEqualTo("status", AlertStatus.ACTIVE.name)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getAlertsByVehicle(vehicleId: String): ResultState<List<Alert>> =
        query { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getAlertsByDriver(driverId: String): ResultState<List<Alert>> =
        query { ref ->
            ref.whereEqualTo("driverId", driverId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getAlertsByTrip(tripId: String): ResultState<List<Alert>> =
        query { ref ->
            ref.whereEqualTo("tripId", tripId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getAlertsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<Alert>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThanOrEqualTo("timestamp", endTimestamp)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getAlertsPaginated(
        companyId: String,
        pageSize: Int,
        lastAlertId: String?
    ): ResultState<List<Alert>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastAlertId != null) {
            val lastDoc = collectionRef.document(lastAlertId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getActiveAlertCount(companyId: String): ResultState<Int> =
        count { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("status", AlertStatus.ACTIVE.name)
        }

    override suspend fun getCriticalAlertCount(companyId: String): ResultState<Int> =
        count { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("priority", AlertPriority.CRITICAL.name)
                .whereEqualTo("status", AlertStatus.ACTIVE.name)
        }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun updateAlert(alert: Alert): ResultState<Unit> = update(alert)

    override suspend fun acknowledgeAlert(alertId: String): ResultState<Unit> =
        updateFields(alertId, mapOf("status" to AlertStatus.ACKNOWLEDGED.name))

    override suspend fun resolveAlert(
        alertId: String,
        resolvedBy: String
    ): ResultState<Unit> = updateFields(
        alertId,
        mapOf(
            "status" to AlertStatus.RESOLVED.name,
            "resolvedBy" to resolvedBy,
            "resolvedAt" to System.currentTimeMillis()
        )
    )

    override suspend fun dismissAlert(alertId: String): ResultState<Unit> =
        updateFields(alertId, mapOf("status" to AlertStatus.DISMISSED.name))

    override suspend fun resolveMultipleAlerts(
        alertIds: List<String>,
        resolvedBy: String
    ): ResultState<Unit> = firebaseSafeCall {
        val batch = com.example.movexa.data.remote.FirebaseProvider.firestore.batch()
        val now = System.currentTimeMillis()
        alertIds.forEach { alertId ->
            val docRef = collectionRef.document(alertId)
            batch.update(
                docRef,
                mapOf(
                    "status" to AlertStatus.RESOLVED.name,
                    "resolvedBy" to resolvedBy,
                    "resolvedAt" to now,
                    "updatedAt" to now
                )
            )
        }
        batch.commit().await()
    }

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteAlert(alertId: String): ResultState<Unit> = delete(alertId)

    override suspend fun deleteResolvedAlerts(companyId: String): ResultState<Unit> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", AlertStatus.RESOLVED.name)
                .get()
                .await()
            val ids = snapshot.documents.map { it.id }
            if (ids.isNotEmpty()) {
                // Batch delete in chunks of 500
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

    override fun observeActiveAlerts(companyId: String): Flow<ResultState<List<Alert>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("status", AlertStatus.ACTIVE.name)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override fun observeCriticalAlerts(companyId: String): Flow<ResultState<List<Alert>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("priority", AlertPriority.CRITICAL.name)
                .whereEqualTo("status", AlertStatus.ACTIVE.name)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override fun observeVehicleAlerts(vehicleId: String): Flow<ResultState<List<Alert>>> =
        observeCollection { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .whereEqualTo("status", AlertStatus.ACTIVE.name)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override fun observeDriverAlerts(driverId: String): Flow<ResultState<List<Alert>>> =
        observeCollection { ref ->
            ref.whereEqualTo("driverId", driverId)
                .whereEqualTo("status", AlertStatus.ACTIVE.name)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }
}
