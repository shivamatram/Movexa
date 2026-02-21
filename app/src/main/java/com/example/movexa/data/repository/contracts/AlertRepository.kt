package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.AlertStatus
import com.example.movexa.data.model.enums.AlertType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Alert repository operations.
 * Handles alerting system, priority management, and resolution tracking.
 */
interface AlertRepository {

    // ── CREATE ──────────────────────────────────────────────────────────────
    suspend fun createAlert(alert: Alert): ResultState<String>
    suspend fun createBatchAlerts(alerts: List<Alert>): ResultState<List<String>>

    // ── READ ────────────────────────────────────────────────────────────────
    suspend fun getAlertById(alertId: String): ResultState<Alert?>
    suspend fun getAllAlerts(): ResultState<List<Alert>>
    suspend fun getAlertsByCompany(companyId: String): ResultState<List<Alert>>
    suspend fun getActiveAlerts(companyId: String): ResultState<List<Alert>>
    suspend fun getAlertsByType(
        companyId: String,
        type: AlertType
    ): ResultState<List<Alert>>
    suspend fun getAlertsByPriority(
        companyId: String,
        priority: AlertPriority
    ): ResultState<List<Alert>>
    suspend fun getAlertsByStatus(
        companyId: String,
        status: AlertStatus
    ): ResultState<List<Alert>>
    suspend fun getCriticalAlerts(companyId: String): ResultState<List<Alert>>
    suspend fun getAlertsByVehicle(vehicleId: String): ResultState<List<Alert>>
    suspend fun getAlertsByDriver(driverId: String): ResultState<List<Alert>>
    suspend fun getAlertsByTrip(tripId: String): ResultState<List<Alert>>
    suspend fun getAlertsByDateRange(
        companyId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): ResultState<List<Alert>>
    suspend fun getAlertsPaginated(
        companyId: String,
        pageSize: Int = 20,
        lastAlertId: String? = null
    ): ResultState<List<Alert>>
    suspend fun getActiveAlertCount(companyId: String): ResultState<Int>
    suspend fun getCriticalAlertCount(companyId: String): ResultState<Int>

    // ── UPDATE ──────────────────────────────────────────────────────────────
    suspend fun updateAlert(alert: Alert): ResultState<Unit>
    suspend fun acknowledgeAlert(alertId: String): ResultState<Unit>
    suspend fun resolveAlert(alertId: String, resolvedBy: String): ResultState<Unit>
    suspend fun dismissAlert(alertId: String): ResultState<Unit>
    suspend fun resolveMultipleAlerts(
        alertIds: List<String>,
        resolvedBy: String
    ): ResultState<Unit>

    // ── DELETE ──────────────────────────────────────────────────────────────
    suspend fun deleteAlert(alertId: String): ResultState<Unit>
    suspend fun deleteResolvedAlerts(companyId: String): ResultState<Unit>

    // ── REAL-TIME ───────────────────────────────────────────────────────────
    fun observeActiveAlerts(companyId: String): Flow<ResultState<List<Alert>>>
    fun observeCriticalAlerts(companyId: String): Flow<ResultState<List<Alert>>>
    fun observeVehicleAlerts(vehicleId: String): Flow<ResultState<List<Alert>>>
    fun observeDriverAlerts(driverId: String): Flow<ResultState<List<Alert>>>
}
