package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.PartHistory
import com.example.movexa.data.model.ResultState
import kotlinx.coroutines.flow.Flow

/**
 * Contract for PartHistory repository operations.
 * Handles parts lifecycle, warranty, and replacement tracking.
 */
interface PartHistoryRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createPartRecord(part: PartHistory): ResultState<String>
    suspend fun createBatchPartRecords(parts: List<PartHistory>): ResultState<List<String>>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getPartById(partId: String): ResultState<PartHistory?>
    suspend fun getAllParts(): ResultState<List<PartHistory>>
    suspend fun getPartsByVehicle(vehicleId: String): ResultState<List<PartHistory>>
    suspend fun getPartsByCompany(companyId: String): ResultState<List<PartHistory>>
    suspend fun getPartsByName(
        vehicleId: String,
        partName: String
    ): ResultState<List<PartHistory>>
    suspend fun getExpiredParts(
        vehicleId: String,
        currentOdometer: Long
    ): ResultState<List<PartHistory>>
    suspend fun getPartsNearExpiry(
        vehicleId: String,
        currentOdometer: Long,
        thresholdKm: Long = 1000
    ): ResultState<List<PartHistory>>
    suspend fun getPartsPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastPartId: String? = null
    ): ResultState<List<PartHistory>>
    suspend fun getPartCount(companyId: String): ResultState<Int>

    // ── UPDATE ──────────────────────────────────────────────────────────────
    suspend fun updatePartRecord(part: PartHistory): ResultState<Unit>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deletePartRecord(partId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeVehicleParts(vehicleId: String): Flow<ResultState<List<PartHistory>>>
}
