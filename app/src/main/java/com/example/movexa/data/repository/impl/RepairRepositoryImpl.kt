package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.Repair
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.RepairRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [RepairRepository].
 *
 * Manages the "repairs" collection with repair logging,
 * warranty tracking, and cost analysis.
 */
class RepairRepositoryImpl : BaseFirestoreRepository<Repair>(), RepairRepository {

    override val collectionName: String = Repair.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): Repair = Repair.fromMap(map)
    override fun toMap(model: Repair): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: Repair): String = model.repairId

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createRepair(repair: Repair): ResultState<String> = create(repair)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getRepairById(repairId: String): ResultState<Repair?> = getById(repairId)

    override suspend fun getAllRepairs(): ResultState<List<Repair>> = getAll()

    override suspend fun getRepairsByVehicle(vehicleId: String): ResultState<List<Repair>> =
        query { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override suspend fun getRepairsByCompany(companyId: String): ResultState<List<Repair>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override suspend fun getRepairsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<Repair>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("date", startTimestamp)
            .whereLessThanOrEqualTo("date", endTimestamp)
            .orderBy("date", Query.Direction.DESCENDING)
    }

    override suspend fun getRepairsUnderWarranty(vehicleId: String): ResultState<List<Repair>> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("vehicleId", vehicleId)
                .get()
                .await()
            snapshot.toModelList(::fromMap).filter { it.isUnderWarranty() }
        }

    override suspend fun getRepairsPaginated(
        companyId: String,
        pageSize: Int,
        lastRepairId: String?
    ): ResultState<List<Repair>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastRepairId != null) {
            val lastDoc = collectionRef.document(lastRepairId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getRepairCount(companyId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("companyId", companyId) }

    override suspend fun getTotalRepairCost(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<Double> = firebaseSafeCall {
        val snapshot = collectionRef
            .whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("date", startTimestamp)
            .whereLessThanOrEqualTo("date", endTimestamp)
            .get()
            .await()
        snapshot.toModelList(::fromMap).sumOf { it.cost }
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun updateRepair(repair: Repair): ResultState<Unit> =
        update(repair.copy(updatedAt = System.currentTimeMillis()))

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteRepair(repairId: String): ResultState<Unit> = delete(repairId)

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeVehicleRepairs(vehicleId: String): Flow<ResultState<List<Repair>>> =
        observeCollection { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override fun observeCompanyRepairs(companyId: String): Flow<ResultState<List<Repair>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("date", Query.Direction.DESCENDING)
        }
}
