package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.Repair
import com.example.movexa.data.model.ResultState
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Repair repository operations.
 * Handles repair logs, warranty tracking, and cost analysis.
 */
interface RepairRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createRepair(repair: Repair): ResultState<String>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getRepairById(repairId: String): ResultState<Repair?>
    suspend fun getAllRepairs(): ResultState<List<Repair>>
    suspend fun getRepairsByVehicle(vehicleId: String): ResultState<List<Repair>>
    suspend fun getRepairsByCompany(companyId: String): ResultState<List<Repair>>
    suspend fun getRepairsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<Repair>>
    suspend fun getRepairsUnderWarranty(vehicleId: String): ResultState<List<Repair>>
    suspend fun getRepairsPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastRepairId: String? = null
    ): ResultState<List<Repair>>
    suspend fun getRepairCount(companyId: String): ResultState<Int>
    suspend fun getTotalRepairCost(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<Double>

    // ── UPDATE ──────────────────────────────────────────────────────────────
    suspend fun updateRepair(repair: Repair): ResultState<Unit>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteRepair(repairId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeVehicleRepairs(vehicleId: String): Flow<ResultState<List<Repair>>>
    fun observeCompanyRepairs(companyId: String): Flow<ResultState<List<Repair>>>
}
