package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.TripStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Trip repository operations.
 * Handles trip lifecycle, status transitions, and route-based queries.
 */
interface TripRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createTrip(trip: Trip): ResultState<String>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getTripById(tripId: String): ResultState<Trip?>
    suspend fun getAllTrips(): ResultState<List<Trip>>
    suspend fun getTripsByCompany(companyId: String): ResultState<List<Trip>>
    suspend fun getTripsByDriver(driverId: String): ResultState<List<Trip>>
    suspend fun getTripsByVehicle(vehicleId: String): ResultState<List<Trip>>
    suspend fun getTripsByStatus(
        companyId: String,
        status: TripStatus
    ): ResultState<List<Trip>>
    suspend fun getActiveTrips(companyId: String): ResultState<List<Trip>>
    suspend fun getCompletedTrips(companyId: String): ResultState<List<Trip>>
    suspend fun getTripsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<Trip>>
    suspend fun getDriverActiveTrip(driverId: String): ResultState<Trip?>
    suspend fun getVehicleActiveTrip(vehicleId: String): ResultState<Trip?>
    suspend fun getTripsPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastTripId: String? = null
    ): ResultState<List<Trip>>
    suspend fun getTripCount(companyId: String): ResultState<Int>
    suspend fun getActiveTripCount(companyId: String): ResultState<Int>

    // ── UPDATE ──────────────────────────────────────────────────────────────
    suspend fun updateTrip(trip: Trip): ResultState<Unit>
    suspend fun updateTripStatus(tripId: String, status: TripStatus): ResultState<Unit>

    /**
     * Update arbitrary fields on a trip document. Useful for custom status transitions
     * that must also modify driverId, vehicleId, etc., to satisfy security rules.
     */
    suspend fun updateTripFields(tripId: String, fields: Map<String, Any?>): ResultState<Unit>
    suspend fun assignDriver(tripId: String, driverId: String): ResultState<Unit>
    suspend fun startTrip(tripId: String): ResultState<Unit>
    suspend fun completeTrip(
        tripId: String,
        distance: Double,
        duration: Long
    ): ResultState<Unit>
    suspend fun cancelTrip(tripId: String, reason: String?): ResultState<Unit>
    suspend fun updateTrackingId(tripId: String, trackingId: String): ResultState<Unit>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteTrip(tripId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeTrip(tripId: String): Flow<ResultState<Trip?>>
    fun observeActiveTrips(companyId: String): Flow<ResultState<List<Trip>>>
    fun observeDriverTrips(driverId: String): Flow<ResultState<List<Trip>>>
}
