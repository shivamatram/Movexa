package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.FuelLog
import com.example.movexa.data.model.ResultState
import kotlinx.coroutines.flow.Flow

/**
 * Contract for FuelLog repository operations.
 * Handles fuel tracking, cost analysis, and mileage queries.
 */
interface FuelLogRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createFuelLog(fuelLog: FuelLog): ResultState<String>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getFuelLogById(fuelId: String): ResultState<FuelLog?>
    suspend fun getAllFuelLogs(): ResultState<List<FuelLog>>
    suspend fun getFuelLogsByVehicle(vehicleId: String): ResultState<List<FuelLog>>
    suspend fun getFuelLogsByDriver(driverId: String): ResultState<List<FuelLog>>
    suspend fun getFuelLogsByCompany(companyId: String): ResultState<List<FuelLog>>
    suspend fun getFuelLogsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<FuelLog>>
    suspend fun getVehicleFuelLogsByDateRange(
        vehicleId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<FuelLog>>
    suspend fun getLatestFuelLog(vehicleId: String): ResultState<FuelLog?>
    suspend fun getFuelLogsPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastFuelId: String? = null
    ): ResultState<List<FuelLog>>
    suspend fun getFuelLogCount(companyId: String): ResultState<Int>
    suspend fun getTotalFuelCost(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<Double>

    // ── UPDATE ──────────────────────────────────────────────────────────────
    suspend fun updateFuelLog(fuelLog: FuelLog): ResultState<Unit>
    suspend fun verifyFuelLog(fuelId: String, verifiedBy: String): ResultState<Unit>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteFuelLog(fuelId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeVehicleFuelLogs(vehicleId: String): Flow<ResultState<List<FuelLog>>>
    fun observeCompanyFuelLogs(companyId: String): Flow<ResultState<List<FuelLog>>>
}
