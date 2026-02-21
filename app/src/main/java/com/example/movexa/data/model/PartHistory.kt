package com.example.movexa.data.model

/**
 * Parts history data model for tracking vehicle part replacements.
 *
 * Firestore collection: parts_history/{partId}
 */
data class PartHistory(
    val partId: String = "",
    val vehicleId: String = "",
    val companyId: String = "",
    val partName: String = "",
    val partNumber: String = "",
    val changedAtKm: Long = 0L,
    val expectedLifeKm: Long = 0L,
    val cost: Double = 0.0,
    val brand: String = "",
    val supplierName: String = "",
    val warrantyKm: Long = 0L,
    val date: Long = 0L,
    val installedBy: String = "",
    val notes: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Remaining life in km from current odometer.
     */
    fun remainingLifeKm(currentOdometer: Long): Long {
        val expectedEnd = changedAtKm + expectedLifeKm
        return if (expectedEnd > currentOdometer) expectedEnd - currentOdometer else 0L
    }

    /**
     * Part life usage percentage.
     */
    fun usagePercent(currentOdometer: Long): Float {
        if (expectedLifeKm <= 0) return 0f
        val used = currentOdometer - changedAtKm
        return ((used.toFloat() / expectedLifeKm.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    /**
     * Whether the part has exceeded its expected life.
     */
    fun isExpired(currentOdometer: Long): Boolean {
        return expectedLifeKm > 0 && (currentOdometer - changedAtKm) >= expectedLifeKm
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "partId" to partId,
            "vehicleId" to vehicleId,
            "companyId" to companyId,
            "partName" to partName,
            "partNumber" to partNumber,
            "changedAtKm" to changedAtKm,
            "expectedLifeKm" to expectedLifeKm,
            "cost" to cost,
            "brand" to brand,
            "supplierName" to supplierName,
            "warrantyKm" to warrantyKm,
            "date" to date,
            "installedBy" to installedBy,
            "notes" to notes,
            "createdBy" to createdBy,
            "createdAt" to createdAt,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "parts_history"

        fun fromMap(map: Map<String, Any?>): PartHistory {
            return PartHistory(
                partId = map["partId"] as? String ?: "",
                vehicleId = map["vehicleId"] as? String ?: "",
                companyId = map["companyId"] as? String ?: "",
                partName = map["partName"] as? String ?: "",
                partNumber = map["partNumber"] as? String ?: "",
                changedAtKm = (map["changedAtKm"] as? Number)?.toLong() ?: 0L,
                expectedLifeKm = (map["expectedLifeKm"] as? Number)?.toLong() ?: 0L,
                cost = (map["cost"] as? Number)?.toDouble() ?: 0.0,
                brand = map["brand"] as? String ?: "",
                supplierName = map["supplierName"] as? String ?: "",
                warrantyKm = (map["warrantyKm"] as? Number)?.toLong() ?: 0L,
                date = (map["date"] as? Number)?.toLong() ?: 0L,
                installedBy = map["installedBy"] as? String ?: "",
                notes = map["notes"] as? String ?: "",
                createdBy = map["createdBy"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
