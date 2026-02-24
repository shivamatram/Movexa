package com.example.movexa.data.repository

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.remote.FirebaseProvider
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Repository for Admin profile and company settings operations.
 *
 * Handles:
 * - Fetching admin profile from Firestore users/{uid}
 * - Updating admin profile fields (name, phone)
 * - Password change via Firebase Auth re-authentication
 * - Company settings CRUD (settings/{companyId})
 * - Audit log fetching (audit_logs collection)
 *
 * All operations wrapped in [firebaseSafeCall] for consistent
 * ResultState error handling.
 */
class AdminProfileRepository : BaseRepository() {

    private val auth = FirebaseProvider.auth
    private val firestore = FirebaseProvider.firestore

    // ═══════════════════════════════════════════════════════════
    // PROFILE OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch the admin user profile from Firestore.
     *
     * @param uid Firebase user UID
     * @return ResultState<User> — the admin's complete profile data
     */
    suspend fun fetchProfile(uid: String): ResultState<User> {
        return firebaseSafeCall {
            val snapshot = firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .get()
                .await()

            if (!snapshot.exists()) {
                throw IllegalStateException("Admin profile not found.")
            }

            val data = snapshot.data
                ?: throw IllegalStateException("Admin profile data is empty.")

            User.fromMap(data)
        }
    }

    /**
     * Update the admin's profile fields in Firestore.
     *
     * @param uid       Firebase user UID
     * @param fullName  Updated full name
     * @param phone     Updated phone number
     * @return ResultState<User> — the updated user profile
     */
    suspend fun updateProfile(
        uid: String,
        fullName: String,
        phone: String
    ): ResultState<User> {
        return firebaseSafeCall {
            val updateMap = mapOf(
                "fullName" to fullName,
                "phone" to phone,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .update(updateMap)
                .await()

            // Re-fetch to get consistent state
            val snapshot = firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .get()
                .await()

            val data = snapshot.data
                ?: throw IllegalStateException("Failed to read updated profile.")

            User.fromMap(data)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PASSWORD OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Change the admin's password using Firebase Auth.
     *
     * Re-authenticates with current password, then updates to new password.
     *
     * @param currentPassword User's current password for verification
     * @param newPassword     The new password to set
     * @return ResultState<Unit>
     */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): ResultState<Unit> {
        return firebaseSafeCall {
            val user = auth.currentUser
                ?: throw IllegalStateException("No authenticated user found.")

            val email = user.email
                ?: throw IllegalStateException("User email not available.")

            // Step 1: Re-authenticate with current password
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()

            // Step 2: Update to new password
            user.updatePassword(newPassword).await()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // COMPANY SETTINGS
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch company settings from Firestore.
     *
     * @param companyId Admin's userId (serves as company identifier)
     * @return ResultState<Map<String, Any>> — company settings map
     */
    suspend fun fetchCompanySettings(companyId: String): ResultState<Map<String, Any>> {
        return firebaseSafeCall {
            val snapshot = firestore.collection(FirebaseProvider.Collections.SETTINGS)
                .document(companyId)
                .get()
                .await()

            if (snapshot.exists()) {
                snapshot.data ?: emptyMap()
            } else {
                // Return default settings if document doesn't exist
                emptyMap()
            }
        }
    }

    /**
     * Update company settings in Firestore.
     *
     * @param companyId Admin's userId
     * @param settings  Map of settings to update
     * @return ResultState<Unit>
     */
    suspend fun updateCompanySettings(
        companyId: String,
        settings: Map<String, Any>
    ): ResultState<Unit> {
        return firebaseSafeCall {
            val updateMap = settings.toMutableMap().apply {
                put("updatedAt", System.currentTimeMillis())
                put("updatedBy", companyId)
            }

            firestore.collection(FirebaseProvider.Collections.SETTINGS)
                .document(companyId)
                .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                .await()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SYSTEM TOGGLES
    // ═══════════════════════════════════════════════════════════

    /**
     * Update a single system toggle setting.
     *
     * @param companyId Admin's userId
     * @param key       Setting key (e.g. "maintenanceMode", "pushNotifications")
     * @param value     Boolean value
     * @return ResultState<Unit>
     */
    suspend fun updateSystemToggle(
        companyId: String,
        key: String,
        value: Boolean
    ): ResultState<Unit> {
        return firebaseSafeCall {
            firestore.collection(FirebaseProvider.Collections.SETTINGS)
                .document(companyId)
                .set(
                    mapOf(key to value, "updatedAt" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // AUDIT LOGS
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch recent audit logs for the admin.
     *
     * @param companyId Admin's userId
     * @param limit     Max number of logs to fetch
     * @return ResultState<List<Map<String, Any>>> — list of audit log entries
     */
    suspend fun fetchAuditLogs(
        companyId: String,
        limit: Int = 5
    ): ResultState<List<Map<String, Any>>> {
        return firebaseSafeCall {
            val snapshot = firestore.collection(FirebaseProvider.Collections.AUDIT_LOGS)
                .whereEqualTo("companyId", companyId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.apply {
                    put("id", doc.id)
                }
            }
        }
    }

    /**
     * Write an audit log entry.
     *
     * @param companyId Admin's userId
     * @param action    Action description (e.g. "profile_update", "settings_change")
     * @param details   Additional details about the action
     */
    suspend fun writeAuditLog(
        companyId: String,
        action: String,
        details: String
    ): ResultState<Unit> {
        return firebaseSafeCall {
            val logEntry = mapOf(
                "companyId" to companyId,
                "action" to action,
                "details" to details,
                "timestamp" to System.currentTimeMillis(),
                "performedBy" to (auth.currentUser?.email ?: "unknown")
            )

            firestore.collection(FirebaseProvider.Collections.AUDIT_LOGS)
                .add(logEntry)
                .await()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // AUTH UTILITIES
    // ═══════════════════════════════════════════════════════════

    /**
     * Sign out the current user from Firebase Auth.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Get the current authenticated user's UID.
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
}
