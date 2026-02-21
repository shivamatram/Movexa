package com.example.movexa.data.model

import com.example.movexa.data.model.enums.TripStatus

/**
 * GeoPoint representation for location coordinates.
 */
data class GeoPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "latitude" to latitude,
        "longitude" to longitude
    )

    companion object {
        fun fromMap(map: Map<String, Any?>?): GeoPoint {
            if (map == null) return GeoPoint()
            return GeoPoint(
                latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0
            )
        }
    }

    val isValid: Boolean
        get() = latitude != 0.0 || longitude != 0.0
}

/**
 * Trip data model for tracking fleet journeys.
 *
 * Firestore collection: trips/{tripId}
 */
data class Trip(
    val tripId: String = "",
    val vehicleId: String = "",
    val driverId: String = "",
    val companyId: String = "",
    val pickupLocation: GeoPoint = GeoPoint(),
    val pickupAddress: String = "",
    val dropLocation: GeoPoint = GeoPoint(),
    val dropAddress: String = "",
    val status: TripStatus = TripStatus.CREATED,
    val distance: Double = 0.0,
    val duration: Long = 0L,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val trackingId: String = "",
    val estimatedDistance: Double = 0.0,
    val estimatedDuration: Long = 0L,
    val notes: String = "",
    val assignedBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Whether tracking data is available for this trip.
     */
    val hasTracking: Boolean
        get() = trackingId.isNotBlank()

    /**
     * Trip duration in minutes (computed from start/end time).
     */
    val durationMinutes: Long
        get() = if (endTime > startTime) (endTime - startTime) / 60_000 else 0L

    /**
     * Formatted distance (e.g., "125.3 km").
     */
    val distanceDisplay: String
        get() = "%.1f km".format(distance)

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "tripId" to tripId,
            "vehicleId" to vehicleId,
            "driverId" to driverId,
            "companyId" to companyId,
            "pickupLocation" to pickupLocation.toMap(),
            "pickupAddress" to pickupAddress,
            "dropLocation" to dropLocation.toMap(),
            "dropAddress" to dropAddress,
            "status" to status.name,
            "distance" to distance,
            "duration" to duration,
            "startTime" to startTime,
            "endTime" to endTime,
            "trackingId" to trackingId,
            "estimatedDistance" to estimatedDistance,
            "estimatedDuration" to estimatedDuration,
            "notes" to notes,
            "assignedBy" to assignedBy,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "trips"

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): Trip {
            return Trip(
                tripId = map["tripId"] as? String ?: "",
                vehicleId = map["vehicleId"] as? String ?: "",
                driverId = map["driverId"] as? String ?: "",
                companyId = map["companyId"] as? String ?: "",
                pickupLocation = GeoPoint.fromMap(map["pickupLocation"] as? Map<String, Any?>),
                pickupAddress = map["pickupAddress"] as? String ?: "",
                dropLocation = GeoPoint.fromMap(map["dropLocation"] as? Map<String, Any?>),
                dropAddress = map["dropAddress"] as? String ?: "",
                status = TripStatus.fromString(map["status"] as? String),
                distance = (map["distance"] as? Number)?.toDouble() ?: 0.0,
                duration = (map["duration"] as? Number)?.toLong() ?: 0L,
                startTime = (map["startTime"] as? Number)?.toLong() ?: 0L,
                endTime = (map["endTime"] as? Number)?.toLong() ?: 0L,
                trackingId = map["trackingId"] as? String ?: "",
                estimatedDistance = (map["estimatedDistance"] as? Number)?.toDouble() ?: 0.0,
                estimatedDuration = (map["estimatedDuration"] as? Number)?.toLong() ?: 0L,
                notes = map["notes"] as? String ?: "",
                assignedBy = map["assignedBy"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
