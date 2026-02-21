package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.VerificationStatus
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.DriverRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [DriverRepository].
 *
 * Manages the "drivers" collection with profile management,
 * verification workflows, and fleet assignment.
 */
class DriverRepositoryImpl : BaseFirestoreRepository<Driver>(), DriverRepository {

    override val collectionName: String = Driver.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): Driver = Driver.fromMap(map)
    override fun toMap(model: Driver): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: Driver): String = model.driverId

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createDriver(driver: Driver): ResultState<String> =
        create(driver)

    override suspend fun createDriverWithId(
        driverId: String,
        driver: Driver
    ): ResultState<Unit> = createWithId(driverId, driver)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getDriverById(driverId: String): ResultState<Driver?> =
        getById(driverId)

    override suspend fun getDriverByUserId(userId: String): ResultState<Driver?> =
        getFirstByField("userId", userId)

    override suspend fun getAllDrivers(): ResultState<List<Driver>> = getAll()

    override suspend fun getDriversByCompany(companyId: String): ResultState<List<Driver>> =
        getByField("companyId", companyId)

    override suspend fun getDriversByVerificationStatus(
        companyId: String,
        status: VerificationStatus
    ): ResultState<List<Driver>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereEqualTo("verificationStatus", status.name)
    }

    override suspend fun getActiveDrivers(companyId: String): ResultState<List<Driver>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("blocked", false)
                .whereEqualTo("verificationStatus", VerificationStatus.APPROVED.name)
        }

    override suspend fun getBlockedDrivers(companyId: String): ResultState<List<Driver>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("blocked", true)
        }

    override suspend fun getUnassignedDrivers(companyId: String): ResultState<List<Driver>> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("blocked", false)
                .whereEqualTo("verificationStatus", VerificationStatus.APPROVED.name)
                .get()
                .await()
            snapshot.toModelList(::fromMap).filter { it.assignedVehicleId.isNullOrBlank() }
        }

    override suspend fun getDriverByLicense(licenseNumber: String): ResultState<Driver?> =
        getFirstByField("licenseNumber", licenseNumber)

    override suspend fun getDriversPaginated(
        companyId: String,
        pageSize: Int,
        lastDriverId: String?
    ): ResultState<List<Driver>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastDriverId != null) {
            val lastDoc = collectionRef.document(lastDriverId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getDriverCount(companyId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("companyId", companyId) }

    override suspend fun licenseExists(licenseNumber: String): ResultState<Boolean> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("licenseNumber", licenseNumber)
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun updateDriver(driver: Driver): ResultState<Unit> =
        update(driver.copy(updatedAt = System.currentTimeMillis()))

    override suspend fun updateVerificationStatus(
        driverId: String,
        status: VerificationStatus
    ): ResultState<Unit> = updateFields(driverId, mapOf("verificationStatus" to status.name))

    override suspend fun blockDriver(driverId: String): ResultState<Unit> =
        updateFields(driverId, mapOf("blocked" to true))

    override suspend fun unblockDriver(driverId: String): ResultState<Unit> =
        updateFields(driverId, mapOf("blocked" to false))

    override suspend fun assignVehicleToDriver(
        driverId: String,
        vehicleId: String
    ): ResultState<Unit> = updateFields(driverId, mapOf("assignedVehicleId" to vehicleId))

    override suspend fun unassignVehicleFromDriver(driverId: String): ResultState<Unit> =
        updateFields(driverId, mapOf("assignedVehicleId" to null))

    override suspend fun updateRating(
        driverId: String,
        newRating: Double
    ): ResultState<Unit> = updateFields(driverId, mapOf("rating" to newRating))

    override suspend fun incrementTripCount(driverId: String): ResultState<Unit> =
        firebaseSafeCall {
            collectionRef.document(driverId)
                .update("totalTrips", FieldValue.increment(1))
                .await()
        }

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteDriver(driverId: String): ResultState<Unit> =
        delete(driverId)

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeDriver(driverId: String): Flow<ResultState<Driver?>> =
        observeDocument(driverId)

    override fun observeCompanyDrivers(companyId: String): Flow<ResultState<List<Driver>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }

    override fun observeDriverByUserId(userId: String): Flow<ResultState<Driver?>> =
        observeCollection { ref ->
            ref.whereEqualTo("userId", userId).limit(1)
        }.map { result ->
            when (result) {
                is ResultState.Success -> ResultState.Success(result.data.firstOrNull())
                is ResultState.Error -> ResultState.Error(result.message, result.exception)
                is ResultState.Loading -> ResultState.Loading
                is ResultState.Idle -> ResultState.Idle
            }
        }
}
