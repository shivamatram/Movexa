package com.example.movexa.data.model

import com.example.movexa.data.model.enums.ActivityLogType

/**
 * Activity log data model for audit trail and system event logging.
 *
 * Firestore collection: activity_logs/{logId}
 */
data class ActivityLog(
    val logId: String = "",
    val type: ActivityLogType = ActivityLogType.SYSTEM,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val referenceId: String = "",
    val referenceType: String = "",
    val userId: String = "",
    val companyId: String = "",
    val ipAddress: String = "",
    val metadata: Map<String, Any> = emptyMap()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "logId" to logId,
            "type" to type.name,
            "message" to message,
            "timestamp" to timestamp,
            "referenceId" to referenceId,
            "referenceType" to referenceType,
            "userId" to userId,
            "companyId" to companyId,
            "ipAddress" to ipAddress,
            "metadata" to metadata
        )
    }

    companion object {
        const val COLLECTION_NAME = "activity_logs"

        fun fromMap(map: Map<String, Any?>): ActivityLog {
            return ActivityLog(
                logId = map["logId"] as? String ?: "",
                type = ActivityLogType.fromString(map["type"] as? String),
                message = map["message"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                referenceId = map["referenceId"] as? String ?: "",
                referenceType = map["referenceType"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                companyId = map["companyId"] as? String ?: "",
                ipAddress = map["ipAddress"] as? String ?: "",
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }
    }
}
