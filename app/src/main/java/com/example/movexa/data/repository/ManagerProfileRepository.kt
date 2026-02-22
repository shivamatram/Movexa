package com.example.movexa.data.repository

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.remote.FirebaseProvider
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Repository for manager profile operations.
 *
 * Handles:
 * - Fetching user profile from Firestore users/{uid}
 * - Updating profile fields (name, phone)
 * - Password change via Firebase Auth re-authentication
 * - Password reset email
 *
 * All operations are wrapped in [firebaseSafeCall] for consistent
 * ResultState error handling.
 */
class ManagerProfileRepository : BaseRepository() {

    private val auth = FirebaseProvider.auth
    private val firestore = FirebaseProvider.firestore

    // ─── Fetch Profile ──────────────────────────────────────────

    /**
     * Fetch the current user profile from Firestore.
     *
     * @param uid Firebase user UID
     * @return ResultState<User> — the user's complete profile data
     */
    suspend fun fetchProfile(uid: String): ResultState<User> {
        return firebaseSafeCall {
            val snapshot = firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .get()
                .await()

            if (!snapshot.exists()) {
                throw IllegalStateException("User profile not found.")
            }

            val data = snapshot.data
                ?: throw IllegalStateException("User profile data is empty.")

            User.fromMap(data)
        }
    }

    // ─── Update Profile ─────────────────────────────────────────

    /**
     * Update the user's profile fields in Firestore.
     *
     * Only updates allowed fields:
     * - fullName
     * - phone
     * - profileImageUrl (future)
     * - updatedAt timestamp
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

    // ─── Change Password ────────────────────────────────────────

    /**
     * Change the user's password using Firebase Auth.
     *
     * Requires re-authentication with current password first,
     * then updates to the new password.
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

    // ─── Send Password Reset ────────────────────────────────────

    /**
     * Send a password reset email to the user's email address.
     *
     * @param email Email address to send reset link to
     * @return ResultState<Unit>
     */
    suspend fun sendPasswordResetEmail(email: String): ResultState<Unit> {
        return firebaseSafeCall {
            auth.sendPasswordResetEmail(email).await()
        }
    }

    // ─── Sign Out ───────────────────────────────────────────────

    /**
     * Sign out the current user from Firebase Auth.
     */
    fun signOut() {
        FirebaseProvider.signOut()
    }

    /**
     * Get the current authenticated user's UID.
     */
    fun getCurrentUserId(): String? = FirebaseProvider.currentUserId

    /**
     * Whether a user is currently authenticated.
     */
    fun isAuthenticated(): Boolean = FirebaseProvider.isAuthenticated
}
