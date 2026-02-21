package com.example.movexa.ui.dashboard

import androidx.fragment.app.activityViewModels
import com.example.movexa.R
import com.example.movexa.data.session.SessionManager
import com.example.movexa.databinding.FragmentAdminContainerBinding
import com.example.movexa.ui.auth.AuthViewModel
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.setDebouncedClickListener

/**
 * Placeholder container fragment for Admin role.
 * Displays welcome message and sign-out functionality.
 * Will be replaced with full admin dashboard in future implementation.
 */
class AdminMainContainerFragment : BaseFragment<FragmentAdminContainerBinding>(
    FragmentAdminContainerBinding::inflate
) {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun initViews() {
        showToolbar(getString(R.string.admin_dashboard_title))
        hideBottomNav()

        // Display current user info
        val session = SessionManager.getInstance()
        val user = session.currentUser.value
        val email = user?.email ?: "Unknown"
        val name = user?.fullName ?: "Admin"
        binding.tvUserInfo.text = "Logged in as: $name\n$email"
    }

    override fun setupListeners() {
        binding.btnSignOut.setDebouncedClickListener {
            authViewModel.signOut()
            // Navigate back to login, clearing entire back stack
            navigateTo(R.id.action_global_login)
        }
    }
}
