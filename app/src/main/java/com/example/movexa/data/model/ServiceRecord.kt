package com.example.movexa.data.model

import com.example.movexa.data.model.enums.ServiceType

/**
 * Service record data model for tracking vehicle maintenance/service history.
 *
 * Firestore collection: services/{serviceId}
 */
data class ServiceRecord(
    val serviceId: String = "",
    val vehicleId: String = "",
    val companyId: String = "",
    val odometer: Long = 0L,
    val serviceType: ServiceType = ServiceType.OTHER,
    val nextServiceKm: Long = 0L,
    val date: Long = 0L,
    val cost: Double = 0.0,
    val description: String = "",
    val servicedBy: String = "",
    val workshopName: String = "",
    val completed: Boolean = false,
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Remaining km until next service from a given current odometer.
     */
    fun remainingKmToNextService(currentOdometer: Long): Long {
        return if (nextServiceKm > 0) nextServiceKm - currentOdometer else 0L
    }

    /**
     * Whether next service is overdue based on current odometer.
     */
    fun isOverdue(currentOdometer: Long): Boolean {
        return nextServiceKm > 0 && currentOdometer >= nextServiceKm
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "serviceId" to serviceId,
            "vehicleId" to vehicleId,
            "companyId" to companyId,
            "odometer" to odometer,
            "serviceType" to serviceType.name,
            "nextServiceKm" to nextServiceKm,
            "date" to date,
            "cost" to cost,
            "description" to description,
            "servicedBy" to servicedBy,
            "workshopName" to workshopName,
            "completed" to completed,
            "createdBy" to createdBy,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "services"

        fun fromMap(map: Map<String, Any?>): ServiceRecord {
            return ServiceRecord(
                serviceId = map["serviceId"] as? String ?: "",
                vehicleId = map["vehicleId"] as? String ?: "",
                companyId = map["companyId"] as? String ?: "",
                odometer = (map["odometer"] as? Number)?.toLong() ?: 0L,
                serviceType = ServiceType.fromString(map["serviceType"] as? String),
                nextServiceKm = (map["nextServiceKm"] as? Number)?.toLong() ?: 0L,
                date = (map["date"] as? Number)?.toLong() ?: 0L,
                cost = (map["cost"] as? Number)?.toDouble() ?: 0.0,
                description = map["description"] as? String ?: "",
                servicedBy = map["servicedBy"] as? String ?: "",
                workshopName = map["workshopName"] as? String ?: "",
                completed = map["completed"] as? Boolean ?: false,
                createdBy = map["createdBy"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
