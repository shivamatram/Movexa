package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.FuelLog
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.FuelLogRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [FuelLogRepository].
 *
 * Manages the "fuel_logs" collection with cost tracking,
 * mileage calculation, and fuel analytics.
 */
class FuelLogRepositoryImpl : BaseFirestoreRepository<FuelLog>(), FuelLogRepository {

    override val collectionName: String = FuelLog.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): FuelLog = FuelLog.fromMap(map)
    override fun toMap(model: FuelLog): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: FuelLog): String = model.fuelId

    override fun getIdFieldName(): String = "fuelId"

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createFuelLog(fuelLog: FuelLog): ResultState<String> = create(fuelLog)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getFuelLogById(fuelId: String): ResultState<FuelLog?> = getById(fuelId)

    override suspend fun getAllFuelLogs(): ResultState<List<FuelLog>> = getAll()

    override suspend fun getFuelLogsByVehicle(vehicleId: String): ResultState<List<FuelLog>> =
        query { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getFuelLogsByDriver(driverId: String): ResultState<List<FuelLog>> =
        query { ref ->
            ref.whereEqualTo("driverId", driverId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getFuelLogsByCompany(companyId: String): ResultState<List<FuelLog>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override suspend fun getFuelLogsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<FuelLog>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThanOrEqualTo("timestamp", endTimestamp)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getVehicleFuelLogsByDateRange(
        vehicleId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<FuelLog>> = query { ref ->
        ref.whereEqualTo("vehicleId", vehicleId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThanOrEqualTo("timestamp", endTimestamp)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    override suspend fun getLatestFuelLog(vehicleId: String): ResultState<FuelLog?> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("vehicleId", vehicleId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            snapshot.toModelList(::fromMap).firstOrNull()
        }

    override suspend fun getFuelLogsPaginated(
        companyId: String,
        pageSize: Int,
        lastFuelId: String?
    ): ResultState<List<FuelLog>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastFuelId != null) {
            val lastDoc = collectionRef.document(lastFuelId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getFuelLogCount(companyId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("companyId", companyId) }

    override suspend fun getTotalFuelCost(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<Double> = firebaseSafeCall {
        val snapshot = collectionRef
            .whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThanOrEqualTo("timestamp", endTimestamp)
            .get()
            .await()
        snapshot.toModelList(::fromMap).sumOf { it.cost }
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun updateFuelLog(fuelLog: FuelLog): ResultState<Unit> =
        update(fuelLog)

    override suspend fun verifyFuelLog(
        fuelId: String,
        verifiedBy: String
    ): ResultState<Unit> = updateFields(fuelId, mapOf("verifiedBy" to verifiedBy))

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteFuelLog(fuelId: String): ResultState<Unit> = delete(fuelId)

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeVehicleFuelLogs(vehicleId: String): Flow<ResultState<List<FuelLog>>> =
        observeCollection { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }

    override fun observeCompanyFuelLogs(companyId: String): Flow<ResultState<List<FuelLog>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }
}
