package com.example.movexa.data.model

/**
 * Repair record data model for tracking vehicle repair history.
 *
 * Firestore collection: repairs/{repairId}
 */
data class Repair(
    val repairId: String = "",
    val vehicleId: String = "",
    val companyId: String = "",
    val issue: String = "",
    val repairDone: String = "",
    val cost: Double = 0.0,
    val date: Long = 0L,
    val odometer: Long = 0L,
    val partsReplaced: List<String> = emptyList(),
    val repairedBy: String = "",
    val workshopName: String = "",
    val warrantyUntil: Long = 0L,
    val notes: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Formatted cost display.
     */
    val costDisplay: String
        get() = "₹%.2f".format(cost)

    /**
     * Whether warranty is still active.
     */
    fun isUnderWarranty(): Boolean {
        return warrantyUntil > 0 && System.currentTimeMillis() < warrantyUntil
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "repairId" to repairId,
            "vehicleId" to vehicleId,
            "companyId" to companyId,
            "issue" to issue,
            "repairDone" to repairDone,
            "cost" to cost,
            "date" to date,
            "odometer" to odometer,
            "partsReplaced" to partsReplaced,
            "repairedBy" to repairedBy,
            "workshopName" to workshopName,
            "warrantyUntil" to warrantyUntil,
            "notes" to notes,
            "createdBy" to createdBy,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "repairs"

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): Repair {
            return Repair(
                repairId = map["repairId"] as? String ?: "",
                vehicleId = map["vehicleId"] as? String ?: "",
                companyId = map["companyId"] as? String ?: "",
                issue = map["issue"] as? String ?: "",
                repairDone = map["repairDone"] as? String ?: "",
                cost = (map["cost"] as? Number)?.toDouble() ?: 0.0,
                date = (map["date"] as? Number)?.toLong() ?: 0L,
                odometer = (map["odometer"] as? Number)?.toLong() ?: 0L,
                partsReplaced = (map["partsReplaced"] as? List<String>) ?: emptyList(),
                repairedBy = map["repairedBy"] as? String ?: "",
                workshopName = map["workshopName"] as? String ?: "",
                warrantyUntil = (map["warrantyUntil"] as? Number)?.toLong() ?: 0L,
                notes = map["notes"] as? String ?: "",
                createdBy = map["createdBy"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
