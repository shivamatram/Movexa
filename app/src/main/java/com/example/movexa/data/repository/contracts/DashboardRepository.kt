package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.DashboardSummary
import com.example.movexa.data.model.OperationsSummary
import com.example.movexa.data.model.ResultState
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Dashboard repository operations.
 * Provides real-time access to aggregated dashboard data
 * for admin and manager views.
 */
interface DashboardRepository {

    // ── Admin Dashboard ─────────────────────────────────────────

    /**
     * One-shot fetch of admin dashboard summary.
     */
    suspend fun getDashboardSummary(companyId: String): ResultState<DashboardSummary?>

    /**
     * Real-time observation of admin dashboard summary document.
     */
    fun observeDashboardSummary(companyId: String): Flow<ResultState<DashboardSummary?>>

    // ── Manager Dashboard ───────────────────────────────────────

    /**
     * One-shot fetch of operations summary for a manager.
     */
    suspend fun getOperationsSummary(companyId: String): ResultState<OperationsSummary?>

    /**
     * Real-time observation of manager operations summary document.
     */
    fun observeOperationsSummary(companyId: String): Flow<ResultState<OperationsSummary?>>
}
