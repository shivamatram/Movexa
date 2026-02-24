package com.example.movexa.data.repository

import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.GeoPoint
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.TripEvent
import com.example.movexa.data.model.User
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.TripEventType
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.remote.FirebaseProvider
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import com.example.movexa.data.repository.impl.DriverPerformanceRepositoryImpl
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.TripEventRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

/**
 * Repository for the Driver Home screen.
 *
 * Aggregates data from multiple data sources to power
 * the driver's operational control center:
 *
 *  • Current user profile (name, avatar)
 *  • Driver record (driverId, assignedVehicleId, rating)
 *  • Active trip (real-time listener)
 *  • Assigned vehicle details
 *  • Performance score summary
 *  • Today's trip statistics
 *  • Today's alert count
 *
 * ═══════════════════════════════════════════════════════════════
 * TRIP STATE MACHINE
 * ═══════════════════════════════════════════════════════════════
 *
 * Valid driver-side transitions:
 *
 *   ASSIGNED  → ACCEPTED
 *   ACCEPTED  → STARTED
 *   STARTED   → COMPLETED
 *
 * Each transition:
 *  1. Validates the transition via [TripStatus.canTransitionTo]
 *  2. Updates the trip document in Firestore
 *  3. Creates a [TripEvent] record for audit trail
 */
class DriverHomeRepository : BaseRepository() {

    // ─── Delegates ──────────────────────────────────────────────
    private val tripRepository = TripRepositoryImpl()
    private val driverRepository = DriverRepositoryImpl()
    private val vehicleRepository = VehicleRepositoryImpl()
    private val performanceRepository = DriverPerformanceRepositoryImpl()
    private val tripEventRepository = TripEventRepositoryImpl()
    private val alertRepository = AlertRepositoryImpl()

    // ═══════════════════════════════════════════════════════════
    //  USER / DRIVER PROFILE
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch the User document to get display name and avatar.
     */
    suspend fun getUserProfile(userId: String): ResultState<User?> =
        firebaseSafeCall {
            val doc = FirebaseProvider.firestore
                .collection(FirebaseProvider.Collections.USERS)
                .document(userId)
                .get()
                .await()
            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                User.fromMap(doc.data as Map<String, Any?>)
            } else null
        }

    /**
     * Get the driver record.
     */
    suspend fun getDriverByUserId(userId: String): ResultState<Driver?> =
        driverRepository.getDriverByUserId(userId)

    /**
     * Get the driver record, auto-creating one if it doesn't exist.
     */
    suspend fun getOrCreateDriverByUserId(userId: String): ResultState<Driver> =
        driverRepository.getOrCreateDriverByUserId(userId)

    /**
     * Observe the driver document in real-time.
     */
    fun observeDriver(driverId: String): Flow<ResultState<Driver?>> =
        driverRepository.observeDriver(driverId)

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE
    // ═══════════════════════════════════════════════════════════

    /**
     * Get vehicle details for the driver's assigned vehicle.
     */
    suspend fun getVehicle(vehicleId: String): ResultState<Vehicle?> =
        vehicleRepository.getVehicleById(vehicleId)

    /**
     * Observe vehicle in real-time for status changes.
     */
    fun observeVehicle(vehicleId: String): Flow<ResultState<Vehicle?>> =
        vehicleRepository.observeVehicle(vehicleId)

    // ═══════════════════════════════════════════════════════════
    //  ACTIVE TRIP
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the driver's current active (non-terminal) trip.
     */
    suspend fun getActiveTrip(driverId: String): ResultState<Trip?> =
        tripRepository.getDriverActiveTrip(driverId)

    /**
     * Observe the driver's non-terminal trips in real-time.
     * Returns the first active trip found, or null if none.
     */
    fun observeActiveTrip(driverId: String): Flow<ResultState<Trip?>> = callbackFlow {
        trySend(ResultState.Loading)

        val registration: ListenerRegistration = FirebaseProvider.firestore
            .collection(FirebaseProvider.Collections.TRIPS)
            .whereEqualTo("driverId", driverId)
            .whereIn("status", TripStatus.activeStatuses().map { it.name })
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ResultState.Error(error.message ?: "Failed to observe trip"))
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    @Suppress("UNCHECKED_CAST")
                    val trip = Trip.fromMap(doc.data as Map<String, Any?>)
                    trySend(ResultState.Success(trip))
                } else {
                    trySend(ResultState.Success(null))
                }
            }

        awaitClose { registration.remove() }
    }

    // ═══════════════════════════════════════════════════════════
    //  TRIP ACTIONS — STATE MACHINE
    // ═══════════════════════════════════════════════════════════

    /**
     * Accept an assigned trip.
     * Transition: ASSIGNED → ACCEPTED
     */
    suspend fun acceptTrip(trip: Trip, driverId: String): ResultState<Unit> {
        if (!trip.status.canTransitionTo(TripStatus.ACCEPTED)) {
            return ResultState.Error(
                "Cannot accept trip in ${trip.status.displayName} state"
            )
        }
        if (trip.driverId != driverId) {
            return ResultState.Error("This trip is not assigned to you")
        }

        val updateResult = tripRepository.updateTripStatus(
            trip.tripId, TripStatus.ACCEPTED
        )
        if (updateResult is ResultState.Success) {
            logTripEvent(trip.tripId, TripEventType.DRIVER_ASSIGNED, driverId,
                "Trip accepted by driver")
        }
        return updateResult
    }

    /**
     * Start an accepted trip.
     * Transition: ACCEPTED → STARTED
     */
    suspend fun startTrip(trip: Trip, driverId: String): ResultState<Unit> {
        if (!trip.status.canTransitionTo(TripStatus.STARTED)) {
            return ResultState.Error(
                "Cannot start trip in ${trip.status.displayName} state"
            )
        }
        if (trip.driverId != driverId) {
            return ResultState.Error("This trip is not assigned to you")
        }

        val updateResult = tripRepository.startTrip(trip.tripId)
        if (updateResult is ResultState.Success) {
            logTripEvent(trip.tripId, TripEventType.STARTED, driverId,
                "Trip started by driver")
        }
        return updateResult
    }

    /**
     * Complete a started trip.
     * Transition: STARTED → COMPLETED
     */
    suspend fun completeTrip(
        trip: Trip,
        driverId: String,
        actualDistance: Double,
        actualDuration: Long
    ): ResultState<Unit> {
        if (!trip.status.canTransitionTo(TripStatus.COMPLETED)) {
            return ResultState.Error(
                "Cannot complete trip in ${trip.status.displayName} state"
            )
        }
        if (trip.driverId != driverId) {
            return ResultState.Error("This trip is not assigned to you")
        }

        val updateResult = tripRepository.completeTrip(
            trip.tripId, actualDistance, actualDuration
        )
        if (updateResult is ResultState.Success) {
            logTripEvent(trip.tripId, TripEventType.COMPLETED, driverId,
                "Trip completed by driver")
        }
        return updateResult
    }

    // ═══════════════════════════════════════════════════════════
    //  TRIP EVENTS
    // ═══════════════════════════════════════════════════════════

    /**
     * Log a trip event in the audit trail.
     */
    private suspend fun logTripEvent(
        tripId: String,
        type: TripEventType,
        createdBy: String,
        description: String
    ) {
        val event = TripEvent(
            eventId = UUID.randomUUID().toString(),
            tripId = tripId,
            type = type,
            timestamp = System.currentTimeMillis(),
            description = description,
            createdBy = createdBy
        )
        tripEventRepository.createEvent(event)
    }

    // ═══════════════════════════════════════════════════════════
    //  PERFORMANCE SCORE
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the driver's performance summary.
     */
    suspend fun getDriverSummary(driverId: String): ResultState<DriverSummary?> =
        performanceRepository.getSummaryByDriver(driverId)

    /**
     * Observe the driver's performance summary in real-time.
     */
    fun observeDriverSummary(driverId: String): Flow<ResultState<DriverSummary?>> =
        performanceRepository.observeDriverSummary(driverId)

    // ═══════════════════════════════════════════════════════════
    //  TODAY STATISTICS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get today's trip statistics for a driver:
     * - Trips completed today
     * - Total distance driven today
     * - Total duration driven today
     */
    suspend fun getTodayStats(driverId: String): ResultState<TodayStats> =
        firebaseSafeCall {
            val (startOfDay, endOfDay) = todayRange()

            val snapshot = FirebaseProvider.firestore
                .collection(FirebaseProvider.Collections.TRIPS)
                .whereEqualTo("driverId", driverId)
                .whereEqualTo("status", TripStatus.COMPLETED.name)
                .whereGreaterThanOrEqualTo("endTime", startOfDay)
                .whereLessThanOrEqualTo("endTime", endOfDay)
                .get()
                .await()

            @Suppress("UNCHECKED_CAST")
            val trips = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Trip.fromMap(it as Map<String, Any?>) }
            }

            TodayStats(
                tripsCompleted = trips.size,
                distanceDriven = trips.sumOf { it.distance },
                durationMinutes = trips.sumOf { it.durationMinutes }
            )
        }

    /**
     * Get today's active alert count for the driver.
     */
    suspend fun getTodayAlertCount(driverId: String): ResultState<Int> =
        firebaseSafeCall {
            val (startOfDay, _) = todayRange()

            val snapshot = FirebaseProvider.firestore
                .collection(FirebaseProvider.Collections.ALERTS)
                .whereEqualTo("driverId", driverId)
                .whereGreaterThanOrEqualTo("createdAt", startOfDay)
                .get()
                .await()

            snapshot.size()
        }

    /**
     * Observe today's completed trips in real-time for live stats updates.
     */
    fun observeTodayStats(driverId: String): Flow<ResultState<TodayStats>> = callbackFlow {
        trySend(ResultState.Loading)

        val (startOfDay, endOfDay) = todayRange()

        val registration = FirebaseProvider.firestore
            .collection(FirebaseProvider.Collections.TRIPS)
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("status", TripStatus.COMPLETED.name)
            .whereGreaterThanOrEqualTo("endTime", startOfDay)
            .whereLessThanOrEqualTo("endTime", endOfDay)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ResultState.Error(error.message ?: "Stats update failed"))
                    return@addSnapshotListener
                }

                @Suppress("UNCHECKED_CAST")
                val trips = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Trip.fromMap(it as Map<String, Any?>) }
                } ?: emptyList()

                trySend(
                    ResultState.Success(
                        TodayStats(
                            tripsCompleted = trips.size,
                            distanceDriven = trips.sumOf { it.distance },
                            durationMinutes = trips.sumOf { it.durationMinutes }
                        )
                    )
                )
            }

        awaitClose { registration.remove() }
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the start and end timestamps for today.
     */
    private fun todayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis
        return startOfDay to endOfDay
    }



    // ═══════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════

    /**
     * Today's aggregated trip statistics.
     */
    data class TodayStats(
        val tripsCompleted: Int = 0,
        val distanceDriven: Double = 0.0,
        val durationMinutes: Long = 0L
    ) {
        val distanceDisplay: String
            get() = "%.1f km".format(distanceDriven)

        val durationDisplay: String
            get() {
                val hours = durationMinutes / 60
                val mins = durationMinutes % 60
                return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            }
    }
}
