package com.example.movexa.data.model

/**
 * Live tracking location data model for Realtime Database.
 *
 * Realtime Database path: tracking_locations/{companyId}/{vehicleId}
 *
 * Optimized for rapid writes/reads during live trip tracking.
 * Low-latency access via Firebase Realtime Database (not Firestore).
 */
data class TrackingLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speed: Float = 0f,
    val heading: Float = 0f,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val tripId: String = "",
    val driverId: String = "",
    val vehicleId: String = "",
    val isMoving: Boolean = false
) {
    /**
     * Speed in km/h.
     */
    val speedKmh: Float
        get() = speed * 3.6f

    /**
     * Whether this is a valid location fix.
     */
    val isValid: Boolean
        get() = lat != 0.0 || lng != 0.0

    /**
     * Convert to GeoPoint for use with Firestore models.
     */
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)

    /**
     * Convert to a Map for Realtime Database storage.
     * Uses short keys for bandwidth efficiency.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "lat" to lat,
            "lng" to lng,
            "speed" to speed,
            "heading" to heading,
            "accuracy" to accuracy,
            "timestamp" to timestamp,
            "tripId" to tripId,
            "driverId" to driverId,
            "vehicleId" to vehicleId,
            "isMoving" to isMoving
        )
    }

    companion object {
        const val PATH_TRACKING_LOCATIONS = "tracking_locations"

        /**
         * Build the Realtime Database path for a specific vehicle.
         */
        fun buildPath(companyId: String, vehicleId: String): String {
            return "$PATH_TRACKING_LOCATIONS/$companyId/$vehicleId"
        }

        /**
         * Build the Realtime Database path for all company vehicles.
         */
        fun buildCompanyPath(companyId: String): String {
            return "$PATH_TRACKING_LOCATIONS/$companyId"
        }

        fun fromMap(map: Map<String, Any?>): TrackingLocation {
            return TrackingLocation(
                lat = (map["lat"] as? Number)?.toDouble() ?: 0.0,
                lng = (map["lng"] as? Number)?.toDouble() ?: 0.0,
                speed = (map["speed"] as? Number)?.toFloat() ?: 0f,
                heading = (map["heading"] as? Number)?.toFloat() ?: 0f,
                accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                tripId = map["tripId"] as? String ?: "",
                driverId = map["driverId"] as? String ?: "",
                vehicleId = map["vehicleId"] as? String ?: "",
                isMoving = map["isMoving"] as? Boolean ?: false
            )
        }
    }
}
