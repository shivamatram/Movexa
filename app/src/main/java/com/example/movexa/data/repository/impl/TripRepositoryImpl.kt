package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.TripRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [TripRepository].
 *
 * Manages the "trips" collection with lifecycle management,
 * status transitions, and route-based queries.
 */
class TripRepositoryImpl : BaseFirestoreRepository<Trip>(), TripRepository {

    override val collectionName: String = Trip.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): Trip = Trip.fromMap(map)
    override fun toMap(model: Trip): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: Trip): String = model.tripId

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createTrip(trip: Trip): ResultState<String> = create(trip)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getTripById(tripId: String): ResultState<Trip?> = getById(tripId)

    override suspend fun getAllTrips(): ResultState<List<Trip>> = getAll()

    override suspend fun getTripsByCompany(companyId: String): ResultState<List<Trip>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }

    override suspend fun getTripsByDriver(driverId: String): ResultState<List<Trip>> =
        query { ref ->
            ref.whereEqualTo("driverId", driverId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }

    override suspend fun getTripsByVehicle(vehicleId: String): ResultState<List<Trip>> =
        query { ref ->
            ref.whereEqualTo("vehicleId", vehicleId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }

    override suspend fun getTripsByStatus(
        companyId: String,
        status: TripStatus
    ): ResultState<List<Trip>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereEqualTo("status", status.name)
    }

    override suspend fun getActiveTrips(companyId: String): ResultState<List<Trip>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereIn("status", TripStatus.activeStatuses().map { it.name })
        }

    override suspend fun getCompletedTrips(companyId: String): ResultState<List<Trip>> =
        query { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereEqualTo("status", TripStatus.COMPLETED.name)
                .orderBy("endTime", Query.Direction.DESCENDING)
        }

    override suspend fun getTripsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<Trip>> = query { ref ->
        ref.whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
            .whereLessThanOrEqualTo("createdAt", endTimestamp)
            .orderBy("createdAt", Query.Direction.DESCENDING)
    }

    override suspend fun getDriverActiveTrip(driverId: String): ResultState<Trip?> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("driverId", driverId)
                .whereIn("status", TripStatus.activeStatuses().map { it.name })
                .limit(1)
                .get()
                .await()
            snapshot.toModelList(::fromMap).firstOrNull()
        }

    override suspend fun getVehicleActiveTrip(vehicleId: String): ResultState<Trip?> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("vehicleId", vehicleId)
                .whereIn("status", TripStatus.activeStatuses().map { it.name })
                .limit(1)
                .get()
                .await()
            snapshot.toModelList(::fromMap).firstOrNull()
        }

    override suspend fun getTripsPaginated(
        companyId: String,
        pageSize: Int,
        lastTripId: String?
    ): ResultState<List<Trip>> = firebaseSafeCall {
        var q = collectionRef
            .whereEqualTo("companyId", companyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastTripId != null) {
            val lastDoc = collectionRef.document(lastTripId).get().await()
            if (lastDoc.exists()) {
                q = q.startAfter(lastDoc)
            }
        }

        q.get().await().toModelList(::fromMap)
    }

    override suspend fun getTripCount(companyId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("companyId", companyId) }

    override suspend fun getActiveTripCount(companyId: String): ResultState<Int> =
        count { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereIn("status", TripStatus.activeStatuses().map { it.name })
        }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun updateTrip(trip: Trip): ResultState<Unit> =
        update(trip.copy(updatedAt = System.currentTimeMillis()))

    override suspend fun updateTripStatus(
        tripId: String,
        status: TripStatus
    ): ResultState<Unit> = updateFields(
        tripId,
        mapOf("status" to status.name)
    )

    override suspend fun assignDriver(
        tripId: String,
        driverId: String
    ): ResultState<Unit> = updateFields(
        tripId,
        mapOf(
            "driverId" to driverId,
            "status" to TripStatus.ASSIGNED.name
        )
    )

    override suspend fun startTrip(tripId: String): ResultState<Unit> = updateFields(
        tripId,
        mapOf(
            "status" to TripStatus.STARTED.name,
            "startTime" to System.currentTimeMillis()
        )
    )

    override suspend fun completeTrip(
        tripId: String,
        distance: Double,
        duration: Long
    ): ResultState<Unit> = updateFields(
        tripId,
        mapOf(
            "status" to TripStatus.COMPLETED.name,
            "endTime" to System.currentTimeMillis(),
            "distance" to distance,
            "duration" to duration
        )
    )

    override suspend fun cancelTrip(
        tripId: String,
        reason: String?
    ): ResultState<Unit> = updateFields(
        tripId,
        mapOf(
            "status" to TripStatus.CANCELLED.name,
            "endTime" to System.currentTimeMillis(),
            "notes" to (reason ?: "Cancelled")
        )
    )

    override suspend fun updateTrackingId(
        tripId: String,
        trackingId: String
    ): ResultState<Unit> = updateFields(tripId, mapOf("trackingId" to trackingId))

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteTrip(tripId: String): ResultState<Unit> = delete(tripId)

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeTrip(tripId: String): Flow<ResultState<Trip?>> =
        observeDocument(tripId)

    override fun observeActiveTrips(companyId: String): Flow<ResultState<List<Trip>>> =
        observeCollection { ref ->
            ref.whereEqualTo("companyId", companyId)
                .whereIn("status", TripStatus.activeStatuses().map { it.name })
        }

    override fun observeDriverTrips(driverId: String): Flow<ResultState<List<Trip>>> =
        observeCollection { ref ->
            ref.whereEqualTo("driverId", driverId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }
}
