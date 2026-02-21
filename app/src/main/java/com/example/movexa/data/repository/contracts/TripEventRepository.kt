package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TripEvent
import com.example.movexa.data.model.enums.TripEventType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for TripEvent repository operations.
 * Handles trip event logging and timeline queries.
 */
interface TripEventRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createEvent(event: TripEvent): ResultState<String>
    suspend fun createBatchEvents(events: List<TripEvent>): ResultState<List<String>>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getEventById(eventId: String): ResultState<TripEvent?>
    suspend fun getEventsByTrip(tripId: String): ResultState<List<TripEvent>>
    suspend fun getEventsByType(
        tripId: String,
        type: TripEventType
    ): ResultState<List<TripEvent>>
    suspend fun getLatestEvent(tripId: String): ResultState<TripEvent?>
    suspend fun getEventCount(tripId: String): ResultState<Int>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteEvent(eventId: String): ResultState<Unit>
    suspend fun deleteEventsForTrip(tripId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeTripEvents(tripId: String): Flow<ResultState<List<TripEvent>>>
}
