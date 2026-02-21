package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.VerificationStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Driver repository operations.
 * Handles driver profiles, verification, and fleet management queries.
 */
interface DriverRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createDriver(driver: Driver): ResultState<String>
    suspend fun createDriverWithId(driverId: String, driver: Driver): ResultState<Unit>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getDriverById(driverId: String): ResultState<Driver?>
    suspend fun getDriverByUserId(userId: String): ResultState<Driver?>
    suspend fun getAllDrivers(): ResultState<List<Driver>>
    suspend fun getDriversByCompany(companyId: String): ResultState<List<Driver>>
    suspend fun getDriversByVerificationStatus(
        companyId: String,
        status: VerificationStatus
    ): ResultState<List<Driver>>
    suspend fun getActiveDrivers(companyId: String): ResultState<List<Driver>>
    suspend fun getBlockedDrivers(companyId: String): ResultState<List<Driver>>
    suspend fun getUnassignedDrivers(companyId: String): ResultState<List<Driver>>
    suspend fun getDriverByLicense(licenseNumber: String): ResultState<Driver?>
    suspend fun getDriversPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastDriverId: String? = null
    ): ResultState<List<Driver>>
    suspend fun getDriverCount(companyId: String): ResultState<Int>
    suspend fun licenseExists(licenseNumber: String): ResultState<Boolean>

    // ── UPDATE ──────────────────────────────────────────────────────────────
    suspend fun updateDriver(driver: Driver): ResultState<Unit>
    suspend fun updateVerificationStatus(
        driverId: String,
        status: VerificationStatus
    ): ResultState<Unit>
    suspend fun blockDriver(driverId: String): ResultState<Unit>
    suspend fun unblockDriver(driverId: String): ResultState<Unit>
    suspend fun assignVehicleToDriver(driverId: String, vehicleId: String): ResultState<Unit>
    suspend fun unassignVehicleFromDriver(driverId: String): ResultState<Unit>
    suspend fun updateRating(driverId: String, newRating: Double): ResultState<Unit>
    suspend fun incrementTripCount(driverId: String): ResultState<Unit>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteDriver(driverId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeDriver(driverId: String): Flow<ResultState<Driver?>>
    fun observeCompanyDrivers(companyId: String): Flow<ResultState<List<Driver>>>
    fun observeDriverByUserId(userId: String): Flow<ResultState<Driver?>>
}
