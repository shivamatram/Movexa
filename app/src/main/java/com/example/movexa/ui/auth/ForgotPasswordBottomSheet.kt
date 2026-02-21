package com.example.movexa.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.databinding.BottomSheetForgotPasswordBinding
import com.example.movexa.utils.setDebouncedClickListener
import com.example.movexa.utils.trimmedText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Bottom sheet dialog for password reset.
 *
 * Accepts an email address and sends a Firebase Auth password reset email.
 * Uses the shared AuthViewModel for the reset operation.
 *
 * Shows inline loading state and success/error feedback within the sheet.
 * Dismisses automatically after successful email delivery.
 */
class ForgotPasswordBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ForgotPasswordBottomSheet"
    }

    private var _binding: BottomSheetForgotPasswordBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeData()
    }

    private fun setupListeners() {
        // Send reset link
        binding.btnSendReset.setDebouncedClickListener {
            attemptPasswordReset()
        }

        // Cancel / dismiss
        binding.btnCancelReset.setDebouncedClickListener {
            authViewModel.resetPasswordResetState()
            dismiss()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.resetPasswordState.collectLatest { state ->
                    when (state) {
                        is ResultState.Loading -> {
                            binding.btnSendReset.isEnabled = false
                            binding.resetLoadingIndicator.visibility = View.VISIBLE
                        }

                        is ResultState.Success -> {
                            binding.btnSendReset.isEnabled = true
                            binding.resetLoadingIndicator.visibility = View.GONE
                            // Show success and dismiss
                            view?.let { rootView ->
                                Snackbar.make(
                                    rootView,
                                    getString(R.string.reset_email_sent),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            authViewModel.resetPasswordResetState()
                            dismiss()
                        }

                        is ResultState.Error -> {
                            binding.btnSendReset.isEnabled = true
                            binding.resetLoadingIndicator.visibility = View.GONE
                            binding.tilResetEmail.error = state.message
                        }

                        is ResultState.Idle -> {
                            binding.btnSendReset.isEnabled = true
                            binding.resetLoadingIndicator.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    /**
     * Validate email and send password reset via AuthViewModel.
     */
    private fun attemptPasswordReset() {
        val email = binding.etResetEmail.trimmedText()

        binding.tilResetEmail.error = null

        if (email.isBlank()) {
            binding.tilResetEmail.error = getString(R.string.error_field_required)
            binding.etResetEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilResetEmail.error = getString(R.string.error_invalid_email)
            binding.etResetEmail.requestFocus()
            return
        }

        authViewModel.sendPasswordReset(email)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
