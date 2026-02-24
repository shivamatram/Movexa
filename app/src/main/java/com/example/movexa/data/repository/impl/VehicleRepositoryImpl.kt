package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.model.enums.VehicleType
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.VehicleRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [VehicleRepository].
 *
 * Manages the "vehicles" collection with full CRUD, status management,
 * fleet queries, and real-time observation.
 */
class VehicleRepositoryImpl : BaseFirestoreRepository<Vehicle>(), VehicleRepository {

    override val collectionName: String = Vehicle.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): Vehicle = Vehicle.fromMap(map)
    override fun toMap(model: Vehicle): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: Vehicle): String = model.vehicleId

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createVehicle(vehicle: Vehicle): ResultState<String> =
        create(vehicle)

    override suspend fun createVehicleWithId(
        vehicleId: String,
        vehicle: Vehicle
    ): ResultState<Unit> = createWithId(vehicleId, vehicle)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getVehicleById(vehicleId: String): ResultState<Vehicle?> =
        getById(vehicleId)

    override suspend fun getAllVehicles(): ResultState<List<Vehicle>> = getAll()

    override suspend fun getVehiclesByCompany(companyId: String): ResultState<List<Vehicle>> =
        getByField("companyId", companyId)

    override suspend fun getVehiclesByStatus(status: VehicleStatus): ResultState<List<Vehicle>> =
        getByField("status", status.name)

    override suspend fun getVehiclesByType(type: VehicleType): ResultState<List<Vehicle>> =
        getByField("type", type.name)

    override suspend fun getVehiclesByDriver(driverId: String): ResultState<List<Vehicle>> =
        getByField("assignedDriverId", driverId)

    override suspend fun getVehicleByNumber(vehicleNumber: String): ResultState<Vehicle?> =
        getFirstByField("number", vehicleNumber)

    override suspend fun getAvailableVehicles(companyId: String): ResultState<List<Vehicle>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("status", VehicleStatus.AVAILABLE.name)
        }

    override suspend fun getVehiclesNeedingService(companyId: String): ResultState<List<Vehicle>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("documentsValid", false)
        }

    override suspend fun getVehiclesPaginated(
        companyId: String,
        pageSize: Int,
        lastVehicleId: String?
    ): ResultState<List<Vehicle>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastVehicleId != null) {
            val lastDoc = collectionRef.document(lastVehicleId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getVehicleCount(companyId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("companyId", companyId) }

    override suspend fun vehicleNumberExists(vehicleNumber: String): ResultState<Boolean> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("number", vehicleNumber)
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun updateVehicle(vehicle: Vehicle): ResultState<Unit> =
        update(vehicle.copy(updatedAt = System.currentTimeMillis()))

    override suspend fun updateVehicleStatus(
        vehicleId: String,
        status: VehicleStatus
    ): ResultState<Unit> = updateFields(vehicleId, mapOf("status" to status.name))

    override suspend fun assignDriverToVehicle(
        vehicleId: String,
        driverId: String
    ): ResultState<Unit> = updateFields(vehicleId, mapOf("assignedDriverId" to driverId))

    override suspend fun unassignDriverFromVehicle(vehicleId: String): ResultState<Unit> =
        updateFields(vehicleId, mapOf("assignedDriverId" to null))

    override suspend fun updateOdometer(
        vehicleId: String,
        odometer: Long
    ): ResultState<Unit> = updateFields(vehicleId, mapOf("lastOdometer" to odometer))

    override suspend fun updateDocumentValidity(
        vehicleId: String,
        valid: Boolean
    ): ResultState<Unit> = updateFields(vehicleId, mapOf("documentsValid" to valid))

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteVehicle(vehicleId: String): ResultState<Unit> =
        delete(vehicleId)

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeVehicle(vehicleId: String): Flow<ResultState<Vehicle?>> =
        observeDocument(vehicleId)

    override fun observeAllVehicles(): Flow<ResultState<List<Vehicle>>> =
        observeCollection { ref ->
            ref.orderBy("createdAt", Query.Direction.DESCENDING)
        }

    override fun observeFleet(companyId: String): Flow<ResultState<List<Vehicle>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }

    override fun observeAvailableVehicles(companyId: String): Flow<ResultState<List<Vehicle>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("status", VehicleStatus.AVAILABLE.name)
        }

}
