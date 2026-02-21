package com.example.movexa.ui.auth

import androidx.fragment.app.activityViewModels
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.UserRole
import com.example.movexa.databinding.FragmentLoginBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.clearErrorOnTextChange
import com.example.movexa.utils.setDebouncedClickListener
import com.example.movexa.utils.trimmedText

/**
 * Login screen with full Firebase Auth integration and role-based routing.
 *
 * Features:
 * - Email/password authentication via AuthViewModel
 * - Forgot password via ForgotPasswordBottomSheet
 * - Role-based navigation after successful login:
 *   - ADMIN → AdminMainContainerFragment
 *   - MANAGER → ManagerMainContainerFragment
 *   - MECHANIC → MechanicMainContainerFragment
 *   - DRIVER → if verified: DriverHomeFragment, else DriverProfileFragment
 *
 * Uses shared AuthViewModel scoped to the Activity for cross-fragment state.
 */
class LoginFragment : BaseFragment<FragmentLoginBinding>(
    FragmentLoginBinding::inflate
) {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun initViews() {
        hideToolbar()
        hideBottomNav()
        // Reset login state when arriving at login screen
        authViewModel.resetLoginState()
    }

    override fun setupListeners() {
        // Sign in button
        binding.btnLogin.setDebouncedClickListener {
            attemptLogin()
        }

        // Navigate to signup
        binding.btnGoToSignup.setDebouncedClickListener {
            navigateTo(R.id.action_login_to_signup)
        }

        // Forgot password bottom sheet
        binding.btnForgotPassword.setDebouncedClickListener {
            showForgotPasswordSheet()
        }

        // Clear errors on text change
        binding.tilEmail.clearErrorOnTextChange()
        binding.tilPassword.clearErrorOnTextChange()
    }

    override fun observeData() {
        // Observe login state
        collectLatestFlow(authViewModel.loginState) { state ->
            when (state) {
                is ResultState.Loading -> {
                    showLoading()
                    binding.btnLogin.isEnabled = false
                    binding.btnGoToSignup.isEnabled = false
                    binding.btnForgotPassword.isEnabled = false
                }

                is ResultState.Success -> {
                    hideLoading()
                    binding.btnLogin.isEnabled = true
                    binding.btnGoToSignup.isEnabled = true
                    binding.btnForgotPassword.isEnabled = true
                    // Navigate based on user role
                    navigateByRole(state.data)
                }

                is ResultState.Error -> {
                    hideLoading()
                    binding.btnLogin.isEnabled = true
                    binding.btnGoToSignup.isEnabled = true
                    binding.btnForgotPassword.isEnabled = true
                    showError(state.message)
                }

                is ResultState.Idle -> {
                    hideLoading()
                    binding.btnLogin.isEnabled = true
                    binding.btnGoToSignup.isEnabled = true
                    binding.btnForgotPassword.isEnabled = true
                }
            }
        }
    }

    // ─── Private Methods ────────────────────────────────────────

    /**
     * Validate inputs and attempt login via AuthViewModel.
     */
    private fun attemptLogin() {
        val email = binding.etEmail.trimmedText()
        val password = binding.etPassword.trimmedText()

        // Clear previous errors
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        // Validate email
        if (email.isBlank()) {
            binding.tilEmail.error = getString(R.string.error_field_required)
            binding.etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            binding.etEmail.requestFocus()
            return
        }

        // Validate password
        if (password.isBlank()) {
            binding.tilPassword.error = getString(R.string.error_field_required)
            binding.etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            binding.etPassword.requestFocus()
            return
        }

        // Fire login
        authViewModel.login(email, password)
    }

    /**
     * Navigate to the appropriate dashboard based on user role and verification.
     *
     * Routing rules:
     * - ADMIN → adminMainContainerFragment
     * - MANAGER → managerMainContainerFragment
     * - MECHANIC → mechanicMainContainerFragment
     * - DRIVER → verified: driverHomeFragment / unverified: driverProfileFragment
     */
    private fun navigateByRole(user: User) {
        val actionId = when (user.role) {
            UserRole.ADMIN -> R.id.action_login_to_admin
            UserRole.MANAGER -> R.id.action_login_to_manager
            UserRole.MECHANIC -> R.id.action_login_to_mechanic
            UserRole.DRIVER -> {
                if (user.isVerified) {
                    R.id.action_login_to_driverHome
                } else {
                    R.id.action_login_to_driverProfile
                }
            }
        }
        navigateTo(actionId)
    }

    /**
     * Show the forgot password bottom sheet dialog.
     */
    private fun showForgotPasswordSheet() {
        val bottomSheet = ForgotPasswordBottomSheet()
        bottomSheet.show(parentFragmentManager, ForgotPasswordBottomSheet.TAG)
    }
}
