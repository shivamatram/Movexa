package com.example.movexa.data.model

import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.model.enums.VehicleType

/**
 * Vehicle data model for the fleet management system.
 * Represents a vehicle registered in the fleet.
 *
 * Firestore collection: vehicles/{vehicleId}
 */
data class Vehicle(
    val vehicleId: String = "",
    val number: String = "",
    val type: VehicleType = VehicleType.OTHER,
    val capacity: Int = 0,
    val status: VehicleStatus = VehicleStatus.AVAILABLE,
    val assignedDriverId: String? = null,
    val documentsValid: Boolean = false,
    val lastOdometer: Long = 0L,
    val companyId: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val fuelType: String = "",
    val insuranceExpiry: Long = 0L,
    val fitnessExpiry: Long = 0L,
    val imageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Display label combining number and type.
     */
    val displayLabel: String
        get() = "$number (${type.displayName})"

    /**
     * Whether the vehicle can be assigned to a new trip.
     */
    val isAvailableForTrip: Boolean
        get() = status.canAssignTrip() && documentsValid && assignedDriverId != null

    /**
     * Convert to a Map for Firestore document storage.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "vehicleId" to vehicleId,
            "number" to number,
            "type" to type.name,
            "capacity" to capacity,
            "status" to status.name,
            "assignedDriverId" to assignedDriverId,
            "documentsValid" to documentsValid,
            "lastOdometer" to lastOdometer,
            "companyId" to companyId,
            "make" to make,
            "model" to model,
            "year" to year,
            "fuelType" to fuelType,
            "insuranceExpiry" to insuranceExpiry,
            "fitnessExpiry" to fitnessExpiry,
            "imageUrl" to imageUrl,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "vehicles"

        fun fromMap(map: Map<String, Any?>): Vehicle {
            return Vehicle(
                vehicleId = map["vehicleId"] as? String ?: "",
                number = map["number"] as? String ?: "",
                type = VehicleType.fromString(map["type"] as? String),
                capacity = (map["capacity"] as? Number)?.toInt() ?: 0,
                status = VehicleStatus.fromString(map["status"] as? String),
                assignedDriverId = map["assignedDriverId"] as? String,
                documentsValid = map["documentsValid"] as? Boolean ?: false,
                lastOdometer = (map["lastOdometer"] as? Number)?.toLong() ?: 0L,
                companyId = map["companyId"] as? String ?: "",
                make = map["make"] as? String ?: "",
                model = map["model"] as? String ?: "",
                year = (map["year"] as? Number)?.toInt() ?: 0,
                fuelType = map["fuelType"] as? String ?: "",
                insuranceExpiry = (map["insuranceExpiry"] as? Number)?.toLong() ?: 0L,
                fitnessExpiry = (map["fitnessExpiry"] as? Number)?.toLong() ?: 0L,
                imageUrl = map["imageUrl"] as? String,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
