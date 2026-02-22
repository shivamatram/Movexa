package com.example.movexa.ui.dashboard.admin

import com.example.movexa.data.model.enums.TripStatus

/**
 * Manages filter state for the Admin Trips screen.
 *
 * Encapsulates:
 * - Status filter (single or null for all)
 * - Date range filter (start/end timestamps)
 * - Driver filter (driverId)
 * - Vehicle filter (vehicleId)
 * - Search query (tracking ID, driver name, vehicle number)
 *
 * Provides immutable filter snapshots for Firestore queries
 * and client-side search filtering.
 */
class TripFilterManager {

    // ── Current Filter State ────────────────────────────────────

    var statusFilter: TripStatus? = null
        private set

    var driverIdFilter: String? = null
        private set

    var vehicleIdFilter: String? = null
        private set

    var startDateFilter: Long? = null
        private set

    var endDateFilter: Long? = null
        private set

    var searchQuery: String = ""
        private set

    /**
     * Whether any filter (excluding search) is active.
     */
    val hasActiveFilters: Boolean
        get() = statusFilter != null ||
                driverIdFilter != null ||
                vehicleIdFilter != null ||
                startDateFilter != null ||
                endDateFilter != null

    /**
     * Whether search query is active.
     */
    val hasSearchQuery: Boolean
        get() = searchQuery.isNotBlank()

    /**
     * Total count of active filter dimensions.
     */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (statusFilter != null) count++
            if (driverIdFilter != null) count++
            if (vehicleIdFilter != null) count++
            if (startDateFilter != null || endDateFilter != null) count++
            return count
        }

    // ── Filter Setters ──────────────────────────────────────────

    fun setStatus(status: TripStatus?) {
        statusFilter = status
    }

    fun setDriverId(driverId: String?) {
        driverIdFilter = driverId?.takeIf { it.isNotBlank() }
    }

    fun setVehicleId(vehicleId: String?) {
        vehicleIdFilter = vehicleId?.takeIf { it.isNotBlank() }
    }

    fun setDateRange(startDate: Long?, endDate: Long?) {
        startDateFilter = startDate
        endDateFilter = endDate
    }

    fun setSearch(query: String) {
        searchQuery = query.trim()
    }

    // ── Batch Operations ────────────────────────────────────────

    /**
     * Clear all filters except search.
     */
    fun clearAllFilters() {
        statusFilter = null
        driverIdFilter = null
        vehicleIdFilter = null
        startDateFilter = null
        endDateFilter = null
    }

    /**
     * Clear everything including search.
     */
    fun clearAll() {
        clearAllFilters()
        searchQuery = ""
    }

    /**
     * Apply a filter snapshot (e.g., from saved preferences).
     */
    fun applySnapshot(snapshot: FilterSnapshot) {
        statusFilter = snapshot.status
        driverIdFilter = snapshot.driverId
        vehicleIdFilter = snapshot.vehicleId
        startDateFilter = snapshot.startDate
        endDateFilter = snapshot.endDate
    }

    /**
     * Create an immutable snapshot of current filters.
     */
    fun toSnapshot(): FilterSnapshot = FilterSnapshot(
        status = statusFilter,
        driverId = driverIdFilter,
        vehicleId = vehicleIdFilter,
        startDate = startDateFilter,
        endDate = endDateFilter,
        searchQuery = searchQuery
    )

    // ── Client-side Search Filter ───────────────────────────────

    /**
     * Filter a list of trips by the current search query.
     * Used for client-side filtering after Firestore returns results.
     *
     * Matches against: tracking ID, pickup address, drop address,
     * and resolved vehicle/driver names via resolvers.
     */
    fun filterBySearch(
        trips: List<com.example.movexa.data.model.Trip>,
        vehicleNameResolver: ((String) -> String?)? = null,
        driverNameResolver: ((String) -> String?)? = null
    ): List<com.example.movexa.data.model.Trip> {
        if (searchQuery.isBlank()) return trips

        val query = searchQuery.lowercase()
        return trips.filter { trip ->
            trip.trackingId.lowercase().contains(query) ||
            trip.pickupAddress.lowercase().contains(query) ||
            trip.dropAddress.lowercase().contains(query) ||
            trip.notes.lowercase().contains(query) ||
            (trip.vehicleId.isNotBlank() &&
                vehicleNameResolver?.invoke(trip.vehicleId)?.lowercase()?.contains(query) == true) ||
            (trip.driverId.isNotBlank() &&
                driverNameResolver?.invoke(trip.driverId)?.lowercase()?.contains(query) == true)
        }
    }

    // ── Filter Snapshot ─────────────────────────────────────────

    /**
     * Immutable representation of a filter configuration.
     */
    data class FilterSnapshot(
        val status: TripStatus? = null,
        val driverId: String? = null,
        val vehicleId: String? = null,
        val startDate: Long? = null,
        val endDate: Long? = null,
        val searchQuery: String = ""
    ) {
        val hasFilters: Boolean
            get() = status != null || driverId != null || vehicleId != null ||
                    startDate != null || endDate != null
    }
}
