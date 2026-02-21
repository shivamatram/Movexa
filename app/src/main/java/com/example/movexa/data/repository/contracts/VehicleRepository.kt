package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.model.enums.VehicleType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Vehicle repository operations.
 * Handles all vehicle CRUD, fleet queries, and real-time observation.
 */
interface VehicleRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createVehicle(vehicle: Vehicle): ResultState<String>
    suspend fun createVehicleWithId(vehicleId: String, vehicle: Vehicle): ResultState<Unit>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getVehicleById(vehicleId: String): ResultState<Vehicle?>
    suspend fun getAllVehicles(): ResultState<List<Vehicle>>
    suspend fun getVehiclesByCompany(companyId: String): ResultState<List<Vehicle>>
    suspend fun getVehiclesByStatus(status: VehicleStatus): ResultState<List<Vehicle>>
    suspend fun getVehiclesByType(type: VehicleType): ResultState<List<Vehicle>>
    suspend fun getVehiclesByDriver(driverId: String): ResultState<List<Vehicle>>
    suspend fun getVehicleByNumber(vehicleNumber: String): ResultState<Vehicle?>
    suspend fun getAvailableVehicles(companyId: String): ResultState<List<Vehicle>>
    suspend fun getVehiclesNeedingService(companyId: String): ResultState<List<Vehicle>>
    suspend fun getVehiclesPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastVehicleId: String? = null
    ): ResultState<List<Vehicle>>
    suspend fun getVehicleCount(companyId: String): ResultState<Int>
    suspend fun vehicleNumberExists(vehicleNumber: String): ResultState<Boolean>

    // ── UPDATE ──────────────────────────────────────────────────────────────
    suspend fun updateVehicle(vehicle: Vehicle): ResultState<Unit>
    suspend fun updateVehicleStatus(vehicleId: String, status: VehicleStatus): ResultState<Unit>
    suspend fun assignDriverToVehicle(vehicleId: String, driverId: String): ResultState<Unit>
    suspend fun unassignDriverFromVehicle(vehicleId: String): ResultState<Unit>
    suspend fun updateOdometer(vehicleId: String, odometer: Long): ResultState<Unit>
    suspend fun updateDocumentValidity(vehicleId: String, valid: Boolean): ResultState<Unit>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteVehicle(vehicleId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeVehicle(vehicleId: String): Flow<ResultState<Vehicle?>>
    fun observeFleet(companyId: String): Flow<ResultState<List<Vehicle>>>
    fun observeAvailableVehicles(companyId: String): Flow<ResultState<List<Vehicle>>>
}
