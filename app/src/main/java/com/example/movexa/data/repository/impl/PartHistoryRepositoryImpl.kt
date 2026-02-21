package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.PartHistory
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.PartHistoryRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [PartHistoryRepository].
 *
 * Manages the "parts_history" collection with part lifecycle tracking,
 * warranty monitoring, and replacement history.
 */
class PartHistoryRepositoryImpl : BaseFirestoreRepository<PartHistory>(), PartHistoryRepository {

    override val collectionName: String = PartHistory.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): PartHistory = PartHistory.fromMap(map)
    override fun toMap(model: PartHistory): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: PartHistory): String = model.partId

    override fun getIdFieldName(): String = "partId"

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createPartRecord(part: PartHistory): ResultState<String> = create(part)

    override suspend fun createBatchPartRecords(
        parts: List<PartHistory>
    ): ResultState<List<String>> = createBatch(parts)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getPartById(partId: String): ResultState<PartHistory?> = getById(partId)

    override suspend fun getAllParts(): ResultState<List<PartHistory>> = getAll()

    override suspend fun getPartsByVehicle(vehicleId: String): ResultState<List<PartHistory>> =
        query { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override suspend fun getPartsByCompany(companyId: String): ResultState<List<PartHistory>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override suspend fun getPartsByName(
        vehicleId: String,
        partName: String
    ): ResultState<List<PartHistory>> = query { ref ->
        ref.whereEqualTo("vehicleId", vehicleId)
            .whereEqualTo("partName", partName)
            .orderBy("date", Query.Direction.DESCENDING)
    }

    override suspend fun getExpiredParts(
        vehicleId: String,
        currentOdometer: Long
    ): ResultState<List<PartHistory>> = firebaseSafeCall {
        val snapshot = collectionRef
            .whereEqualTo("vehicleId", vehicleId)
            .get()
            .await()
        snapshot.toModelList(::fromMap).filter { it.isExpired(currentOdometer) }
    }

    override suspend fun getPartsNearExpiry(
        vehicleId: String,
        currentOdometer: Long,
        thresholdKm: Long
    ): ResultState<List<PartHistory>> = firebaseSafeCall {
        val snapshot = collectionRef
            .whereEqualTo("vehicleId", vehicleId)
            .get()
            .await()
        snapshot.toModelList(::fromMap).filter { part ->
            val remaining = part.remainingLifeKm(currentOdometer)
            remaining in 1..thresholdKm
        }
    }

    override suspend fun getPartsPaginated(
        companyId: String,
        pageSize: Int,
        lastPartId: String?
    ): ResultState<List<PartHistory>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastPartId != null) {
            val lastDoc = collectionRef.document(lastPartId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getPartCount(companyId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("companyId", companyId) }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun updatePartRecord(part: PartHistory): ResultState<Unit> = update(part)

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deletePartRecord(partId: String): ResultState<Unit> = delete(partId)

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeVehicleParts(vehicleId: String): Flow<ResultState<List<PartHistory>>> =
        observeCollection { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("date", Query.Direction.DESCENDING)
        }
}
