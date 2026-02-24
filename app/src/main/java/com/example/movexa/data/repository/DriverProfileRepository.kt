package com.example.movexa.data.repository

import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VerificationStatus
import com.example.movexa.data.remote.FirebaseProvider
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for driver profile operations.
 *
 * Handles:
 * - Fetching user profile from Firestore users/{uid}
 * - Fetching driver record from Firestore drivers/{driverId}
 * - Updating profile fields (name, phone, emergency contact, blood group)
 * - Document upload (license, ID proof) to Firebase Storage
 * - Verification status reset on document re-upload
 * - Password change via Firebase Auth re-authentication
 * - Real-time Firestore listeners for user & driver docs
 * - Fetching assigned vehicle details
 *
 * All operations are wrapped in [firebaseSafeCall] for consistent
 * ResultState error handling.
 */
class DriverProfileRepository : BaseRepository() {

    private val auth = FirebaseProvider.auth
    private val firestore = FirebaseProvider.firestore
    private val storage = FirebaseProvider.storage
    private val driverRepository = DriverRepositoryImpl()

    // ─── Fetch User Profile ─────────────────────────────────────

    /**
     * Fetch the current user profile from Firestore.
     *
     * @param uid Firebase user UID
     * @return ResultState<User> — the user's complete profile data
     */
    suspend fun fetchUserProfile(uid: String): ResultState<User> {
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

    // ─── Fetch Driver Profile ───────────────────────────────────

    /**
     * Fetch the driver record linked to a user ID.
     * Auto-creates a driver record if one doesn't exist yet.
     *
     * @param userId The user's UID to find corresponding driver record
     * @return ResultState<Driver> — the driver's complete data
     */
    suspend fun fetchDriverProfile(userId: String): ResultState<Driver> =
        driverRepository.getOrCreateDriverByUserId(userId)

    // ─── Fetch Assigned Vehicle ─────────────────────────────────

    /**
     * Fetch the vehicle assigned to this driver.
     *
     * @param vehicleId The assigned vehicle's ID
     * @return ResultState<Vehicle> — the vehicle details
     */
    suspend fun fetchAssignedVehicle(vehicleId: String): ResultState<Vehicle> {
        return firebaseSafeCall {
            val snapshot = firestore.collection(FirebaseProvider.Collections.VEHICLES)
                .document(vehicleId)
                .get()
                .await()

            if (!snapshot.exists()) {
                throw IllegalStateException("Vehicle not found.")
            }

            val data = snapshot.data
                ?: throw IllegalStateException("Vehicle data is empty.")

            Vehicle.fromMap(data)
        }
    }

    // ─── Update User Profile ────────────────────────────────────

    /**
     * Update the driver's user profile fields in Firestore.
     *
     * Only updates allowed fields:
     * - fullName
     * - phone
     * - updatedAt timestamp
     *
     * @param uid       Firebase user UID
     * @param fullName  Updated full name
     * @param phone     Updated phone number
     * @return ResultState<User> — the updated user profile
     */
    suspend fun updateUserProfile(
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

    // ─── Update Driver Details ──────────────────────────────────

    /**
     * Update driver-specific fields (emergency contact, blood group).
     *
     * @param driverId       The driver document ID
     * @param emergencyContact Updated emergency contact number
     * @param bloodGroup      Updated blood group
     * @return ResultState<Driver> — the updated driver record
     */
    suspend fun updateDriverDetails(
        driverId: String,
        emergencyContact: String,
        bloodGroup: String
    ): ResultState<Driver> {
        return firebaseSafeCall {
            val updateMap = mapOf(
                "emergencyContact" to emergencyContact,
                "bloodGroup" to bloodGroup,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(FirebaseProvider.Collections.DRIVERS)
                .document(driverId)
                .update(updateMap)
                .await()

            // Re-fetch
            val snapshot = firestore.collection(FirebaseProvider.Collections.DRIVERS)
                .document(driverId)
                .get()
                .await()

            val data = snapshot.data
                ?: throw IllegalStateException("Failed to read updated driver profile.")

            Driver.fromMap(data)
        }
    }

    // ─── Document Upload ────────────────────────────────────────

    /**
     * Upload a document image to Firebase Storage and update the driver record.
     *
     * Storage path: driver_documents/{driverId}/{documentType}.jpg
     * After upload, updates the corresponding URL field in the driver document
     * and resets verificationStatus to PENDING.
     *
     * @param driverId     The driver document ID
     * @param documentType "license" or "idproof"
     * @param imageBytes   Compressed image as byte array
     * @return ResultState<String> — the download URL of the uploaded document
     */
    suspend fun uploadDocument(
        driverId: String,
        documentType: String,
        imageBytes: ByteArray
    ): ResultState<String> {
        return firebaseSafeCall {
            // Determine storage path
            val storagePath = when (documentType) {
                "license" -> "${FirebaseProvider.StoragePaths.LICENSES}/$driverId/license_${System.currentTimeMillis()}.jpg"
                "idproof" -> "${FirebaseProvider.StoragePaths.ID_PROOFS}/$driverId/idproof_${System.currentTimeMillis()}.jpg"
                else -> "${FirebaseProvider.StoragePaths.DOCUMENTS}/$driverId/${documentType}_${System.currentTimeMillis()}.jpg"
            }

            // Upload to Firebase Storage
            val storageRef = storage.reference.child(storagePath)
            storageRef.putBytes(imageBytes).await()

            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Determine which field to update in the driver document
            val fieldName = when (documentType) {
                "license" -> "licenseUrl"
                "idproof" -> "idProofUrl"
                else -> throw IllegalArgumentException("Unknown document type: $documentType")
            }

            // Update driver document with URL and reset verification status
            val updateMap = mapOf(
                fieldName to downloadUrl,
                "verificationStatus" to VerificationStatus.PENDING.name,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection(FirebaseProvider.Collections.DRIVERS)
                .document(driverId)
                .update(updateMap)
                .await()

            downloadUrl
        }
    }

    // ─── Real-Time User Listener ────────────────────────────────

    /**
     * Observe real-time changes to the user document.
     *
     * @param uid Firebase user UID
     * @return Flow<ResultState<User>> — emits on every user document change
     */
    fun observeUserProfile(uid: String): Flow<ResultState<User>> = callbackFlow {
        val registration = firestore.collection(FirebaseProvider.Collections.USERS)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ResultState.Error(
                        message = error.message ?: "Failed to observe user profile"
                    ))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val data = snapshot.data ?: return@addSnapshotListener
                        val user = User.fromMap(data)
                        trySend(ResultState.Success(user))
                    } catch (e: Exception) {
                        trySend(ResultState.Error(
                            message = e.message ?: "Failed to parse user profile"
                        ))
                    }
                }
            }

        awaitClose { registration.remove() }
    }

    // ─── Real-Time Driver Listener ──────────────────────────────

    /**
     * Observe real-time changes to the driver document.
     *
     * @param driverId The driver document ID
     * @return Flow<ResultState<Driver>> — emits on every driver document change
     */
    fun observeDriverProfile(driverId: String): Flow<ResultState<Driver>> = callbackFlow {
        val registration = firestore.collection(FirebaseProvider.Collections.DRIVERS)
            .document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ResultState.Error(
                        message = error.message ?: "Failed to observe driver profile"
                    ))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val data = snapshot.data ?: return@addSnapshotListener
                        val driver = Driver.fromMap(data)
                        trySend(ResultState.Success(driver))
                    } catch (e: Exception) {
                        trySend(ResultState.Error(
                            message = e.message ?: "Failed to parse driver profile"
                        ))
                    }
                }
            }

        awaitClose { registration.remove() }
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
