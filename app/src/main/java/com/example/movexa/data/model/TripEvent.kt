package com.example.movexa.data.model

import com.example.movexa.data.model.enums.TripEventType

/**
 * Trip event data model for logging events during a trip lifecycle.
 *
 * Firestore collection: trip_events/{eventId}
 */
data class TripEvent(
    val eventId: String = "",
    val tripId: String = "",
    val type: TripEventType = TripEventType.NOTE_ADDED,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = "",
    val location: GeoPoint? = null,
    val createdBy: String = "",
    val metadata: Map<String, Any> = emptyMap()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "eventId" to eventId,
            "tripId" to tripId,
            "type" to type.name,
            "timestamp" to timestamp,
            "description" to description,
            "location" to location?.toMap(),
            "createdBy" to createdBy,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "trip_events"

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): TripEvent {
            return TripEvent(
                eventId = map["eventId"] as? String ?: "",
                tripId = map["tripId"] as? String ?: "",
                type = TripEventType.fromString(map["type"] as? String),
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                description = map["description"] as? String ?: "",
                location = (map["location"] as? Map<String, Any?>)?.let {
                    GeoPoint.fromMap(it)
                },
                createdBy = map["createdBy"] as? String ?: "",
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
