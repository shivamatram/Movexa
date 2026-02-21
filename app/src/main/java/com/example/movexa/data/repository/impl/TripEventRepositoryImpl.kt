package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TripEvent
import com.example.movexa.data.model.enums.TripEventType
import com.example.movexa.data.remote.toModelList
import com.example.movexa.data.repository.BaseFirestoreRepository
import com.example.movexa.data.repository.contracts.TripEventRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [TripEventRepository].
 *
 * Manages the "trip_events" collection for trip timeline logging.
 */
class TripEventRepositoryImpl : BaseFirestoreRepository<TripEvent>(), TripEventRepository {

    override val collectionName: String = TripEvent.COLLECTION_NAME

    override fun fromMap(map: Map<String, Any?>): TripEvent = TripEvent.fromMap(map)
    override fun toMap(model: TripEvent): Map<String, Any?> = model.toMap()
    override fun getDocumentId(model: TripEvent): String = model.eventId

    override fun getIdFieldName(): String = "eventId"

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════

    override suspend fun createEvent(event: TripEvent): ResultState<String> = create(event)

    override suspend fun createBatchEvents(events: List<TripEvent>): ResultState<List<String>> =
        createBatch(events)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getEventById(eventId: String): ResultState<TripEvent?> =
        getById(eventId)

    override suspend fun getEventsByTrip(tripId: String): ResultState<List<TripEvent>> =
        query { ref ->
            ref.whereEqualTo("tripId", tripId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
        }

    override suspend fun getEventsByType(
        tripId: String,
        type: TripEventType
    ): ResultState<List<TripEvent>> = query { ref ->
        ref.whereEqualTo("tripId", tripId)
            .whereEqualTo("type", type.name)
            .orderBy("timestamp", Query.Direction.ASCENDING)
    }

    override suspend fun getLatestEvent(tripId: String): ResultState<TripEvent?> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("tripId", tripId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            snapshot.toModelList(::fromMap).firstOrNull()
        }

    override suspend fun getEventCount(tripId: String): ResultState<Int> =
        count { ref -> ref.whereEqualTo("tripId", tripId) }

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    override suspend fun deleteEvent(eventId: String): ResultState<Unit> = delete(eventId)

    override suspend fun deleteEventsForTrip(tripId: String): ResultState<Unit> =
        firebaseSafeCall {
            val snapshot = collectionRef
                .whereEqualTo("tripId", tripId)
                .get()
                .await()
            val ids = snapshot.documents.map { it.id }
            if (ids.isNotEmpty()) {
                deleteMultiple(ids).let { result ->
                    if (result is ResultState.Error) throw Exception(result.message)
                }
            }
        }

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeTripEvents(tripId: String): Flow<ResultState<List<TripEvent>>> =
        observeCollection { ref ->
            ref.whereEqualTo("tripId", tripId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
        }
}
