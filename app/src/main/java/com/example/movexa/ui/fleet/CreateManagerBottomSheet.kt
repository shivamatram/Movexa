package com.example.movexa.ui.fleet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.movexa.R
import com.example.movexa.data.model.ManagerCreationState
import com.example.movexa.data.model.User
import com.example.movexa.databinding.BottomSheetCreateManagerBinding
import com.example.movexa.utils.ValidationUtils
import com.example.movexa.utils.clearErrorOnTextChange
import com.example.movexa.utils.setDebouncedClickListener
import com.example.movexa.utils.trimmedText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Bottom sheet for creating a new manager account.
 *
 * Only accessible by ADMIN users through the Manager tab FAB.
 * Creates a Firebase Auth account + Firestore profile for the new manager,
 * optionally sends a password reset email.
 *
 * Uses [AdminManagerViewModel] for all business logic:
 * - Input validation (via ValidationUtils)
 * - RoleGuard enforcement (only ADMINs can create managers)
 * - Firebase Auth user creation with rollback on failure
 * - Firestore document creation with company scoping
 * - Password reset email dispatch
 *
 * Follows the existing bottom sheet pattern:
 * - Extends BottomSheetDialogFragment directly (matches project convention)
 * - Manual ViewBinding lifecycle management
 * - Callback lambda for parent notification
 * - Loading/progress state with disabled controls
 */
class CreateManagerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateManagerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminManagerViewModel by activityViewModels()

    /**
     * Callback invoked when a manager is successfully created.
     * The parent fragment should refresh the manager list on receiving this.
     */
    var onManagerCreated: ((User) -> Unit)? = null

    // ─── Lifecycle ──────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        setupListeners()
        observeCreationState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── Setup ──────────────────────────────────────────────────

    private fun initViews() {
        // Clear any previous creation state when opening fresh
        viewModel.resetCreationState()

        // Auto-clear field errors on text change
        binding.tilFullName.clearErrorOnTextChange()
        binding.tilEmail.clearErrorOnTextChange()
        binding.tilPhone.clearErrorOnTextChange()
        binding.tilPassword.clearErrorOnTextChange()
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnCreate.setDebouncedClickListener {
            attemptCreateManager()
        }
    }

    // ─── Validation & Submission ────────────────────────────────

    /**
     * Validate all input fields and trigger manager creation if valid.
     * Uses the same ValidationUtils patterns as the signup form.
     */
    private fun attemptCreateManager() {
        val fullName = binding.etFullName.trimmedText()
        val email = binding.etEmail.trimmedText()
        val phone = binding.etPhone.trimmedText()
        val password = binding.etPassword.trimmedText()
        val sendResetEmail = binding.cbSendResetEmail.isChecked

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

        // Password validation
        val passwordResult = ValidationUtils.validatePassword(password)
        if (!passwordResult.isValid) {
            binding.tilPassword.error = passwordResult.errorMessage
            if (isValid) binding.etPassword.requestFocus()
            isValid = false
        }

        if (!isValid) return

        // All validations passed — delegate to ViewModel
        viewModel.createManager(
            fullName = fullName,
            email = email,
            phone = phone,
            tempPassword = password,
            sendResetEmail = sendResetEmail
        )
    }

    // ─── State Observation ──────────────────────────────────────

    /**
     * Observe the manager creation state from ViewModel and update UI accordingly.
     * Shows progress steps, handles success/error, and manages control states.
     */
    private fun observeCreationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.creationState.collectLatest { state ->
                    when (state) {
                        is ManagerCreationState.Idle -> {
                            hideLoading()
                            binding.tvStatus.visibility = View.GONE
                        }

                        is ManagerCreationState.Validating -> {
                            showLoading()
                            showStatus(getString(R.string.state_validating))
                        }

                        is ManagerCreationState.CreatingAuth -> {
                            showLoading()
                            showStatus(getString(R.string.state_creating_auth))
                        }

                        is ManagerCreationState.WritingProfile -> {
                            showLoading()
                            showStatus(getString(R.string.state_writing_profile))
                        }

                        is ManagerCreationState.SendingResetEmail -> {
                            showLoading()
                            showStatus(getString(R.string.state_sending_reset_email))
                        }

                        is ManagerCreationState.Success -> {
                            hideLoading()
                            binding.tvStatus.visibility = View.GONE
                            onManagerCreated?.invoke(state.manager)
                            viewModel.resetCreationState()
                            dismiss()
                        }

                        is ManagerCreationState.Error -> {
                            hideLoading()
                            showStatus(state.message)
                            binding.tvStatus.setTextColor(
                                resources.getColor(R.color.error, requireContext().theme)
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── Loading State ──────────────────────────────────────────

    /**
     * Show loading state — disable all inputs and show progress indicator.
     */
    private fun showLoading() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.btnCreate.isEnabled = false
        binding.btnCancel.isEnabled = false
        binding.etFullName.isEnabled = false
        binding.etEmail.isEnabled = false
        binding.etPhone.isEnabled = false
        binding.etPassword.isEnabled = false
        binding.cbSendResetEmail.isEnabled = false
        isCancelable = false
    }

    /**
     * Hide loading state — re-enable all inputs and hide progress indicator.
     */
    private fun hideLoading() {
        binding.progressIndicator.visibility = View.GONE
        binding.btnCreate.isEnabled = true
        binding.btnCancel.isEnabled = true
        binding.etFullName.isEnabled = true
        binding.etEmail.isEnabled = true
        binding.etPhone.isEnabled = true
        binding.etPassword.isEnabled = true
        binding.cbSendResetEmail.isEnabled = true
        isCancelable = true
    }

    /**
     * Show a status text message below the progress indicator.
     */
    private fun showStatus(message: String) {
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(
            resources.getColor(R.color.text_secondary, requireContext().theme)
        )
    }

    // ─── Companion ──────────────────────────────────────────────

    companion object {
        const val TAG = "CreateManagerBottomSheet"

        fun newInstance(): CreateManagerBottomSheet {
            return CreateManagerBottomSheet()
        }
    }
}
