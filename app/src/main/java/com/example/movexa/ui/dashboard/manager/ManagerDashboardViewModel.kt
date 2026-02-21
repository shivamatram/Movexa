package com.example.movexa.ui.dashboard.manager

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ActivityLog
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.OperationsSummary
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.repository.contracts.ActivityLogRepository
import com.example.movexa.data.repository.contracts.AlertRepository
import com.example.movexa.data.repository.contracts.DashboardRepository
import com.example.movexa.data.repository.impl.ActivityLogRepositoryImpl
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import com.example.movexa.data.repository.impl.DashboardRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel for the Manager Dashboard screen.
 *
 * Manages three independent real-time data streams:
 * 1. Operations summary (stat cards + pending actions)
 * 2. Active alerts (quick alerts panel)
 * 3. Recent activity logs (live activity feed)
 *
 * Uses OperationsSummary (manager-specific) rather than DashboardSummary (admin-level).
 */
class ManagerDashboardViewModel : BaseViewModel() {

    // ── Repositories ────────────────────────────────────────────

    private val dashboardRepository: DashboardRepository = DashboardRepositoryImpl()
    private val alertRepository: AlertRepository = AlertRepositoryImpl()
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepositoryImpl()

    // ── State Flows ─────────────────────────────────────────────

    private val _operationsSummary = MutableStateFlow<ResultState<OperationsSummary?>>(ResultState.Loading)
    val operationsSummary: StateFlow<ResultState<OperationsSummary?>> = _operationsSummary.asStateFlow()

    private val _activeAlerts = MutableStateFlow<ResultState<List<Alert>>>(ResultState.Loading)
    val activeAlerts: StateFlow<ResultState<List<Alert>>> = _activeAlerts.asStateFlow()

    private val _recentActivity = MutableStateFlow<ResultState<List<ActivityLog>>>(ResultState.Loading)
    val recentActivity: StateFlow<ResultState<List<ActivityLog>>> = _recentActivity.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private var currentCompanyId: String? = null

    // ── Initialization ──────────────────────────────────────────

    /**
     * Start loading dashboard data.
     * Called from fragment's initViews() after session is available.
     */
    fun loadDashboard() {
        viewModelScope.launch {
            val sessionManager = SessionManager.getInstance()
            val userId = sessionManager.getCachedUserId() ?: return@launch

            // Use userId as company identifier for Firestore queries
            currentCompanyId = userId

            // Load user name for welcome header
            val user = sessionManager.currentUser.value
            _userName.value = user?.displayName ?: "Manager"

            // Start all three real-time listeners
            observeOperationsSummary(userId)
            observeActiveAlerts(userId)
            observeRecentActivity(userId)
        }
    }

    /**
     * Refresh all dashboard data (triggered by swipe-to-refresh or retry).
     */
    fun refreshDashboard() {
        val companyId = currentCompanyId ?: return
        _operationsSummary.value = ResultState.Loading
        _activeAlerts.value = ResultState.Loading
        _recentActivity.value = ResultState.Loading

        observeOperationsSummary(companyId)
        observeActiveAlerts(companyId)
        observeRecentActivity(companyId)
    }

    // ── Real-Time Observers ─────────────────────────────────────

    private fun observeOperationsSummary(companyId: String) {
        viewModelScope.launch {
            dashboardRepository.observeOperationsSummary(companyId)
                .catch { e ->
                    _operationsSummary.value = ResultState.Error(
                        message = e.message ?: "Failed to load operations summary",
                        exception = e
                    )
                }
                .collect { result ->
                    _operationsSummary.value = result
                }
        }
    }

    private fun observeActiveAlerts(companyId: String) {
        viewModelScope.launch {
            alertRepository.observeActiveAlerts(companyId)
                .catch { e ->
                    _activeAlerts.value = ResultState.Error(
                        message = e.message ?: "Failed to load alerts",
                        exception = e
                    )
                }
                .collect { result ->
                    _activeAlerts.value = result
                }
        }
    }

    private fun observeRecentActivity(companyId: String) {
        viewModelScope.launch {
            activityLogRepository.observeRecentLogs(companyId)
                .catch { e ->
                    _recentActivity.value = ResultState.Error(
                        message = e.message ?: "Failed to load activity",
                        exception = e
                    )
                }
                .collect { result ->
                    _recentActivity.value = result
                }
        }
    }

    // ── Computed Helpers ─────────────────────────────────────────

    /**
     * Get the total count of pending actions from the current operations summary.
     */
    fun getTotalPendingActions(): Int {
        val summary = (_operationsSummary.value as? ResultState.Success)?.data
        return summary?.totalPendingActions ?: 0
    }

    /**
     * Check if any data stream has an error.
     */
    fun hasAnyError(): Boolean {
        return _operationsSummary.value is ResultState.Error ||
                _activeAlerts.value is ResultState.Error ||
                _recentActivity.value is ResultState.Error
    }

    /**
     * Check if all data streams are still loading.
     */
    fun isAllLoading(): Boolean {
        return _operationsSummary.value is ResultState.Loading &&
                _activeAlerts.value is ResultState.Loading &&
                _recentActivity.value is ResultState.Loading
    }
}
