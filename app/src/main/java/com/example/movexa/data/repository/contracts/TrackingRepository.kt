package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import kotlinx.coroutines.flow.Flow

/**
 * Contract for TrackingRepository operations.
 * Handles real-time vehicle tracking via Firebase Realtime Database.
 */
interface TrackingRepository {

    // ── WRITE ───────────────────────────────────────────────────────────────
    suspend fun updateLocation(
        companyId: String,
        vehicleId: String,
        location: TrackingLocation
    ): ResultState<Unit>

    suspend fun removeLocation(
        companyId: String,
        vehicleId: String
    ): ResultState<Unit>

    suspend fun removeAllCompanyLocations(companyId: String): ResultState<Unit>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getLocation(
        companyId: String,
        vehicleId: String
    ): ResultState<TrackingLocation?>

    suspend fun getAllCompanyLocations(
        companyId: String
    ): ResultState<List<TrackingLocation>>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeVehicleLocation(
        companyId: String,
        vehicleId: String
    ): Flow<ResultState<TrackingLocation?>>

    fun observeAllCompanyLocations(
        companyId: String
    ): Flow<ResultState<List<TrackingLocation>>>

    fun observeMovingVehicles(
        companyId: String
    ): Flow<ResultState<List<TrackingLocation>>>
}
