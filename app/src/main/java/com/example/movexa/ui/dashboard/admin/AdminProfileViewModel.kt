package com.example.movexa.ui.dashboard.admin

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.repository.AdminProfileRepository
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Admin Profile screen.
 *
 * Manages:
 * - Profile loading from Firestore (with SessionManager cache fallback)
 * - Profile editing (fullName, phone)
 * - Password change via re-authentication
 * - Company settings CRUD
 * - System toggles (maintenance mode, notifications, tracking, auto-assign)
 * - Audit log fetching
 * - Logout (clear session + Firebase sign-out)
 *
 * Uses [AdminProfileRepository] for Firebase operations and
 * [SessionManager] for cached session state.
 */
class AdminProfileViewModel : BaseViewModel() {

    private val repository = AdminProfileRepository()
    private val sessionManager = SessionManager.getInstance()

    // ─── Profile State ──────────────────────────────────────────

    private val _profileState = MutableStateFlow<ResultState<User>>(ResultState.Idle)
    val profileState: StateFlow<ResultState<User>> = _profileState.asStateFlow()

    // ─── Edit Profile State ─────────────────────────────────────

    private val _editProfileState = MutableStateFlow<ResultState<User>>(ResultState.Idle)
    val editProfileState: StateFlow<ResultState<User>> = _editProfileState.asStateFlow()

    // ─── Change Password State ──────────────────────────────────

    private val _changePasswordState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val changePasswordState: StateFlow<ResultState<Unit>> = _changePasswordState.asStateFlow()

    // ─── Company Settings State ─────────────────────────────────

    private val _companySettingsState = MutableStateFlow<ResultState<Map<String, Any>>>(ResultState.Idle)
    val companySettingsState: StateFlow<ResultState<Map<String, Any>>> = _companySettingsState.asStateFlow()

    // ─── Company Settings Update State ──────────────────────────

    private val _companySettingsUpdateState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val companySettingsUpdateState: StateFlow<ResultState<Unit>> = _companySettingsUpdateState.asStateFlow()

    // ─── Audit Logs State ───────────────────────────────────────

    private val _auditLogsState = MutableStateFlow<ResultState<List<Map<String, Any>>>>(ResultState.Idle)
    val auditLogsState: StateFlow<ResultState<List<Map<String, Any>>>> = _auditLogsState.asStateFlow()

    // ─── Logout State ───────────────────────────────────────────

    private val _logoutState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val logoutState: StateFlow<ResultState<Unit>> = _logoutState.asStateFlow()

    // ─── UI State ───────────────────────────────────────────────

    /**
     * Cached user to populate UI immediately while Firestore loads.
     */
    val cachedUser: User?
        get() = sessionManager.currentUser.value

    /**
     * Observe session user changes (e.g. after profile update).
     */
    val sessionUser: StateFlow<User?> = sessionManager.currentUser

    /**
     * Company ID is admin's user ID.
     * Must be called from a coroutine since getCachedUserId is suspend.
     */
    private suspend fun getCompanyId(): String? =
        sessionManager.getCachedUserId() ?: repository.getCurrentUserId()

    // ─── Cached Company Settings ────────────────────────────────

    private var _cachedCompanySettings: Map<String, Any> = emptyMap()
    val cachedCompanySettings: Map<String, Any>
        get() = _cachedCompanySettings

    // ═══════════════════════════════════════════════════════════
    // PROFILE LOADING
    // ═══════════════════════════════════════════════════════════

    /**
     * Load the admin's profile from Firestore.
     *
     * Strategy:
     * 1. Show SessionManager cached user immediately via [cachedUser]
     * 2. Fetch fresh data from Firestore in background
     * 3. Update SessionManager cache on success
     */
    fun loadProfile() {
        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = false,
            onError = { e ->
                _profileState.value = ResultState.Error(
                    message = e.message ?: "Failed to load profile"
                )
            }
        ) {
            _profileState.value = ResultState.Loading

            val uid = getCompanyId() ?: run {
                _profileState.value = ResultState.Error("User not authenticated")
                return@launchWithLoading
            }

            when (val result = repository.fetchProfile(uid)) {
                is ResultState.Success -> {
                    sessionManager.updateUser(result.data)
                    _profileState.value = result
                }
                is ResultState.Error -> {
                    val cached = sessionManager.currentUser.value
                    if (cached != null) {
                        _profileState.value = ResultState.Success(cached)
                        emitError("Using cached profile. ${result.message}")
                    } else {
                        _profileState.value = result
                    }
                }
                else -> {
                    _profileState.value = result
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PROFILE EDITING
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the admin's profile fields (name and phone).
     */
    fun updateProfile(fullName: String, phone: String) {
        if (fullName.isBlank()) {
            emitError("Name cannot be empty")
            return
        }
        if (fullName.length < 2) {
            emitError("Name must be at least 2 characters")
            return
        }

        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = true,
            onError = { e ->
                _editProfileState.value = ResultState.Error(
                    message = e.message ?: "Failed to update profile"
                )
            }
        ) {
            _editProfileState.value = ResultState.Loading

            val uid = getCompanyId() ?: run {
                _editProfileState.value = ResultState.Error("User not authenticated")
                return@launchWithLoading
            }

            when (val result = repository.updateProfile(uid, fullName, phone)) {
                is ResultState.Success -> {
                    sessionManager.updateUser(result.data)
                    _editProfileState.value = result
                    _profileState.value = result
                    emitSuccess("Profile updated successfully")

                    // Write audit log
                    repository.writeAuditLog(uid, "profile_update", "Updated profile name and phone")
                }
                is ResultState.Error -> {
                    _editProfileState.value = result
                    emitError(result.message)
                }
                else -> {
                    _editProfileState.value = result
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PASSWORD CHANGE
    // ═══════════════════════════════════════════════════════════

    /**
     * Change the admin's password.
     */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        if (currentPassword.isBlank()) {
            emitError("Current password is required")
            return
        }
        if (newPassword.isBlank()) {
            emitError("New password is required")
            return
        }
        if (newPassword.length < 6) {
            emitError("Password must be at least 6 characters")
            return
        }
        if (newPassword != confirmPassword) {
            emitError("Passwords do not match")
            return
        }
        if (currentPassword == newPassword) {
            emitError("New password must be different from current password")
            return
        }

        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = true,
            onError = { e ->
                _changePasswordState.value = ResultState.Error(
                    message = e.message ?: "Failed to change password"
                )
            }
        ) {
            _changePasswordState.value = ResultState.Loading

            when (val result = repository.changePassword(currentPassword, newPassword)) {
                is ResultState.Success -> {
                    _changePasswordState.value = result
                    emitSuccess("Password changed successfully")

                    getCompanyId()?.let {
                        repository.writeAuditLog(it, "password_change", "Admin password changed")
                    }
                }
                is ResultState.Error -> {
                    _changePasswordState.value = result
                    emitError(result.message)
                }
                else -> {
                    _changePasswordState.value = result
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // COMPANY SETTINGS
    // ═══════════════════════════════════════════════════════════

    /**
     * Load company settings from Firestore.
     */
    fun loadCompanySettings() {
        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = false,
            onError = { e ->
                _companySettingsState.value = ResultState.Error(
                    message = e.message ?: "Failed to load company settings"
                )
            }
        ) {
            _companySettingsState.value = ResultState.Loading

            val id = getCompanyId() ?: run {
                _companySettingsState.value = ResultState.Error("User not authenticated")
                return@launchWithLoading
            }

            when (val result = repository.fetchCompanySettings(id)) {
                is ResultState.Success -> {
                    _cachedCompanySettings = result.data
                    _companySettingsState.value = result
                }
                is ResultState.Error -> {
                    _companySettingsState.value = result
                }
                else -> {
                    _companySettingsState.value = result
                }
            }
        }
    }

    /**
     * Update company settings (name, address, phone, email, gst).
     */
    fun updateCompanySettings(
        companyName: String,
        companyAddress: String,
        companyPhone: String,
        companyEmail: String,
        gstNumber: String
    ) {
        if (companyName.isBlank()) {
            emitError("Company name cannot be empty")
            return
        }

        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = true,
            onError = { e ->
                _companySettingsUpdateState.value = ResultState.Error(
                    message = e.message ?: "Failed to update company settings"
                )
            }
        ) {
            _companySettingsUpdateState.value = ResultState.Loading

            val id = getCompanyId() ?: run {
                _companySettingsUpdateState.value = ResultState.Error("User not authenticated")
                return@launchWithLoading
            }

            val settings = mapOf(
                "companyName" to companyName,
                "companyAddress" to companyAddress,
                "companyPhone" to companyPhone,
                "companyEmail" to companyEmail,
                "gstNumber" to gstNumber
            )

            when (val result = repository.updateCompanySettings(id, settings)) {
                is ResultState.Success -> {
                    _companySettingsUpdateState.value = result
                    // Refresh cached settings
                    _cachedCompanySettings = _cachedCompanySettings.toMutableMap().apply {
                        putAll(settings)
                    }
                    _companySettingsState.value = ResultState.Success(_cachedCompanySettings)
                    emitSuccess("Company settings updated")

                    repository.writeAuditLog(id, "settings_update", "Updated company settings")
                }
                is ResultState.Error -> {
                    _companySettingsUpdateState.value = result
                    emitError(result.message)
                }
                else -> {
                    _companySettingsUpdateState.value = result
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SYSTEM TOGGLES
    // ═══════════════════════════════════════════════════════════

    /**
     * Toggle a system-level setting.
     *
     * @param key   Setting key (e.g. "maintenanceMode")
     * @param value New boolean value
     */
    fun toggleSystemSetting(key: String, value: Boolean) {
        launchSafe(
            dispatcher = Dispatchers.IO,
            onError = { e ->
                emitError(e.message ?: "Failed to update setting")
            }
        ) {
            val id = getCompanyId() ?: run {
                emitError("User not authenticated")
                return@launchSafe
            }

            when (val result = repository.updateSystemToggle(id, key, value)) {
                is ResultState.Success -> {
                    _cachedCompanySettings = _cachedCompanySettings.toMutableMap().apply {
                        put(key, value)
                    }
                    _companySettingsState.value = ResultState.Success(_cachedCompanySettings)

                    val label = key.replace(Regex("([A-Z])"), " $1")
                        .trim().lowercase().replaceFirstChar { it.uppercase() }
                    val state = if (value) "enabled" else "disabled"
                    emitSuccess("$label $state")

                    repository.writeAuditLog(id, "system_toggle", "$key set to $value")
                }
                is ResultState.Error -> {
                    emitError(result.message)
                }
                else -> { /* no-op */ }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // AUDIT LOGS
    // ═══════════════════════════════════════════════════════════

    /**
     * Load recent audit logs.
     */
    fun loadAuditLogs() {
        launchSafe(
            dispatcher = Dispatchers.IO,
            onError = { e ->
                _auditLogsState.value = ResultState.Error(
                    message = e.message ?: "Failed to load audit logs"
                )
            }
        ) {
            _auditLogsState.value = ResultState.Loading

            val id = getCompanyId() ?: run {
                _auditLogsState.value = ResultState.Error("User not authenticated")
                return@launchSafe
            }

            when (val result = repository.fetchAuditLogs(id, limit = 5)) {
                is ResultState.Success -> {
                    _auditLogsState.value = result
                }
                is ResultState.Error -> {
                    _auditLogsState.value = result
                }
                else -> {
                    _auditLogsState.value = result
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LOGOUT
    // ═══════════════════════════════════════════════════════════

    /**
     * Sign out the admin:
     * 1. Clear SessionManager (DataStore + in-memory)
     * 2. Sign out Firebase Auth
     * 3. Emit success state for navigation
     */
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _logoutState.value = ResultState.Loading

                sessionManager.clearSession()
                repository.signOut()

                _logoutState.value = ResultState.Success(Unit)
            } catch (e: Exception) {
                _logoutState.value = ResultState.Error(
                    message = e.message ?: "Failed to sign out"
                )
                emitError("Logout failed. Please try again.")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STATE RESET
    // ═══════════════════════════════════════════════════════════

    fun resetEditProfileState() {
        _editProfileState.value = ResultState.Idle
    }

    fun resetChangePasswordState() {
        _changePasswordState.value = ResultState.Idle
    }

    fun resetLogoutState() {
        _logoutState.value = ResultState.Idle
    }

    fun resetCompanySettingsUpdateState() {
        _companySettingsUpdateState.value = ResultState.Idle
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the current user email for display purposes.
     */
    fun getCurrentEmail(): String {
        return cachedUser?.email ?: ""
    }

    /**
     * Get the account creation date as milliseconds.
     */
    fun getAccountCreatedAt(): Long {
        return cachedUser?.createdAt ?: 0L
    }

    /**
     * Format a role for display (e.g. ADMIN → "Administrator").
     */
    fun getFormattedRole(): String {
        return "Administrator"
    }

    /**
     * Get a company setting value by key.
     */
    fun getCompanySetting(key: String, default: String = ""): String {
        return _cachedCompanySettings[key]?.toString() ?: default
    }

    /**
     * Get a company toggle value by key.
     */
    fun getCompanyToggle(key: String, default: Boolean = false): Boolean {
        return _cachedCompanySettings[key] as? Boolean ?: default
    }
}
