package com.example.movexa.ui.dashboard.admin

import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.movexa.R
import com.example.movexa.databinding.FragmentAdminProfileBinding
import com.example.movexa.ui.auth.AuthViewModel
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.HapticManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Profile tab for the Admin dashboard.
 *
 * Provides sign-out functionality for administrators.
 */
class AdminProfileFragment : BaseFragment<FragmentAdminProfileBinding>(
    FragmentAdminProfileBinding::inflate
) {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun initViews() {
        // No-op
    }

    override fun setupListeners() {
        binding.actionLogout.setOnClickListener {
            HapticManager.medium(it)
            showLogoutConfirmation()
        }
    }

    override fun observeData() {
        // No-op
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.logout_confirm_title))
            .setMessage(getString(R.string.logout_confirm_message))
            .setPositiveButton(getString(R.string.logout_confirm_yes)) { dialog, _ ->
                dialog.dismiss()
                HapticManager.mediumVibrate(requireContext())
                authViewModel.signOut()
                navigateToLogin()
            }
            .setNegativeButton(getString(R.string.logout_confirm_no)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    /**
     * Navigate to Login and clear the entire backstack.
     */
    private fun navigateToLogin() {
        try {
            val navController = requireActivity().let { activity ->
                androidx.navigation.Navigation.findNavController(
                    activity, R.id.navHostFragment
                )
            }
            navController.navigate(R.id.action_global_login)
        } catch (e: Exception) {
            try {
                findNavController().navigate(R.id.action_global_login)
            } catch (e2: Exception) {
                showError("Navigation error. Please restart the app.")
            }
        }
    }
}
