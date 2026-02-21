package com.example.movexa.ui.dashboard

import androidx.fragment.app.activityViewModels
import com.example.movexa.R
import com.example.movexa.data.session.SessionManager
import com.example.movexa.databinding.FragmentDriverHomeBinding
import com.example.movexa.ui.auth.AuthViewModel
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.setDebouncedClickListener

/**
 * Home screen for verified Driver role.
 * Displays welcome message and sign-out functionality.
 * Will be replaced with full driver home (trips, navigation) in future implementation.
 */
class DriverHomeFragment : BaseFragment<FragmentDriverHomeBinding>(
    FragmentDriverHomeBinding::inflate
) {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun initViews() {
        showToolbar(getString(R.string.driver_home_title))
        hideBottomNav()

        val session = SessionManager.getInstance()
        val user = session.currentUser.value
        val email = user?.email ?: "Unknown"
        val name = user?.fullName ?: "Driver"
        binding.tvUserInfo.text = "Logged in as: $name\n$email"
    }

    override fun setupListeners() {
        binding.btnSignOut.setDebouncedClickListener {
            authViewModel.signOut()
            navigateTo(R.id.action_global_login)
        }
    }
}
