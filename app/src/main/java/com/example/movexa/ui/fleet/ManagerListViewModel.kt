package com.example.movexa.ui.fleet

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.UserRole
import com.example.movexa.data.remote.FirebaseProvider
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for the Managers tab in Fleet Management (Admin only).
 *
 * Since there is no dedicated ManagerRepository, this ViewModel queries
 * the Firestore users collection directly for users with MANAGER role.
 *
 * Responsibilities:
 * - Fetch managers under the admin's company
 * - Client-side search filtering
 * - Periodic refresh
 *
 * Note: Managers are User objects with role == MANAGER.
 * In the current data model, the admin's userId serves as the companyId.
 * Managers are linked by being in the same Firestore scope, but since
 * User.kt has no companyId field, we query all users with MANAGER role
 * and filter by metadata or just display all managers visible to this admin.
 */
class ManagerListViewModel : BaseViewModel() {

    // ── State Flows ─────────────────────────────────────────────

    /** Raw manager list from Firestore. */
    private val _allManagers = MutableStateFlow<ResultState<List<User>>>(ResultState.Loading)

    /** Filtered managers displayed in the UI. */
    private val _managers = MutableStateFlow<ResultState<List<User>>>(ResultState.Loading)
    val managers: StateFlow<ResultState<List<User>>> = _managers.asStateFlow()

    /** Current search query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Manager count after filtering. */
    private val _managerCount = MutableStateFlow(0)
    val managerCount: StateFlow<Int> = _managerCount.asStateFlow()

    private var currentCompanyId: String? = null

    // ── Initialization ──────────────────────────────────────────

    /**
     * Start loading managers. Call from fragment's initViews().
     */
    fun loadManagers() {
        viewModelScope.launch {
            val companyId = SessionManager.getInstance().getCachedUserId()
            if (companyId.isNullOrBlank()) {
                _managers.value = ResultState.Error("No company ID found. Please log in again.")
                return@launch
            }
            currentCompanyId = companyId
            fetchManagers()
        }
    }

    /**
     * Refresh manager data.
     */
    fun refreshManagers() {
        _allManagers.value = ResultState.Loading
        _managers.value = ResultState.Loading
        fetchManagers()
    }

    // ── Data Fetching ───────────────────────────────────────────

    /**
     * Fetch all users with MANAGER role from Firestore.
     *
     * Query: users collection where role == "MANAGER"
     * Since User model doesn't have companyId, we fetch all MANAGER users.
     * In a production system, this would be scoped by company.
     */
    private fun fetchManagers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = FirebaseProvider.firestore
                    .collection(User.COLLECTION_NAME)
                    .whereEqualTo("role", UserRole.MANAGER.name)
                    .get()
                    .await()

                val managers = snapshot.documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        User.fromMap(data)
                    } catch (e: Exception) {
                        null
                    }
                }

                _allManagers.value = ResultState.Success(managers)
                applyFilters()
            } catch (e: Exception) {
                _allManagers.value = ResultState.Error(
                    message = e.message ?: "Failed to load managers",
                    exception = e
                )
                _managers.value = _allManagers.value
            }
        }
    }

    // ── Filtering ───────────────────────────────────────────────

    /**
     * Set the search query.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    /**
     * Apply search filter to the raw manager list.
     */
    private fun applyFilters() {
        val currentState = _allManagers.value
        if (currentState !is ResultState.Success) {
            _managers.value = currentState
            return
        }

        val allManagers = currentState.data
        val searchQuery = _searchQuery.value.trim().lowercase()

        val filtered = if (searchQuery.isBlank()) {
            allManagers
        } else {
            allManagers.filter { user ->
                user.displayName.lowercase().contains(searchQuery) ||
                        user.email.lowercase().contains(searchQuery) ||
                        user.phone.lowercase().contains(searchQuery)
            }
        }

        _managerCount.value = filtered.size
        _managers.value = ResultState.Success(filtered)
    }
}
