package com.example.movexa.data.model

/**
 * User data model for the Movexa fleet management system.
 * Represents an authenticated user with their profile and role information.
 *
 * Prepared for Firebase Firestore mapping.
 */
data class User(
    val uid: String = "",
    val email: String = "",
    val fullName: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.DRIVER,
    val profileImageUrl: String? = null,
    val isActive: Boolean = true,
    val isVerified: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val fcmToken: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Display name or email fallback.
     */
    val displayName: String
        get() = fullName.ifBlank { email.substringBefore("@") }

    /**
     * First name extracted from full name.
     */
    val firstName: String
        get() = fullName.split(" ").firstOrNull() ?: displayName

    /**
     * Initials for avatar placeholder.
     */
    val initials: String
        get() {
            val parts = fullName.split(" ").filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                parts.size == 1 -> parts.first().take(2).uppercase()
                else -> email.take(2).uppercase()
            }
        }

    /**
     * Convert to a Map for Firestore document storage.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "uid" to uid,
            "email" to email,
            "fullName" to fullName,
            "phone" to phone,
            "role" to role.name,
            "profileImageUrl" to profileImageUrl,
            "isActive" to isActive,
            "isVerified" to isVerified,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "fcmToken" to fcmToken,
            "metadata" to metadata
        )
    }

    companion object {
        /**
         * Create a User from a Firestore document map.
         */
        fun fromMap(map: Map<String, Any?>): User {
            return User(
                uid = map["uid"] as? String ?: "",
                email = map["email"] as? String ?: "",
                fullName = map["fullName"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                role = UserRole.fromString(map["role"] as? String),
                profileImageUrl = map["profileImageUrl"] as? String,
                isActive = map["isActive"] as? Boolean ?: true,
                isVerified = map["isVerified"] as? Boolean ?: true,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                fcmToken = map["fcmToken"] as? String,
                metadata = (map["metadata"] as? Map<String, Any>) ?: emptyMap()
            )
        }

        /**
         * Firestore collection name for users.
         */
        const val COLLECTION_NAME = "users"
    }
}
