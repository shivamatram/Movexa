package com.example.movexa

import android.app.Application
import com.example.movexa.data.local.PreferencesManager
import com.example.movexa.data.session.SessionManager

/**
 * Application class for Movexa Fleet Management System.
 *
 * Responsibilities:
 * - Global application initialization
 * - Firebase auto-initialization (via google-services.json & ContentProvider)
 * - Singleton access to application context
 * - Preferences initialization
 * - SessionManager initialization (DataStore)
 *
 * Firebase modules will auto-initialize through their ContentProviders.
 * No manual Firebase.initializeApp() call is needed because the
 * google-services plugin handles it automatically.
 */
class MoveXaApp : Application() {

    companion object {
        @Volatile
        private var instance: MoveXaApp? = null

        /**
         * Global access to the Application instance.
         */
        fun getInstance(): MoveXaApp =
            instance ?: throw IllegalStateException("MoveXaApp not initialized yet.")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        initializePreferences()
        initializeSession()
        initializeModules()
    }

    // ─── Private Initialization ─────────────────────────────────

    /**
     * Initialize SharedPreferences manager with application context.
     */
    private fun initializePreferences() {
        PreferencesManager.init(this)
    }

    /**
     * Initialize DataStore-backed SessionManager for auth persistence.
     */
    private fun initializeSession() {
        SessionManager.init(this)
    }

    /**
     * Placeholder for future module initialization.
     * Firebase auto-initializes via google-services.json.
     * Additional modules (crash reporting, analytics, etc.) can be added here.
     */
    private fun initializeModules() {
        // Firebase is auto-initialized via ContentProvider mechanism.
        // Future: Add Crashlytics, Analytics, Remote Config initialization here.
    }
}
