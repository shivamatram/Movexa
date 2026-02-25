package com.example.movexa.ui.fleet

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ManagerCreationState
import com.example.movexa.data.model.ManagerListState
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.UserRole
import com.example.movexa.data.repository.AdminManagerRepository
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import com.example.movexa.utils.RoleGuard
import com.example.movexa.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Admin Manager Management module.
 *
 * Manages:
 * - Manager list loading, filtering, and refresh
 * - Manager creation with full validation
 * - Manager deactivation/reactivation
 * - Role-based access control enforcement
 *
 * Architecture:
 * - Uses [AdminManagerRepository] for all data operations
 * - Exposes [ManagerListState] and [ManagerCreationState] for UI consumption
 * - All Firebase calls are isolated in the repository layer
 * - Enforces ADMIN-only access via [RoleGuard]
 */
class AdminManagerViewModel : BaseViewModel() {

    // ── Repository ──────────────────────────────────────────────
    private val repository = AdminManagerRepository()
    private val sessionManager = SessionManager.getInstance()

    // ── Manager List State ──────────────────────────────────────

    /** Raw list of all managers from Firestore. */
    private val _allManagers = mutableListOf<User>()

    /** Filtered/displayed manager list state. */
    private val _managerListState = MutableStateFlow<ManagerListState>(ManagerListState.Loading)
    val managerListState: StateFlow<ManagerListState> = _managerListState.asStateFlow()

    /** Filtered manager list for direct observation (backward compat). */
    private val _managers = MutableStateFlow<ResultState<List<User>>>(ResultState.Loading)
    val managers: StateFlow<ResultState<List<User>>> = _managers.asStateFlow()

    /** Current search query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Manager count after filtering. */
    private val _managerCount = MutableStateFlow(0)
    val managerCount: StateFlow<Int> = _managerCount.asStateFlow()

    // ── Manager Creation State ──────────────────────────────────

    private val _creationState = MutableStateFlow<ManagerCreationState>(ManagerCreationState.Idle)
    val creationState: StateFlow<ManagerCreationState> = _creationState.asStateFlow()

    // ── Deactivation State ──────────────────────────────────────

    private val _deactivationState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val deactivationState: StateFlow<ResultState<Unit>> = _deactivationState.asStateFlow()

    // ─── Manager List Operations ────────────────────────────────

    /**
     * Load managers from Firestore. Call from fragment's initViews().
     */
    fun loadManagers() {
        viewModelScope.launch(Dispatchers.IO) {
            _managerListState.value = ManagerListState.Loading
            _managers.value = ResultState.Loading

            val companyId = sessionManager.getCachedCompanyId()
            if (companyId.isNullOrBlank()) {
                val error = "No company ID found. Please re-login."
                _managerListState.value = ManagerListState.Error(error)
                _managers.value = ResultState.Error(error)
                return@launch
            }

            fetchManagersInternal()
        }
    }

    /**
     * Refresh manager data (pull-to-refresh).
     */
    fun refreshManagers() {
        viewModelScope.launch(Dispatchers.IO) {
            _managerListState.value = ManagerListState.Refreshing
            fetchManagersInternal()
        }
    }

    /**
     * Internal fetch — queries repository and updates state.
     */
    private suspend fun fetchManagersInternal() {
        // Try company-scoped query first, fall back to all managers
        val result = repository.getManagers()

        when (result) {
            is ResultState.Success -> {
                _allManagers.clear()
                _allManagers.addAll(result.data)

                // If no company-scoped results, try all managers (legacy data)
                if (_allManagers.isEmpty()) {
                    val allResult = repository.getAllManagers()
                    if (allResult is ResultState.Success) {
                        _allManagers.clear()
                        _allManagers.addAll(allResult.data)
                    }
                }

                applyFilters()
            }

            is ResultState.Error -> {
                _managerListState.value = ManagerListState.Error(result.message)
                _managers.value = result
            }

            else -> { /* Loading/Idle — no action */ }
        }
    }

    // ─── Search / Filter ────────────────────────────────────────

    /**
     * Set the search query and re-filter.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    /**
     * Apply current search filter to the manager list.
     */
    private fun applyFilters() {
        val query = _searchQuery.value.trim().lowercase()

        val filtered = if (query.isBlank()) {
            _allManagers.toList()
        } else {
            _allManagers.filter { user ->
                user.displayName.lowercase().contains(query) ||
                        user.email.lowercase().contains(query) ||
                        user.phone.lowercase().contains(query)
            }
        }

        _managerCount.value = filtered.size

        if (filtered.isEmpty() && _allManagers.isEmpty()) {
            _managerListState.value = ManagerListState.Empty("No managers found")
        } else if (filtered.isEmpty()) {
            _managerListState.value = ManagerListState.Empty("No managers match your search")
        } else {
            _managerListState.value = ManagerListState.Success(
                managers = filtered,
                totalCount = _allManagers.size,
                filteredCount = filtered.size
            )
        }

        _managers.value = ResultState.Success(filtered)
    }

    // ─── Create Manager ─────────────────────────────────────────

    /**
     * Validate inputs and create a new manager account.
     *
     * @param fullName Manager's full name
     * @param email Manager's email address
     * @param phone Manager's phone number
     * @param tempPassword Temporary password
     * @param sendResetEmail Whether to send password reset email
     */
    fun createManager(
        fullName: String,
        email: String,
        phone: String,
        tempPassword: String,
        sendResetEmail: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // ── Step 1: Role guard ──────────────────────────────
            if (!RoleGuard.canCreateManager()) {
                _creationState.value = ManagerCreationState.Error(
                    RoleGuard.ERROR_UNAUTHORIZED_MANAGER_CREATION
                )
                return@launch
            }

            // ── Step 2: Validate fields ─────────────────────────
            _creationState.value = ManagerCreationState.Validating

            val nameResult = ValidationUtils.validateFullName(fullName)
            if (!nameResult.isValid) {
                _creationState.value = ManagerCreationState.Error(
                    nameResult.errorMessage ?: "Invalid name"
                )
                return@launch
            }

            val emailResult = ValidationUtils.validateEmail(email)
            if (!emailResult.isValid) {
                _creationState.value = ManagerCreationState.Error(
                    emailResult.errorMessage ?: "Invalid email"
                )
                return@launch
            }

            val phoneResult = ValidationUtils.validatePhone(phone)
            if (!phoneResult.isValid) {
                _creationState.value = ManagerCreationState.Error(
                    phoneResult.errorMessage ?: "Invalid phone number"
                )
                return@launch
            }

            val passwordResult = ValidationUtils.validatePassword(tempPassword)
            if (!passwordResult.isValid) {
                _creationState.value = ManagerCreationState.Error(
                    passwordResult.errorMessage ?: "Invalid password"
                )
                return@launch
            }

            // ── Step 3: Create via repository ───────────────────
            _creationState.value = ManagerCreationState.CreatingAuth

            val result = repository.createManagerSafe(
                fullName = fullName,
                email = email,
                phone = phone,
                tempPassword = tempPassword,
                sendResetEmail = sendResetEmail
            )

            when (result) {
                is ResultState.Success -> {
                    val newManager = result.data
                    _creationState.value = ManagerCreationState.Success(newManager)

                    // Add to local list and refresh
                    _allManagers.add(0, newManager)
                    applyFilters()

                    emitSuccess("Manager ${newManager.displayName} created successfully!")
                }

                is ResultState.Error -> {
                    _creationState.value = ManagerCreationState.Error(result.message)
                    emitError(result.message)
                }

                else -> { /* no-op */ }
            }
        }
    }

    // ─── Deactivate Manager ─────────────────────────────────────

    /**
     * Deactivate a manager account (set isActive = false).
     *
     * @param managerUid The UID of the manager to deactivate
     */
    fun deactivateManager(managerUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!RoleGuard.canModifyManager()) {
                _deactivationState.value = ResultState.Error(
                    RoleGuard.ERROR_INSUFFICIENT_PRIVILEGES
                )
                return@launch
            }

            if (RoleGuard.isSelf(managerUid)) {
                _deactivationState.value = ResultState.Error(
                    RoleGuard.ERROR_SELF_DEACTIVATION
                )
                emitError(RoleGuard.ERROR_SELF_DEACTIVATION)
                return@launch
            }

            _deactivationState.value = ResultState.Loading

            val result = repository.deactivateManager(managerUid)
            _deactivationState.value = result

            when (result) {
                is ResultState.Success -> {
                    // Update local list
                    val index = _allManagers.indexOfFirst { it.uid == managerUid }
                    if (index >= 0) {
                        _allManagers[index] = _allManagers[index].copy(
                            isActive = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        applyFilters()
                    }
                    emitSuccess("Manager deactivated successfully")
                }

                is ResultState.Error -> {
                    emitError(result.message)
                }

                else -> { /* no-op */ }
            }
        }
    }

    // ─── Reactivate Manager ─────────────────────────────────────

    /**
     * Reactivate a previously deactivated manager.
     *
     * @param managerUid The UID of the manager to reactivate
     */
    fun reactivateManager(managerUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!RoleGuard.canModifyManager()) {
                emitError(RoleGuard.ERROR_INSUFFICIENT_PRIVILEGES)
                return@launch
            }

            _deactivationState.value = ResultState.Loading

            val result = repository.reactivateManager(managerUid)
            _deactivationState.value = result

            when (result) {
                is ResultState.Success -> {
                    val index = _allManagers.indexOfFirst { it.uid == managerUid }
                    if (index >= 0) {
                        _allManagers[index] = _allManagers[index].copy(
                            isActive = true,
                            updatedAt = System.currentTimeMillis()
                        )
                        applyFilters()
                    }
                    emitSuccess("Manager reactivated successfully")
                }

                is ResultState.Error -> {
                    emitError(result.message)
                }

                else -> { /* no-op */ }
            }
        }
    }

    // ─── State Reset ────────────────────────────────────────────

    /**
     * Reset the creation state back to Idle.
     * Call after dismissing the creation bottom sheet.
     */
    fun resetCreationState() {
        _creationState.value = ManagerCreationState.Idle
    }

    /**
     * Reset the deactivation state back to Idle.
     */
    fun resetDeactivationState() {
        _deactivationState.value = ResultState.Idle
    }
}
