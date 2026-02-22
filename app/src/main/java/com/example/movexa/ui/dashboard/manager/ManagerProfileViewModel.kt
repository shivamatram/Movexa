package com.example.movexa.ui.dashboard.manager

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.repository.ManagerProfileRepository
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Manager Profile screen.
 *
 * Manages:
 * - Profile loading from Firestore (with SessionManager cache fallback)
 * - Profile editing (fullName, phone)
 * - Password change via re-authentication
 * - Logout (clear session + Firebase sign-out)
 *
 * Uses [ManagerProfileRepository] for Firebase operations and
 * [SessionManager] for cached session state.
 */
class ManagerProfileViewModel : BaseViewModel() {

    private val repository = ManagerProfileRepository()
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

    // ─── Profile Loading ────────────────────────────────────────

    /**
     * Load the manager's profile from Firestore.
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

            val uid = sessionManager.getCachedUserId()
                ?: repository.getCurrentUserId()
                ?: run {
                    _profileState.value = ResultState.Error("User not authenticated")
                    return@launchWithLoading
                }

            when (val result = repository.fetchProfile(uid)) {
                is ResultState.Success -> {
                    // Update session cache with fresh data
                    sessionManager.updateUser(result.data)
                    _profileState.value = result
                }
                is ResultState.Error -> {
                    // If Firestore fails but we have cached data, use it
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

    // ─── Profile Editing ────────────────────────────────────────

    /**
     * Update the user's profile fields (name and phone).
     *
     * @param fullName Updated full name
     * @param phone    Updated phone number
     */
    fun updateProfile(fullName: String, phone: String) {
        // ── Input validation ─────────────────────────────
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

            val uid = sessionManager.getCachedUserId()
                ?: repository.getCurrentUserId()
                ?: run {
                    _editProfileState.value = ResultState.Error("User not authenticated")
                    return@launchWithLoading
                }

            when (val result = repository.updateProfile(uid, fullName, phone)) {
                is ResultState.Success -> {
                    // Update local session cache
                    sessionManager.updateUser(result.data)
                    _editProfileState.value = result
                    // Also refresh main profile state
                    _profileState.value = result
                    emitSuccess("Profile updated successfully")
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

    // ─── Password Change ────────────────────────────────────────

    /**
     * Change the user's password.
     *
     * Re-authenticates with current password, then updates to new password.
     *
     * @param currentPassword Existing password for verification
     * @param newPassword     New password to set
     * @param confirmPassword Confirmation of new password (must match)
     */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        // ── Input validation ─────────────────────────────
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

    // ─── Logout ─────────────────────────────────────────────────

    /**
     * Sign out the user:
     * 1. Clear SessionManager (DataStore + in-memory)
     * 2. Sign out Firebase Auth
     * 3. Emit navigation event to login screen
     */
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _logoutState.value = ResultState.Loading

                // Clear session first
                sessionManager.clearSession()

                // Sign out Firebase (redundant but safe — clearSession also calls this)
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

    // ─── State Reset ────────────────────────────────────────────

    /**
     * Reset the edit profile state (call after dismissing bottom sheet).
     */
    fun resetEditProfileState() {
        _editProfileState.value = ResultState.Idle
    }

    /**
     * Reset the change password state (call after dismissing dialog).
     */
    fun resetChangePasswordState() {
        _changePasswordState.value = ResultState.Idle
    }

    /**
     * Reset the logout state.
     */
    fun resetLogoutState() {
        _logoutState.value = ResultState.Idle
    }

    // ─── Helpers ────────────────────────────────────────────────

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
     * Format a role for display (e.g. MANAGER → "Manager").
     */
    fun getFormattedRole(): String {
        val role = cachedUser?.role ?: sessionManager.currentRole.value
        return role?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Manager"
    }
}
