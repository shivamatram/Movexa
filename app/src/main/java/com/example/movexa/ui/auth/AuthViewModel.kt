package com.example.movexa.ui.auth

import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.UserRole
import com.example.movexa.data.repository.AuthRepository
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for authentication screens (Login, Signup, Forgot Password).
 *
 * Manages:
 * - Signup state: creating Firebase Auth + Firestore user
 * - Login state: authenticating + creating session
 * - Reset password state: sending password reset email
 *
 * All operations use [ResultState] for predictable state transitions.
 * UI layers observe StateFlows and react to Idle/Loading/Success/Error.
 */
class AuthViewModel : BaseViewModel() {

    private val authRepository = AuthRepository()
    private val sessionManager = SessionManager.getInstance()

    // ─── Signup State ───────────────────────────────────────────

    private val _signupState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val signupState: StateFlow<ResultState<Unit>> = _signupState.asStateFlow()

    // ─── Login State ────────────────────────────────────────────

    private val _loginState = MutableStateFlow<ResultState<User>>(ResultState.Idle)
    val loginState: StateFlow<ResultState<User>> = _loginState.asStateFlow()

    // ─── Password Reset State ───────────────────────────────────

    private val _resetPasswordState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val resetPasswordState: StateFlow<ResultState<Unit>> = _resetPasswordState.asStateFlow()

    // ─── Selected Role (Signup) ─────────────────────────────────

    private val _selectedRole = MutableStateFlow<UserRole?>(null)
    val selectedRole: StateFlow<UserRole?> = _selectedRole.asStateFlow()

    // ─── Signup ─────────────────────────────────────────────────

    /**
     * Register a new user with Firebase Auth and create a Firestore profile.
     *
     * The account is signed out immediately after creation — the user
     * must log in explicitly after signup succeeds.
     *
     * @param fullName User's full name (trimmed, validated externally)
     * @param email User's email (trimmed, validated externally)
     * @param phone User's phone number (trimmed, validated externally)
     * @param password User's password (validated externally)
     * @param role Selected user role from dropdown
     */
    fun signUp(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        role: UserRole
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _signupState.value = ResultState.Loading
            setLoading(true)

            val result = authRepository.signUp(
                fullName = fullName.trim(),
                email = email.trim().lowercase(),
                phone = phone.trim(),
                password = password,
                role = role
            )

            _signupState.value = result
            setLoading(false)

            when (result) {
                is ResultState.Success -> emitSuccess("Account created successfully! Please log in.")
                is ResultState.Error -> emitError(result.message)
                else -> { /* Loading/Idle — no action */ }
            }
        }
    }

    // ─── Login ──────────────────────────────────────────────────

    /**
     * Sign in with email/password, fetch Firestore profile, and create session.
     *
     * On success:
     * 1. User profile is retrieved from Firestore
     * 2. SessionManager is populated (DataStore + in-memory)
     * 3. loginState emits Success<User>
     * 4. UI observes the User's role and navigates accordingly
     *
     * @param email User's email
     * @param password User's password
     */
    fun login(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = ResultState.Loading
            setLoading(true)

            val result = authRepository.login(
                email = email.trim().lowercase(),
                password = password
            )

            when (result) {
                is ResultState.Success -> {
                    // Create session in DataStore
                    val user = result.data
                    sessionManager.setUser(user)
                    _loginState.value = result
                    setLoading(false)
                }

                is ResultState.Error -> {
                    _loginState.value = result
                    setLoading(false)
                    emitError(result.message)
                }

                else -> {
                    setLoading(false)
                }
            }
        }
    }

    // ─── Forgot Password ────────────────────────────────────────

    /**
     * Send a password reset email via Firebase Auth.
     *
     * @param email The email address to send the reset link to
     */
    fun sendPasswordReset(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _resetPasswordState.value = ResultState.Loading
            setLoading(true)

            val result = authRepository.sendPasswordResetEmail(email.trim().lowercase())

            _resetPasswordState.value = result
            setLoading(false)

            when (result) {
                is ResultState.Success ->
                    emitSuccess("Password reset email sent. Please check your inbox.")

                is ResultState.Error ->
                    emitError(result.message)

                else -> { /* no-op */ }
            }
        }
    }

    // ─── Role Selection ─────────────────────────────────────────

    /**
     * Set the selected role for signup form.
     */
    fun setSelectedRole(role: UserRole?) {
        _selectedRole.value = role
    }

    // ─── State Reset ────────────────────────────────────────────

    /**
     * Reset signup state back to Idle.
     * Call after navigating away from signup screen.
     */
    fun resetSignupState() {
        _signupState.value = ResultState.Idle
    }

    /**
     * Reset login state back to Idle.
     * Call after navigating away from login screen.
     */
    fun resetLoginState() {
        _loginState.value = ResultState.Idle
    }

    /**
     * Reset password reset state back to Idle.
     * Call after dismissing the dialog.
     */
    fun resetPasswordResetState() {
        _resetPasswordState.value = ResultState.Idle
    }

    /**
     * Reset all auth states — typically called when auth flow is abandoned.
     */
    fun resetAllStates() {
        _signupState.value = ResultState.Idle
        _loginState.value = ResultState.Idle
        _resetPasswordState.value = ResultState.Idle
        _selectedRole.value = null
    }

    // ─── Sign Out ───────────────────────────────────────────────

    /**
     * Sign out the current user and clear the session.
     * Clears Firebase Auth + DataStore.
     */
    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            sessionManager.clearSession()
            emitSuccess("Signed out successfully.")
        }
    }
}
