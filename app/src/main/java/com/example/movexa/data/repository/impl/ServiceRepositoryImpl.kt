package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.data.model.enums.ServiceType
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.ServiceRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [ServiceRepository].
 *
 * Manages the "services" collection with maintenance scheduling,
 * service tracking, and overdue detection.
 */
class ServiceRepositoryImpl : BaseFirestoreRepository<ServiceRecord>(), ServiceRepository {

    override val collectionName: String = ServiceRecord.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): ServiceRecord = ServiceRecord.fromMap(map)
    override fun toMap(model: ServiceRecord): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: ServiceRecord): String = model.serviceId

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createServiceRecord(service: ServiceRecord): ResultState<String> =
        create(service)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getServiceById(serviceId: String): ResultState<ServiceRecord?> =
        getById(serviceId)

    override suspend fun getAllServices(): ResultState<List<ServiceRecord>> = getAll()

    override suspend fun getServicesByVehicle(vehicleId: String): ResultState<List<ServiceRecord>> =
        query { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override suspend fun getServicesByCompany(companyId: String): ResultState<List<ServiceRecord>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override suspend fun getServicesByType(
        companyId: String,
        type: ServiceType
    ): ResultState<List<ServiceRecord>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereEqualTo("serviceType", type.name)
    }

    override suspend fun getPendingServices(companyId: String): ResultState<List<ServiceRecord>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("completed", false)
        }

    override suspend fun getCompletedServices(companyId: String): ResultState<List<ServiceRecord>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("completed", true)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override suspend fun getOverdueServices(
        companyId: String,
        currentOdometerMap: Map<String, Long>
    ): ResultState<List<ServiceRecord>> = firebaseSafeCall {
        val snapshot = collectionRef
            .whereEqualTo("companyId", companyId)
            .whereEqualTo("completed", true)
            .get()
            .await()
        snapshot.toModelList(::fromMap).filter { service ->
            val currentOdometer = currentOdometerMap[service.vehicleId] ?: return@filter false
            service.isOverdue(currentOdometer)
        }
    }

    override suspend fun getLastServiceByType(
        vehicleId: String,
        type: ServiceType
    ): ResultState<ServiceRecord?> = firebaseSafeCall {
        val snapshot = collectionRef
            .whereEqualTo("vehicleId", vehicleId)
            .whereEqualTo("serviceType", type.name)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        snapshot.toModelList(::fromMap).firstOrNull()
    }

    override suspend fun getServicesByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<ServiceRecord>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("date", startTimestamp)
            .whereLessThanOrEqualTo("date", endTimestamp)
            .orderBy("date", Query.Direction.DESCENDING)
    }

    override suspend fun getServicesPaginated(
        companyId: String,
        pageSize: Int,
        lastServiceId: String?
    ): ResultState<List<ServiceRecord>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastServiceId != null) {
            val lastDoc = collectionRef.document(lastServiceId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getServiceCount(companyId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("companyId", companyId) }

    override suspend fun getTotalServiceCost(
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

    override suspend fun updateServiceRecord(service: ServiceRecord): ResultState<Unit> =
        update(service.copy(updatedAt = System.currentTimeMillis()))

    override suspend fun markServiceCompleted(serviceId: String): ResultState<Unit> =
        updateFields(serviceId, mapOf("completed" to true))

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteServiceRecord(serviceId: String): ResultState<Unit> =
        delete(serviceId)

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeVehicleServices(vehicleId: String): Flow<ResultState<List<ServiceRecord>>> =
        observeCollection { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("date", Query.Direction.DESCENDING)
        }

    override fun observeCompanyServices(companyId: String): Flow<ResultState<List<ServiceRecord>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("date", Query.Direction.DESCENDING)
        }
}
