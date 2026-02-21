package com.example.movexa.ui.dashboard

import androidx.fragment.app.activityViewModels
import com.example.movexa.R
import com.example.movexa.data.session.SessionManager
import com.example.movexa.databinding.FragmentDriverProfileBinding
import com.example.movexa.ui.auth.AuthViewModel
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.setDebouncedClickListener

/**
 * Profile screen for unverified Driver accounts.
 * Shows a pending verification message and allows sign-out.
 *
 * When the driver's account is verified by an admin,
 * subsequent logins will route to DriverHomeFragment instead.
 */
class DriverProfileFragment : BaseFragment<FragmentDriverProfileBinding>(
    FragmentDriverProfileBinding::inflate
) {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun initViews() {
        showToolbar(getString(R.string.driver_profile_title))
        hideBottomNav()

        val session = SessionManager.getInstance()
        val user = session.currentUser.value
        val email = user?.email ?: "Unknown"
        val name = user?.fullName ?: "Driver"
        binding.tvUserInfo.text = "Logged in as: $name\n$email\nStatus: Pending Verification"
    }

    override fun setupListeners() {
        binding.btnSignOut.setDebouncedClickListener {
            authViewModel.signOut()
            navigateTo(R.id.action_global_login)
        }
    }
}
