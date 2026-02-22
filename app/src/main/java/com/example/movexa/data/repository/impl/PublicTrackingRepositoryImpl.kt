package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.PublicDriverInfo
import com.example.movexa.data.model.PublicLiveLocation
import com.example.movexa.data.model.PublicTripInfo
import com.example.movexa.data.model.PublicVehicleInfo
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.User
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.repository.BaseRepository
import com.example.movexa.data.repository.contracts.PublicTrackingRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * ═══════════════════════════════════════════════════════════════════
 *  PUBLIC TRACKING REPOSITORY — IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════
 *
 * Production implementation of [PublicTrackingRepository].
 *
 * Architecture notes:
 *  • This class does NOT extend [BaseFirestoreRepository] because
 *    it intentionally has NO generic CRUD.  Public customers can
 *    only READ, never write.
 *  • All data is sanitised before leaving this layer — the ViewModel
 *    and Fragment never see raw Trip/Driver/Vehicle objects.
 *  • Firestore queries use indexed `trackingId` field lookups.
 *  • Realtime Database is used for live GPS via ValueEventListener.
 *  • Error messages are customer-friendly (no stack traces).
 *
 * Data flow:
 *  ┌─────────────┐   trackingId   ┌────────────────┐
 *  │  Customer    │ ────────────→  │  Firestore     │
 *  │  (no auth)   │                │  trips where   │
 *  └─────────────┘                │  trackingId==X │
 *                                  └───────┬────────┘
 *                                          │ Trip doc
 *                                          ▼
 *                                  ┌────────────────┐
 *                                  │ sanitise →     │
 *                                  │ PublicTripInfo  │
 *                                  └───────┬────────┘
 *                          ┌───────────────┼───────────────┐
 *                          ▼               ▼               ▼
 *                   ┌──────────┐   ┌──────────┐   ┌──────────────┐
 *                   │ Vehicle  │   │  Driver   │   │ RTDB         │
 *                   │ info     │   │  + User   │   │ live loc     │
 *                   └──────────┘   └──────────┘   └──────────────┘
 *
 * @since 2026-02-22
 */
class PublicTrackingRepositoryImpl : BaseRepository(), PublicTrackingRepository {

    // ─── Firebase references ────────────────────────────────────
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val realtimeDb: DatabaseReference = FirebaseDatabase.getInstance().reference

    private val tripsRef     = firestore.collection(Trip.COLLECTION_NAME)
    private val vehiclesRef  = firestore.collection(Vehicle.COLLECTION_NAME)
    private val driversRef   = firestore.collection(Driver.COLLECTION_NAME)
    private val usersRef     = firestore.collection("users")

    // ═══════════════════════════════════════════════════════════
    //  TRIP LOOKUP
    // ═══════════════════════════════════════════════════════════

    /**
     * Find a trip by its public trackingId.
     *
     * Firestore query: `trips WHERE trackingId == <id> LIMIT 1`
     *
     * Security: The raw Trip is immediately converted to a
     * [PublicTripInfo] which strips all internal data.
     */
    override suspend fun findTripByTrackingId(
        trackingId: String
    ): ResultState<PublicTripInfo?> = safeCall {
        // Query Firestore for matching trackingId
        val snapshot = tripsRef
            .whereEqualTo("trackingId", trackingId)
            .limit(1)
            .get()
            .await()

        if (snapshot.isEmpty) {
            null
        } else {
            val doc = snapshot.documents.first()
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@safeCall null
            val trip = Trip.fromMap(data)
            PublicTripInfo.fromTrip(trip)
        }
    }

    /**
     * Observe a trip document for real-time status changes.
     *
     * Uses Firestore snapshot listener on the query
     * `trips WHERE trackingId == <id> LIMIT 1`.
     *
     * The flow re-emits whenever the document is updated
     * (e.g. status changes from STARTED → COMPLETED).
     */
    override fun observeTripByTrackingId(
        trackingId: String
    ): Flow<ResultState<PublicTripInfo?>> = callbackFlow {
        trySend(ResultState.Loading)

        var registration: ListenerRegistration? = null

        registration = tripsRef
            .whereEqualTo("trackingId", trackingId)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(
                        ResultState.Error(
                            message = "Connection lost. Please check your internet.",
                            exception = error
                        )
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    trySend(ResultState.Success(null))
                    return@addSnapshotListener
                }

                try {
                    val doc = snapshot.documents.first()
                    @Suppress("UNCHECKED_CAST")
                    val data = doc.data as? Map<String, Any?>
                    if (data != null) {
                        val trip = Trip.fromMap(data)
                        val publicInfo = PublicTripInfo.fromTrip(trip)
                        trySend(ResultState.Success(publicInfo))
                    } else {
                        trySend(ResultState.Success(null))
                    }
                } catch (e: Exception) {
                    trySend(
                        ResultState.Error(
                            message = "Error processing delivery data",
                            exception = e
                        )
                    )
                }
            }

        awaitClose {
            registration?.remove()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LIVE LOCATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe the vehicle's live GPS location from Realtime Database.
     *
     * Path: `tracking_locations/{companyId}/{vehicleId}`
     *
     * The raw [TrackingLocation] is converted to [PublicLiveLocation]
     * which strips driverId, vehicleId, and other internal fields.
     */
    override fun observeVehicleLocation(
        companyId: String,
        vehicleId: String
    ): Flow<ResultState<PublicLiveLocation?>> = callbackFlow {
        trySend(ResultState.Loading)

        val path = "${TrackingLocation.PATH_TRACKING_LOCATIONS}/$companyId/$vehicleId"
        val ref = realtimeDb.child(path)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (!snapshot.exists()) {
                        trySend(ResultState.Success(null))
                        return
                    }

                    @Suppress("UNCHECKED_CAST")
                    val data = snapshot.value as? Map<String, Any?> ?: run {
                        trySend(ResultState.Success(null))
                        return
                    }

                    val trackingLocation = TrackingLocation.fromMap(data)
                    val publicLocation = PublicLiveLocation.fromTrackingLocation(trackingLocation)
                    trySend(ResultState.Success(publicLocation))
                } catch (e: Exception) {
                    trySend(
                        ResultState.Error(
                            message = "Error processing location data",
                            exception = e
                        )
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(
                    ResultState.Error(
                        message = "Location tracking disconnected",
                        exception = error.toException()
                    )
                )
            }
        }

        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DRIVER INFO
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch sanitised driver info.
     *
     * Strategy:
     *  1. Read the Driver document to get userId, rating, totalTrips
     *  2. Read the User document (by driver.userId) to get fullName + phone
     *  3. Mask the phone and sanitise the name
     *  4. Return [PublicDriverInfo]
     *
     * If the driver or user document doesn't exist, returns a
     * generic "Your Driver" placeholder.
     */
    override suspend fun getDriverInfo(
        driverId: String
    ): ResultState<PublicDriverInfo?> = safeCall {
        if (driverId.isBlank()) return@safeCall null

        // Step 1 — Get Driver doc
        val driverDoc = driversRef.document(driverId).get().await()
        if (!driverDoc.exists()) return@safeCall null

        @Suppress("UNCHECKED_CAST")
        val driverData = driverDoc.data as? Map<String, Any?> ?: return@safeCall null
        val driver = Driver.fromMap(driverData)

        // Step 2 — Get User doc (for name + phone)
        var fullName = "Your Driver"
        var phone = ""

        if (driver.userId.isNotBlank()) {
            val userDoc = usersRef.document(driver.userId).get().await()
            if (userDoc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val userData = userDoc.data as? Map<String, Any?>
                if (userData != null) {
                    val user = User.fromMap(userData)
                    fullName = user.fullName.ifBlank { "Your Driver" }
                    phone = user.phone
                }
            }
        }

        // Also check driver's metadata for fullName fallback
        if (fullName == "Your Driver") {
            val metaName = driver.metadata["fullName"] as? String
            if (!metaName.isNullOrBlank()) fullName = metaName
        }

        // Step 3 — Build sanitised info
        PublicDriverInfo.fromUserAndDriver(
            fullName = fullName,
            phone = phone,
            rating = driver.rating,
            totalTrips = driver.totalTrips
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE INFO
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch minimal vehicle info — registration number + type.
     */
    override suspend fun getVehicleInfo(
        vehicleId: String
    ): ResultState<PublicVehicleInfo?> = safeCall {
        if (vehicleId.isBlank()) return@safeCall null

        val doc = vehiclesRef.document(vehicleId).get().await()
        if (!doc.exists()) return@safeCall null

        @Suppress("UNCHECKED_CAST")
        val data = doc.data as? Map<String, Any?> ?: return@safeCall null
        val vehicle = Vehicle.fromMap(data)
        PublicVehicleInfo.fromVehicle(vehicle)
    }
}
