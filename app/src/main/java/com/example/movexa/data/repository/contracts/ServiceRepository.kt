package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.data.model.enums.ServiceType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for ServiceRecord repository operations.
 * Handles service scheduling, tracking, and maintenance history.
 */
interface ServiceRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createServiceRecord(service: ServiceRecord): ResultState<String>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getServiceById(serviceId: String): ResultState<ServiceRecord?>
    suspend fun getAllServices(): ResultState<List<ServiceRecord>>
    suspend fun getServicesByVehicle(vehicleId: String): ResultState<List<ServiceRecord>>
    suspend fun getServicesByCompany(companyId: String): ResultState<List<ServiceRecord>>
    suspend fun getServicesByType(
        companyId: String,
        type: ServiceType
    ): ResultState<List<ServiceRecord>>
    suspend fun getPendingServices(companyId: String): ResultState<List<ServiceRecord>>
    suspend fun getCompletedServices(companyId: String): ResultState<List<ServiceRecord>>
    suspend fun getOverdueServices(
        companyId: String,
        currentOdometerMap: Map<String, Long>
    ): ResultState<List<ServiceRecord>>
    suspend fun getLastServiceByType(
        vehicleId: String,
        type: ServiceType
    ): ResultState<ServiceRecord?>
    suspend fun getServicesByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<ServiceRecord>>
    suspend fun getServicesPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastServiceId: String? = null
    ): ResultState<List<ServiceRecord>>
    suspend fun getServiceCount(companyId: String): ResultState<Int>
    suspend fun getTotalServiceCost(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<Double>

    // ── UPDATE ──────────────────────────────────────────────────────────────
    suspend fun updateServiceRecord(service: ServiceRecord): ResultState<Unit>
    suspend fun markServiceCompleted(serviceId: String): ResultState<Unit>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteServiceRecord(serviceId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeVehicleServices(vehicleId: String): Flow<ResultState<List<ServiceRecord>>>
    fun observeCompanyServices(companyId: String): Flow<ResultState<List<ServiceRecord>>>
}
