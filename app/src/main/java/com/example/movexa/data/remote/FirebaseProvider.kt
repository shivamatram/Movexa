package com.example.movexa.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage

/**
 * Singleton wrapper for Firebase services.
 * Provides centralized access to all Firebase SDK instances.
 * Future Firebase modules plug in through this provider without refactoring.
 */
object FirebaseProvider {

    // ─── Firebase Auth ──────────────────────────────────────────

    /**
     * Firebase Authentication instance.
     */
    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    /**
     * Currently authenticated Firebase user, or null.
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Whether a user is currently authenticated.
     */
    val isAuthenticated: Boolean
        get() = currentUser != null

    /**
     * Current user's UID, or null if not authenticated.
     */
    val currentUserId: String?
        get() = currentUser?.uid

    // ─── Cloud Firestore ────────────────────────────────────────

    /**
     * Cloud Firestore instance for structured document storage.
     */
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    /**
     * Get a Firestore collection reference by name.
     */
    fun collection(name: String) = firestore.collection(name)

    /**
     * Get a Firestore document reference.
     */
    fun document(collection: String, documentId: String) =
        firestore.collection(collection).document(documentId)

    // ─── Realtime Database ──────────────────────────────────────

    /**
     * Firebase Realtime Database instance for real-time data sync.
     */
    val realtimeDb: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }

    /**
     * Get a Realtime Database reference by path.
     */
    fun databaseRef(path: String) = realtimeDb.getReference(path)

    // ─── Cloud Storage ──────────────────────────────────────────

    /**
     * Firebase Cloud Storage instance for file upload/download.
     */
    val storage: FirebaseStorage by lazy {
        FirebaseStorage.getInstance()
    }

    /**
     * Get a Storage reference by path.
     */
    fun storageRef(path: String) = storage.getReference(path)

    // ─── Cloud Messaging ────────────────────────────────────────

    /**
     * Firebase Cloud Messaging instance for push notifications.
     */
    val messaging: FirebaseMessaging by lazy {
        FirebaseMessaging.getInstance()
    }

    // ─── Utility ────────────────────────────────────────────────

    /**
     * Sign out the current user from all Firebase services.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Firestore collection paths (centralized constants).
     */
    object Collections {
        const val USERS = "users"
        const val VEHICLES = "vehicles"
        const val TRIPS = "trips"
        const val MAINTENANCE = "maintenance"
        const val REPORTS = "reports"
        const val NOTIFICATIONS = "notifications"
        const val SETTINGS = "settings"
        const val AUDIT_LOGS = "audit_logs"
    }

    /**
     * Realtime Database paths.
     */
    object RealtimePaths {
        const val VEHICLE_LOCATIONS = "vehicle_locations"
        const val DRIVER_STATUS = "driver_status"
        const val LIVE_TRIPS = "live_trips"
        const val ALERTS = "alerts"
    }

    /**
     * Storage paths.
     */
    object StoragePaths {
        const val PROFILE_IMAGES = "profile_images"
        const val VEHICLE_IMAGES = "vehicle_images"
        const val DOCUMENTS = "documents"
        const val REPORTS = "reports"
    }
}
