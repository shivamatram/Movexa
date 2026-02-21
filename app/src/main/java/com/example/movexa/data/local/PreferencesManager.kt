package com.example.movexa.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages local SharedPreferences storage for the application.
 * Handles persistent local data that doesn't require server sync.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // ─── String Operations ──────────────────────────────────────

    fun putString(key: String, value: String?) {
        prefs.edit { putString(key, value) }
    }

    fun getString(key: String, default: String? = null): String? {
        return prefs.getString(key, default)
    }

    // ─── Boolean Operations ─────────────────────────────────────

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return prefs.getBoolean(key, default)
    }

    // ─── Int Operations ─────────────────────────────────────────

    fun putInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    fun getInt(key: String, default: Int = 0): Int {
        return prefs.getInt(key, default)
    }

    // ─── Long Operations ────────────────────────────────────────

    fun putLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    fun getLong(key: String, default: Long = 0L): Long {
        return prefs.getLong(key, default)
    }

    // ─── Float Operations ───────────────────────────────────────

    fun putFloat(key: String, value: Float) {
        prefs.edit { putFloat(key, value) }
    }

    fun getFloat(key: String, default: Float = 0f): Float {
        return prefs.getFloat(key, default)
    }

    // ─── Remove / Clear ─────────────────────────────────────────

    fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun clear() {
        prefs.edit { clear() }
    }

    // ─── Application-Specific Keys ─────────────────────────────

    var isFirstLaunch: Boolean
        get() = getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = putBoolean(KEY_FIRST_LAUNCH, value)

    var isOnboardingComplete: Boolean
        get() = getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = putBoolean(KEY_ONBOARDING_COMPLETE, value)

    var lastSyncTimestamp: Long
        get() = getLong(KEY_LAST_SYNC, 0L)
        set(value) = putLong(KEY_LAST_SYNC, value)

    var selectedLanguage: String?
        get() = getString(KEY_LANGUAGE)
        set(value) = putString(KEY_LANGUAGE, value)

    var notificationsEnabled: Boolean
        get() = getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = putBoolean(KEY_NOTIFICATIONS, value)

    companion object {
        private const val PREFS_NAME = "movexa_preferences"

        // Key constants
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_LANGUAGE = "selected_language"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"

        @Volatile
        private var instance: PreferencesManager? = null

        /**
         * Initialize the singleton instance. Call from Application.onCreate().
         */
        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = PreferencesManager(context.applicationContext)
                    }
                }
            }
        }

        /**
         * Get the singleton instance.
         * @throws IllegalStateException if init() has not been called.
         */
        fun getInstance(): PreferencesManager =
            instance ?: throw IllegalStateException(
                "PreferencesManager not initialized. Call init(context) in Application.onCreate()."
            )
    }
}
