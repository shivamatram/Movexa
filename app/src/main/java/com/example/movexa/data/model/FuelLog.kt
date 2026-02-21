package com.example.movexa.data.model

/**
 * Fuel log data model for tracking fuel consumption per vehicle.
 *
 * Firestore collection: fuel_logs/{fuelId}
 */
data class FuelLog(
    val fuelId: String = "",
    val vehicleId: String = "",
    val driverId: String = "",
    val companyId: String = "",
    val quantity: Double = 0.0,
    val cost: Double = 0.0,
    val odometer: Long = 0L,
    val mileage: Double = 0.0,
    val fuelType: String = "",
    val billUrl: String? = null,
    val stationName: String = "",
    val location: GeoPoint? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val verifiedBy: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Cost per litre.
     */
    val costPerLitre: Double
        get() = if (quantity > 0) cost / quantity else 0.0

    /**
     * Formatted cost display (e.g., "₹1,250.00").
     */
    val costDisplay: String
        get() = "₹%.2f".format(cost)

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "fuelId" to fuelId,
            "vehicleId" to vehicleId,
            "driverId" to driverId,
            "companyId" to companyId,
            "quantity" to quantity,
            "cost" to cost,
            "odometer" to odometer,
            "mileage" to mileage,
            "fuelType" to fuelType,
            "billUrl" to billUrl,
            "stationName" to stationName,
            "location" to location?.toMap(),
            "timestamp" to timestamp,
            "notes" to notes,
            "verifiedBy" to verifiedBy,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "fuel_logs"

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): FuelLog {
            return FuelLog(
                fuelId = map["fuelId"] as? String ?: "",
                vehicleId = map["vehicleId"] as? String ?: "",
                driverId = map["driverId"] as? String ?: "",
                companyId = map["companyId"] as? String ?: "",
                quantity = (map["quantity"] as? Number)?.toDouble() ?: 0.0,
                cost = (map["cost"] as? Number)?.toDouble() ?: 0.0,
                odometer = (map["odometer"] as? Number)?.toLong() ?: 0L,
                mileage = (map["mileage"] as? Number)?.toDouble() ?: 0.0,
                fuelType = map["fuelType"] as? String ?: "",
                billUrl = map["billUrl"] as? String,
                stationName = map["stationName"] as? String ?: "",
                location = (map["location"] as? Map<String, Any?>)?.let {
                    GeoPoint.fromMap(it)
                },
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                notes = map["notes"] as? String ?: "",
                verifiedBy = map["verifiedBy"] as? String,
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
