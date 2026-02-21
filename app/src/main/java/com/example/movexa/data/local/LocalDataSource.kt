package com.example.movexa.data.local

/**
 * Abstraction for local data source operations.
 * Future implementations may include Room database or encrypted storage.
 */
abstract class LocalDataSource {

    /**
     * Initialize the local data source.
     */
    abstract suspend fun initialize()

    /**
     * Clear all locally cached data.
     */
    abstract suspend fun clearAll()

    /**
     * Check if local data cache is stale and needs refresh.
     */
    abstract fun isDataStale(lastUpdated: Long, maxAgeMillis: Long): Boolean
}

/**
 * Default implementation of LocalDataSource using preferences.
 */
class DefaultLocalDataSource(
    private val preferencesManager: PreferencesManager
) : LocalDataSource() {

    override suspend fun initialize() {
        // Future: Initialize Room database, setup encryption keys, etc.
    }

    override suspend fun clearAll() {
        preferencesManager.clear()
    }

    override fun isDataStale(lastUpdated: Long, maxAgeMillis: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastUpdated) > maxAgeMillis
    }

    companion object {
        /** Default cache validity duration: 15 minutes */
        const val DEFAULT_CACHE_DURATION_MS = 15 * 60 * 1000L

        /** Extended cache validity: 1 hour */
        const val EXTENDED_CACHE_DURATION_MS = 60 * 60 * 1000L

        /** Short cache validity: 5 minutes */
        const val SHORT_CACHE_DURATION_MS = 5 * 60 * 1000L
    }
}
