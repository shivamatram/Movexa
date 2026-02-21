package com.example.movexa.ui.auth

import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.UserRole
import com.example.movexa.databinding.FragmentSignupBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.ValidationUtils
import com.example.movexa.utils.clearErrorOnTextChange
import com.example.movexa.utils.setDebouncedClickListener
import com.example.movexa.utils.trimmedText

/**
 * Signup screen with full Firebase Auth + Firestore integration.
 *
 * Features:
 * - Full name, email, phone, role, password, confirm password inputs
 * - Role dropdown (AutoCompleteTextView) for ADMIN/MANAGER/DRIVER/MECHANIC
 * - Real-time field validation using ValidationUtils
 * - Firebase account creation via AuthViewModel
 * - Signs out immediately after signup (no auto-login)
 * - Navigates back to login on success
 *
 * Uses shared AuthViewModel scoped to the Activity for cross-fragment state.
 */
class SignupFragment : BaseFragment<FragmentSignupBinding>(
    FragmentSignupBinding::inflate
) {

    private val authViewModel: AuthViewModel by activityViewModels()

    // Map display names to UserRole enum
    private val roleMap = linkedMapOf(
        "Admin" to UserRole.ADMIN,
        "Manager" to UserRole.MANAGER,
        "Driver" to UserRole.DRIVER,
        "Mechanic" to UserRole.MECHANIC
    )

    override fun initViews() {
        hideToolbar()
        hideBottomNav()
        setupRoleDropdown()
    }

    override fun setupListeners() {
        // Create account button
        binding.btnSignup.setDebouncedClickListener {
            attemptSignup()
        }

        // Navigate back to login
        binding.btnGoToLogin.setDebouncedClickListener {
            authViewModel.resetSignupState()
            navigateTo(R.id.action_signup_to_login)
        }

        // Clear errors on text change
        binding.tilFullName.clearErrorOnTextChange()
        binding.tilEmail.clearErrorOnTextChange()
        binding.tilPhone.clearErrorOnTextChange()
        binding.tilPassword.clearErrorOnTextChange()
        binding.tilConfirmPassword.clearErrorOnTextChange()
    }

    override fun observeData() {
        // Observe signup state
        collectLatestFlow(authViewModel.signupState) { state ->
            when (state) {
                is ResultState.Loading -> {
                    showLoading()
                    binding.btnSignup.isEnabled = false
                }

                is ResultState.Success -> {
                    hideLoading()
                    binding.btnSignup.isEnabled = true
                    showSuccess("Account created successfully! Please log in.")
                    // Navigate to login after successful signup
                    authViewModel.resetSignupState()
                    navigateTo(R.id.action_signup_to_login)
                }

                is ResultState.Error -> {
                    hideLoading()
                    binding.btnSignup.isEnabled = true
                    showError(state.message)
                }

                is ResultState.Idle -> {
                    hideLoading()
                    binding.btnSignup.isEnabled = true
                }
            }
        }

        // Observe loading state
        collectFlow(authViewModel.isLoading) { isLoading ->
            if (isLoading) showLoading() else hideLoading()
        }

        // Observe error events
        collectFlow(authViewModel.errorEvent) { error ->
            showError(error)
        }
    }

    // ─── Private Methods ────────────────────────────────────────

    /**
     * Set up the role dropdown with available roles.
     */
    private fun setupRoleDropdown() {
        val roleNames = roleMap.keys.toList()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            roleNames
        )
        binding.actRole.setAdapter(adapter)

        // Track selected role
        binding.actRole.setOnItemClickListener { _, _, position, _ ->
            val selectedRoleName = roleNames[position]
            val selectedRole = roleMap[selectedRoleName]
            authViewModel.setSelectedRole(selectedRole)
            binding.tilRole.error = null
        }
    }

    /**
     * Validate all inputs and attempt signup via AuthViewModel.
     */
    private fun attemptSignup() {
        val fullName = binding.etFullName.trimmedText()
        val email = binding.etEmail.trimmedText()
        val phone = binding.etPhone.trimmedText()
        val password = binding.etPassword.trimmedText()
        val confirmPassword = binding.etConfirmPassword.trimmedText()
        val selectedRoleText = binding.actRole.text.toString().trim()

        // Clear all previous errors
        clearErrors()

        // Validate all fields
        var isValid = true

        // Full name validation
        val nameResult = ValidationUtils.validateFullName(fullName)
        if (!nameResult.isValid) {
            binding.tilFullName.error = nameResult.errorMessage
            if (isValid) binding.etFullName.requestFocus()
            isValid = false
        }

        // Email validation
        val emailResult = ValidationUtils.validateEmail(email)
        if (!emailResult.isValid) {
            binding.tilEmail.error = emailResult.errorMessage
            if (isValid) binding.etEmail.requestFocus()
            isValid = false
        }

        // Phone validation
        val phoneResult = ValidationUtils.validatePhone(phone)
        if (!phoneResult.isValid) {
            binding.tilPhone.error = phoneResult.errorMessage
            if (isValid) binding.etPhone.requestFocus()
            isValid = false
        }

        // Role validation
        val selectedRole = roleMap[selectedRoleText]
        if (selectedRole == null) {
            binding.tilRole.error = getString(R.string.error_role_required)
            if (isValid) binding.actRole.requestFocus()
            isValid = false
        }

        // Password validation
        val passwordResult = ValidationUtils.validatePassword(password)
        if (!passwordResult.isValid) {
            binding.tilPassword.error = passwordResult.errorMessage
            if (isValid) binding.etPassword.requestFocus()
            isValid = false
        }

        // Confirm password validation
        val confirmResult = ValidationUtils.validatePasswordMatch(password, confirmPassword)
        if (!confirmResult.isValid) {
            binding.tilConfirmPassword.error = confirmResult.errorMessage
            if (isValid) binding.etConfirmPassword.requestFocus()
            isValid = false
        }

        if (!isValid) return

        // All validations passed — fire signup
        authViewModel.signUp(
            fullName = fullName,
            email = email,
            phone = phone,
            password = password,
            role = selectedRole!!
        )
    }

    /**
     * Clear all TextInputLayout error states.
     */
    private fun clearErrors() {
        binding.tilFullName.error = null
        binding.tilEmail.error = null
        binding.tilPhone.error = null
        binding.tilRole.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Don't reset state here — let the user navigate back and keep form state
    }
}
