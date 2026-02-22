package com.example.movexa.ui.dashboard.admin

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Pagination controller for admin trip lists.
 *
 * Manages cursor-based pagination state for Firestore queries.
 * Supports separate pagination cursors for each tab
 * (Completed, Cancelled, All, Filtered).
 *
 * Features:
 * - Cursor management via DocumentSnapshot references
 * - Load-more detection (hasMore flag)
 * - Accumulated trip list management
 * - Reset/refresh support
 * - Thread-safe state updates
 */
class TripPaginationController {

    // ── Configuration ───────────────────────────────────────────

    /** Default items per page. */
    var pageSize: Int = 20
        private set

    // ── Pagination State ────────────────────────────────────────

    /** Last document snapshot for cursor-based pagination. */
    @Volatile
    var lastDocument: DocumentSnapshot? = null
        private set

    /** Whether more pages are available. */
    @Volatile
    var hasMore: Boolean = true
        private set

    /** Whether a page load is in progress. */
    @Volatile
    var isLoading: Boolean = false
        private set

    /** Whether the first page has been loaded. */
    @Volatile
    var isInitialized: Boolean = false
        private set

    /** Accumulated trips across all loaded pages. */
    private val _accumulatedTrips = mutableListOf<Trip>()
    val accumulatedTrips: List<Trip>
        get() = _accumulatedTrips.toList()

    /** Total loaded count. */
    val loadedCount: Int
        get() = _accumulatedTrips.size

    // ── State Management ────────────────────────────────────────

    /**
     * Mark loading started. Returns false if already loading.
     */
    @Synchronized
    fun beginLoading(): Boolean {
        if (isLoading) return false
        isLoading = true
        return true
    }

    /**
     * Process a successful page result.
     *
     * @param trips Newly loaded trips
     * @param lastDoc Last document snapshot for next page cursor
     */
    @Synchronized
    fun onPageLoaded(trips: List<Trip>, lastDoc: DocumentSnapshot?) {
        isLoading = false
        isInitialized = true
        lastDocument = lastDoc

        // If we got fewer than pageSize, no more pages
        hasMore = trips.size >= pageSize

        // Deduplicate and append
        val existingIds = _accumulatedTrips.map { it.tripId }.toSet()
        val newTrips = trips.filter { it.tripId !in existingIds }
        _accumulatedTrips.addAll(newTrips)
    }

    /**
     * Handle a page load error.
     */
    @Synchronized
    fun onLoadError() {
        isLoading = false
    }

    /**
     * Reset pagination state for a fresh start.
     * Call when filters change or user pulls to refresh.
     */
    @Synchronized
    fun reset() {
        lastDocument = null
        hasMore = true
        isLoading = false
        isInitialized = false
        _accumulatedTrips.clear()
    }

    /**
     * Update a trip in the accumulated list (e.g., after status change).
     */
    @Synchronized
    fun updateTrip(updatedTrip: Trip) {
        val index = _accumulatedTrips.indexOfFirst { it.tripId == updatedTrip.tripId }
        if (index >= 0) {
            _accumulatedTrips[index] = updatedTrip
        }
    }

    /**
     * Remove a trip from the accumulated list.
     */
    @Synchronized
    fun removeTrip(tripId: String) {
        _accumulatedTrips.removeAll { it.tripId == tripId }
    }

    /**
     * Replace the entire accumulated list (e.g., from real-time updates).
     */
    @Synchronized
    fun replaceAll(trips: List<Trip>) {
        _accumulatedTrips.clear()
        _accumulatedTrips.addAll(trips)
        isInitialized = true
    }

    /**
     * Check if load-more should be triggered.
     *
     * @param lastVisiblePosition Last visible item position in RecyclerView
     * @param totalItemCount Total items in adapter
     * @param threshold Items from end to trigger load (default 5)
     */
    fun shouldLoadMore(
        lastVisiblePosition: Int,
        totalItemCount: Int,
        threshold: Int = 5
    ): Boolean {
        return !isLoading &&
                hasMore &&
                isInitialized &&
                totalItemCount > 0 &&
                lastVisiblePosition >= totalItemCount - threshold
    }

    /**
     * Configure page size.
     */
    fun setPageSize(size: Int) {
        pageSize = size.coerceIn(10, 100)
    }
}
