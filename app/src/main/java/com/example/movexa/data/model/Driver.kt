package com.example.movexa.data.model

import com.example.movexa.data.model.enums.VerificationStatus

/**
 * Driver data model extending user information with driver-specific fields.
 * Linked to a User via userId.
 *
 * Firestore collection: drivers/{driverId}
 */
data class Driver(
    val driverId: String = "",
    val userId: String = "",
    val licenseNumber: String = "",
    val licenseUrl: String? = null,
    val licenseExpiry: Long = 0L,
    val idProofUrl: String? = null,
    val rating: Float = 0f,
    val totalTrips: Int = 0,
    val blocked: Boolean = false,
    val assignedVehicleId: String? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.PENDING,
    val companyId: String = "",
    val emergencyContact: String = "",
    val bloodGroup: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Whether this driver can be assigned to trips.
     */
    val isEligible: Boolean
        get() = !blocked &&
                verificationStatus.isApproved() &&
                assignedVehicleId != null

    /**
     * Formatted rating string (e.g., "4.2/5.0").
     */
    val ratingDisplay: String
        get() = "%.1f/5.0".format(rating)

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "driverId" to driverId,
            "userId" to userId,
            "licenseNumber" to licenseNumber,
            "licenseUrl" to licenseUrl,
            "licenseExpiry" to licenseExpiry,
            "idProofUrl" to idProofUrl,
            "rating" to rating,
            "totalTrips" to totalTrips,
            "blocked" to blocked,
            "assignedVehicleId" to assignedVehicleId,
            "verificationStatus" to verificationStatus.name,
            "companyId" to companyId,
            "emergencyContact" to emergencyContact,
            "bloodGroup" to bloodGroup,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "drivers"

        fun fromMap(map: Map<String, Any?>): Driver {
            return Driver(
                driverId = map["driverId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                licenseNumber = map["licenseNumber"] as? String ?: "",
                licenseUrl = map["licenseUrl"] as? String,
                licenseExpiry = (map["licenseExpiry"] as? Number)?.toLong() ?: 0L,
                idProofUrl = map["idProofUrl"] as? String,
                rating = (map["rating"] as? Number)?.toFloat() ?: 0f,
                totalTrips = (map["totalTrips"] as? Number)?.toInt() ?: 0,
                blocked = map["blocked"] as? Boolean ?: false,
                assignedVehicleId = map["assignedVehicleId"] as? String,
                verificationStatus = VerificationStatus.fromString(
                    map["verificationStatus"] as? String
                ),
                companyId = map["companyId"] as? String ?: "",
                emergencyContact = map["emergencyContact"] as? String ?: "",
                bloodGroup = map["bloodGroup"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
