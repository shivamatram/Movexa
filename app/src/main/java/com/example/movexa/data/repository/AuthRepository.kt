package com.example.movexa.data.repository

import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.UserRole
import com.example.movexa.data.model.enums.VerificationStatus
import com.example.movexa.data.remote.FirebaseProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.tasks.await

/**
 * Repository handling all authentication operations.
 *
 * Responsibilities:
 * - Firebase Auth: signup, login, password reset, sign out
 * - Firestore: create user document, fetch user profile
 * - Error mapping: Firebase exceptions → user-friendly messages
 *
 * All Firebase calls are wrapped in [firebaseSafeCall] from [BaseRepository]
 * for consistent error handling and ResultState wrapping.
 *
 * No Firebase SDK calls should exist outside the repository layer.
 */
class AuthRepository : BaseRepository() {

    private val auth = FirebaseProvider.auth
    private val firestore = FirebaseProvider.firestore

    // ─── Sign Up ────────────────────────────────────────────────

    /**
     * Create a new user account with Firebase Auth and store profile in Firestore.
     *
     * Flow:
     * 1. Create Firebase Auth user with email/password
     * 2. Build User data model with role/verified logic
     * 3. Write user document to Firestore users/{uid}
     * 4. Sign out immediately (never auto-login after signup)
     *
     * @param fullName User's full name
     * @param email User's email address
     * @param phone User's phone number
     * @param password User's chosen password
     * @param role Selected UserRole
     * @return ResultState<Unit> — success or error
     */
    suspend fun signUp(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        role: UserRole
    ): ResultState<Unit> {
        return firebaseSafeCall {
            // Step 1: Create Firebase Auth account
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: throw IllegalStateException("Account creation succeeded but user is null.")

            val uid = firebaseUser.uid

            // Step 2: Build user document
            // Driver accounts require verification; all other roles are auto-verified
            val isVerified = role != UserRole.DRIVER

            val user = User(
                uid = uid,
                email = email,
                fullName = fullName,
                phone = phone,
                role = role,
                isActive = true,
                isVerified = isVerified,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Step 3: Write to Firestore users/{uid}
            firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .set(user.toMap())
                .await()

            // Step 4: If driver role, create a Driver document in drivers collection
            if (role == UserRole.DRIVER) {
                val driverDocRef = firestore
                    .collection(FirebaseProvider.Collections.DRIVERS)
                    .document()
                val driverId = driverDocRef.id

                val driver = Driver(
                    driverId = driverId,
                    userId = uid,
                    verificationStatus = VerificationStatus.PENDING,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                driverDocRef.set(driver.toMap()).await()
            }

            // Step 5: Sign out — signup does NOT create session
            auth.signOut()
        }
    }

    // ─── Login ──────────────────────────────────────────────────

    /**
     * Authenticate user with email/password and fetch their Firestore profile.
     *
     * Flow:
     * 1. Sign in with Firebase Auth
     * 2. Fetch user document from Firestore users/{uid}
     * 3. Verify user exists in Firestore
     * 4. Check if account is active
     * 5. Return User model for session creation
     *
     * @param email User's email
     * @param password User's password
     * @return ResultState<User> — the authenticated user profile
     */
    suspend fun login(email: String, password: String): ResultState<User> {
        return firebaseSafeCall {
            // Step 1: Firebase Auth sign in
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: throw IllegalStateException("Sign in succeeded but user is null.")

            val uid = firebaseUser.uid

            // Step 2: Fetch Firestore user document
            val documentSnapshot = firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .get()
                .await()

            // Step 3: Verify document exists
            if (!documentSnapshot.exists()) {
                auth.signOut()
                throw IllegalStateException(
                    "Your account profile was not found. Please contact support."
                )
            }

            // Step 4: Parse user document
            val userData = documentSnapshot.data
                ?: throw IllegalStateException("User profile data is empty.")

            val user = User.fromMap(userData)

            // Step 5: Check active flag
            if (!user.isActive) {
                auth.signOut()
                throw IllegalStateException(
                    "Your account has been deactivated. Please contact an administrator."
                )
            }

            user
        }
    }

    // ─── Forgot Password ────────────────────────────────────────

    /**
     * Send a password reset email via Firebase Auth.
     *
     * @param email The email address to send the reset link to.
     * @return ResultState<Unit>
     */
    suspend fun sendPasswordResetEmail(email: String): ResultState<Unit> {
        return firebaseSafeCall {
            auth.sendPasswordResetEmail(email).await()
        }
    }

    // ─── Fetch User Profile ─────────────────────────────────────

    /**
     * Fetch a user profile from Firestore by UID.
     * Used for session restoration on app restart.
     *
     * @param uid Firebase user UID
     * @return ResultState<User>
     */
    suspend fun fetchUserProfile(uid: String): ResultState<User> {
        return firebaseSafeCall {
            val documentSnapshot = firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .get()
                .await()

            if (!documentSnapshot.exists()) {
                throw IllegalStateException("User profile not found.")
            }

            val userData = documentSnapshot.data
                ?: throw IllegalStateException("User profile data is empty.")

            User.fromMap(userData)
        }
    }

    // ─── Sign Out ───────────────────────────────────────────────

    /**
     * Sign out the current user from Firebase Auth.
     */
    fun signOut() {
        FirebaseProvider.signOut()
    }

    // ─── Check Auth State ───────────────────────────────────────

    /**
     * Whether a user is currently authenticated with Firebase.
     */
    fun isAuthenticated(): Boolean = FirebaseProvider.isAuthenticated

    /**
     * Get the current Firebase user UID, or null.
     */
    fun getCurrentUserId(): String? = FirebaseProvider.currentUserId

    // ─── Error Mapping ──────────────────────────────────────────

    /**
     * Override base error parsing with Firebase-specific exception handling.
     * Maps FirebaseAuthException subtypes to user-friendly messages.
     */
    override fun parseErrorMessage(exception: Throwable): String {
        return when (exception) {
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Please use at least 6 characters with a mix of letters and numbers."

            is FirebaseAuthInvalidCredentialsException ->
                "Invalid email or password. Please check your credentials and try again."

            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists. Please sign in or use a different email."

            is FirebaseAuthInvalidUserException ->
                when (exception.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> "No account found with this email address."
                    "ERROR_USER_DISABLED" -> "This account has been disabled by an administrator."
                    else -> "Account error. Please contact support."
                }

            is FirebaseAuthException ->
                mapFirebaseAuthErrorCode(exception.errorCode)

            else -> super.parseErrorMessage(exception)
        }
    }

    /**
     * Map Firebase Auth error codes to human-readable messages.
     */
    private fun mapFirebaseAuthErrorCode(errorCode: String): String {
        return when (errorCode) {
            "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
            "ERROR_WRONG_PASSWORD" -> "Incorrect password. Please try again."
            "ERROR_USER_NOT_FOUND" -> "No account found with this email address."
            "ERROR_USER_DISABLED" -> "This account has been disabled."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password sign-in is not enabled."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "This email is already registered."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Please check your internet connection."
            "ERROR_REQUIRES_RECENT_LOGIN" -> "Please sign in again to continue."
            else -> "Authentication error ($errorCode). Please try again."
        }
    }

    // ─── Update Verification Status ─────────────────────────────

    /**
     * Update the verified status of a user in Firestore.
     * Used by admin/manager to verify driver accounts.
     *
     * @param uid The user's UID
     * @param verified The new verification status
     * @return ResultState<Unit>
     */
    suspend fun updateVerificationStatus(
        uid: String,
        verified: Boolean
    ): ResultState<Unit> {
        return firebaseSafeCall {
            firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .update(
                    mapOf(
                        "isVerified" to verified,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        }
    }

    /**
     * Update user's active status in Firestore.
     *
     * @param uid The user's UID
     * @param active The new active status
     * @return ResultState<Unit>
     */
    suspend fun updateActiveStatus(
        uid: String,
        active: Boolean
    ): ResultState<Unit> {
        return firebaseSafeCall {
            firestore.collection(FirebaseProvider.Collections.USERS)
                .document(uid)
                .update(
                    mapOf(
                        "isActive" to active,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        }
    }
}
