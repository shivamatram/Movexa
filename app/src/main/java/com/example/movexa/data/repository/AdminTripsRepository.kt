package com.example.movexa.data.repository

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.TripEvent
import com.example.movexa.data.model.enums.TripEventType
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.remote.FirebaseProvider
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.contracts.TripEventRepository
import com.example.movexa.data.repository.contracts.TripRepository
import com.example.movexa.data.repository.contracts.VehicleRepository
import com.example.movexa.data.repository.impl.TripEventRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Admin-specific trip repository providing privileged query access.
 *
 * Responsibilities:
 * - Company-wide trip queries with server-side filtering
 * - Paginated queries for completed/cancelled trips
 * - Real-time observation of ongoing trips
 * - Admin override actions (cancel, force-complete, reassign, flag)
 * - Firestore composite query support for multi-field filtering
 *
 * All queries are scoped to companyId to prevent cross-company data leakage.
 */
class AdminTripsRepository : BaseRepository() {

    private val tripRepository: TripRepository = TripRepositoryImpl()
    private val tripEventRepository: TripEventRepository = TripEventRepositoryImpl()
    private val vehicleRepository: VehicleRepository = VehicleRepositoryImpl()

    private val tripsRef = FirebaseProvider.collection("trips")
    private val eventsRef = FirebaseProvider.collection("trip_events")

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME OBSERVATION (Ongoing Trips)
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe all ongoing trips for a company in real-time.
     * Ongoing = CREATED, ASSIGNED, ACCEPTED, STARTED
     */
    fun observeOngoingTrips(companyId: String): Flow<ResultState<List<Trip>>> = callbackFlow {
        val listener = tripsRef
            .whereEqualTo("companyId", companyId)
            .whereIn("status", TripStatus.activeStatuses().map { it.name })
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ResultState.Error(error.message ?: "Failed to observe trips", error))
                    return@addSnapshotListener
                }
                val trips = snapshot?.toModelList(Trip.Companion::fromMap) ?: emptyList()
                trySend(ResultState.Success(trips))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observe all trips for a company (all statuses) in real-time.
     */
    fun observeAllTrips(companyId: String): Flow<ResultState<List<Trip>>> = callbackFlow {
        val listener = tripsRef
            .whereEqualTo("companyId", companyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ResultState.Error(error.message ?: "Failed to observe trips", error))
                    return@addSnapshotListener
                }
                val trips = snapshot?.toModelList(Trip.Companion::fromMap) ?: emptyList()
                trySend(ResultState.Success(trips))
            }
        awaitClose { listener.remove() }
    }

    // ═══════════════════════════════════════════════════════════
    // PAGINATED QUERIES (Completed / Cancelled)
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch paginated completed trips.
     *
     * @param companyId Company scope
     * @param pageSize Number of trips per page
     * @param lastDocument Cursor for pagination (null for first page)
     * @return Pair of trip list and last document snapshot for next page
     */
    suspend fun getCompletedTripsPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastDocument: DocumentSnapshot? = null
    ): ResultState<Pair<List<Trip>, DocumentSnapshot?>> = firebaseSafeCall {
        var query = tripsRef
            .whereEqualTo("companyId", companyId)
            .whereEqualTo("status", TripStatus.COMPLETED.name)
            .orderBy("endTime", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.get().await()
        val trips = snapshot.toModelList(Trip.Companion::fromMap)
        val last = snapshot.documents.lastOrNull()
        Pair(trips, last)
    }

    /**
     * Fetch paginated cancelled trips.
     */
    suspend fun getCancelledTripsPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastDocument: DocumentSnapshot? = null
    ): ResultState<Pair<List<Trip>, DocumentSnapshot?>> = firebaseSafeCall {
        var query = tripsRef
            .whereEqualTo("companyId", companyId)
            .whereEqualTo("status", TripStatus.CANCELLED.name)
            .orderBy("endTime", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.get().await()
        val trips = snapshot.toModelList(Trip.Companion::fromMap)
        val last = snapshot.documents.lastOrNull()
        Pair(trips, last)
    }

    // ═══════════════════════════════════════════════════════════
    // FILTERED QUERIES
    // ═══════════════════════════════════════════════════════════

    /**
     * Query trips with server-side filters.
     * Combines companyId + status + date range filters.
     */
    suspend fun getFilteredTrips(
        companyId: String,
        status: TripStatus? = null,
        driverId: String? = null,
        vehicleId: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        pageSize: Int = 50,
        lastDocument: DocumentSnapshot? = null
    ): ResultState<Pair<List<Trip>, DocumentSnapshot?>> = firebaseSafeCall {
        var query: Query = tripsRef.whereEqualTo("companyId", companyId)

        // Apply status filter
        if (status != null) {
            query = query.whereEqualTo("status", status.name)
        }

        // Apply driver filter
        if (!driverId.isNullOrBlank()) {
            query = query.whereEqualTo("driverId", driverId)
        }

        // Apply vehicle filter
        if (!vehicleId.isNullOrBlank()) {
            query = query.whereEqualTo("vehicleId", vehicleId)
        }

        // Apply date range filter
        if (startDate != null) {
            query = query.whereGreaterThanOrEqualTo("createdAt", startDate)
        }
        if (endDate != null) {
            query = query.whereLessThanOrEqualTo("createdAt", endDate)
        }

        // Ordering and pagination
        query = query.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.get().await()
        val trips = snapshot.toModelList(Trip.Companion::fromMap)
        val last = snapshot.documents.lastOrNull()
        Pair(trips, last)
    }

    /**
     * Get trips by date range for a company.
     */
    suspend fun getTripsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<Trip>> = firebaseSafeCall {
        val snapshot = tripsRef
            .whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
            .whereLessThanOrEqualTo("createdAt", endTimestamp)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        snapshot.toModelList(Trip.Companion::fromMap)
    }

    // ═══════════════════════════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════════════════════════

    /**
     * Search trips by tracking ID (prefix match).
     * Uses Firestore range query for server-side prefix search.
     */
    suspend fun searchByTrackingId(
        companyId: String,
        trackingIdPrefix: String
    ): ResultState<List<Trip>> = firebaseSafeCall {
        val endPrefix = trackingIdPrefix + "\uf8ff"
        val snapshot = tripsRef
            .whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("trackingId", trackingIdPrefix.uppercase())
            .whereLessThanOrEqualTo("trackingId", endPrefix.uppercase())
            .limit(20)
            .get()
            .await()
        snapshot.toModelList(Trip.Companion::fromMap)
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN OVERRIDE ACTIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Admin force-cancel a trip.
     * - Updates trip status to CANCELLED
     * - Returns vehicle to AVAILABLE if assigned
     * - Creates CANCELLED trip event
     */
    suspend fun adminCancelTrip(
        tripId: String,
        adminId: String,
        reason: String
    ): ResultState<Unit> = firebaseSafeCall {
        val now = System.currentTimeMillis()

        // Get current trip state
        val tripResult = tripRepository.getTripById(tripId)
        val trip = (tripResult as? ResultState.Success)?.data
            ?: throw IllegalStateException("Trip not found")

        if (trip.status.isTerminal) {
            throw IllegalStateException("Cannot cancel a ${trip.status.displayName} trip")
        }

        // Cancel the trip
        tripRepository.cancelTrip(tripId, "Admin override: $reason")

        // Return vehicle if assigned
        if (trip.vehicleId.isNotBlank()) {
            vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
        }

        // Log event
        tripEventRepository.createEvent(
            TripEvent(
                tripId = tripId,
                type = TripEventType.CANCELLED,
                description = "Trip cancelled by admin: $reason",
                createdBy = adminId,
                timestamp = now
            )
        )
    }

    /**
     * Admin force-complete a trip.
     * - Updates trip status to COMPLETED with current timestamp
     * - Returns vehicle to AVAILABLE
     * - Creates COMPLETED trip event
     */
    suspend fun adminForceCompleteTrip(
        tripId: String,
        adminId: String,
        reason: String
    ): ResultState<Unit> = firebaseSafeCall {
        val now = System.currentTimeMillis()

        val tripResult = tripRepository.getTripById(tripId)
        val trip = (tripResult as? ResultState.Success)?.data
            ?: throw IllegalStateException("Trip not found")

        if (trip.status.isTerminal) {
            throw IllegalStateException("Trip is already ${trip.status.displayName}")
        }

        val distance = if (trip.distance > 0) trip.distance else trip.estimatedDistance
        val duration = if (trip.startTime > 0) now - trip.startTime else 0L

        tripRepository.completeTrip(tripId, distance, duration)

        // Return vehicle
        if (trip.vehicleId.isNotBlank()) {
            vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
        }

        // Log event
        tripEventRepository.createEvent(
            TripEvent(
                tripId = tripId,
                type = TripEventType.COMPLETED,
                description = "Trip force-completed by admin: $reason",
                createdBy = adminId,
                timestamp = now
            )
        )
    }

    /**
     * Admin reassign driver (emergency override).
     * - Updates trip vehicleId/driverId
     * - Returns old vehicle to AVAILABLE, sets new vehicle to ON_TRIP
     * - Creates DRIVER_ASSIGNED event
     */
    suspend fun adminReassignDriver(
        tripId: String,
        newVehicleId: String,
        newDriverId: String,
        adminId: String,
        reason: String
    ): ResultState<Unit> = firebaseSafeCall {
        val now = System.currentTimeMillis()

        val tripResult = tripRepository.getTripById(tripId)
        val trip = (tripResult as? ResultState.Success)?.data
            ?: throw IllegalStateException("Trip not found")

        if (trip.status.isTerminal) {
            throw IllegalStateException("Cannot reassign a ${trip.status.displayName} trip")
        }

        // Return old vehicle if assigned
        if (trip.vehicleId.isNotBlank()) {
            vehicleRepository.updateVehicleStatus(trip.vehicleId, VehicleStatus.AVAILABLE)
        }

        // Update trip with new assignment
        val updatedTrip = trip.copy(
            vehicleId = newVehicleId,
            driverId = newDriverId,
            status = TripStatus.ASSIGNED,
            assignedBy = adminId,
            updatedAt = now,
            metadata = trip.metadata + mapOf(
                "adminReassigned" to true,
                "reassignReason" to reason,
                "previousVehicleId" to trip.vehicleId,
                "previousDriverId" to trip.driverId
            )
        )
        tripRepository.updateTrip(updatedTrip)

        // Mark new vehicle as ON_TRIP
        vehicleRepository.updateVehicleStatus(newVehicleId, VehicleStatus.ON_TRIP)

        // Log event
        tripEventRepository.createEvent(
            TripEvent(
                tripId = tripId,
                type = TripEventType.DRIVER_ASSIGNED,
                description = "Driver reassigned by admin (override): $reason",
                createdBy = adminId,
                timestamp = now,
                metadata = mapOf(
                    "newVehicleId" to newVehicleId,
                    "newDriverId" to newDriverId,
                    "previousVehicleId" to trip.vehicleId,
                    "previousDriverId" to trip.driverId
                )
            )
        )
    }

    /**
     * Flag a trip for audit review.
     * Updates trip metadata with audit flag and creates an audit event.
     */
    suspend fun flagTripForAudit(
        tripId: String,
        adminId: String,
        reason: String
    ): ResultState<Unit> = firebaseSafeCall {
        val now = System.currentTimeMillis()

        // Update trip metadata
        tripsRef.document(tripId).update(
            mapOf(
                "metadata.auditFlagged" to true,
                "metadata.auditReason" to reason,
                "metadata.auditFlaggedBy" to adminId,
                "metadata.auditFlaggedAt" to now,
                "updatedAt" to now
            )
        ).await()

        // Create audit event
        tripEventRepository.createEvent(
            TripEvent(
                tripId = tripId,
                type = TripEventType.NOTE_ADDED,
                description = "Trip flagged for audit: $reason",
                createdBy = adminId,
                timestamp = now,
                metadata = mapOf(
                    "eventCategory" to "AUDIT_FLAG",
                    "auditReason" to reason
                )
            )
        )
    }

    // ═══════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get trip counts by status for dashboard badges.
     */
    suspend fun getTripCountsByStatus(
        companyId: String
    ): ResultState<Map<TripStatus, Int>> = firebaseSafeCall {
        val snapshot = tripsRef
            .whereEqualTo("companyId", companyId)
            .get()
            .await()

        val trips = snapshot.toModelList(Trip.Companion::fromMap)
        trips.groupBy { it.status }.mapValues { it.value.size }
    }
}
