package com.example.movexa.ui.dashboard.manager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.databinding.BottomSheetChangePasswordBinding
import com.example.movexa.databinding.BottomSheetEditProfileBinding
import com.example.movexa.databinding.FragmentManagerProfileBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.clearErrorOnTextChange
import com.example.movexa.utils.hideKeyboard
import com.example.movexa.utils.setDebouncedClickListener
import com.example.movexa.utils.trimmedText
import com.example.movexa.utils.HapticManager
import com.example.movexa.utils.addLiftOnTouch
import com.example.movexa.utils.addPressScale
import com.example.movexa.utils.bounceIn
import com.example.movexa.utils.fadeSlideIn
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-featured Manager Profile screen.
 *
 * ┌──────────────────────────────────────────────────┐
 * │  PROFILE HEADER (gradient, avatar, name, badge)  │
 * ├──────────────────────────────────────────────────┤
 * │  ACCOUNT INFORMATION (name, email, phone, since) │
 * ├──────────────────────────────────────────────────┤
 * │  QUICK ACTIONS                                    │
 * │    • Edit Profile  → BottomSheetDialog            │
 * │    • Change Pass.  → BottomSheetDialog            │
 * │    • Activity Sum. → Info Snackbar (placeholder)  │
 * │    • Logout        → MaterialAlertDialog → Login  │
 * ├──────────────────────────────────────────────────┤
 * │  APP INFO (version, privacy, terms)               │
 * └──────────────────────────────────────────────────┘
 *
 * Features:
 * - Staggered card entrance animations
 * - Haptic feedback on actions
 * - Edit Profile & Change Password bottom sheets
 * - Logout confirmation dialog with session clearing
 * - Real-time state observation via StateFlow
 */
class ManagerProfileFragment : BaseFragment<FragmentManagerProfileBinding>(
    FragmentManagerProfileBinding::inflate
) {

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: ManagerProfileViewModel by viewModels()

    // ─── Bottom Sheet Dialogs ───────────────────────────────────
    private var editProfileSheet: BottomSheetDialog? = null
    private var changePasswordSheet: BottomSheetDialog? = null
    private var editProfileSheetBinding: BottomSheetEditProfileBinding? = null
    private var changePasswordSheetBinding: BottomSheetChangePasswordBinding? = null

    // ─── Date Formatter ─────────────────────────────────────────
    private val dateFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val lastActiveFormatter = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())

    // ─── Lifecycle ──────────────────────────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Load profile if not already loaded
        if (viewModel.profileState.value is ResultState.Idle) {
            viewModel.loadProfile()
        }
    }

    override fun onDestroyView() {
        editProfileSheet?.dismiss()
        editProfileSheet = null
        editProfileSheetBinding = null
        changePasswordSheet?.dismiss()
        changePasswordSheet = null
        changePasswordSheetBinding = null
        super.onDestroyView()
    }

    // ─── Init Views ─────────────────────────────────────────────

    override fun initViews() {
        // Populate from cached user immediately
        viewModel.cachedUser?.let { populateProfile(it) }

        // Entrance animations
        animateCardEntrance()
    }

    // ─── Setup Listeners ────────────────────────────────────────

    override fun setupListeners() {
        with(binding) {
            // ── Profile Header ───────────────────────────
            cardProfileHeader.addLiftOnTouch()
            ivEditAvatar.setDebouncedClickListener {
                HapticManager.light(it)
                showInfo("Photo upload coming soon")
            }

            // ── Quick Actions ────────────────────────────
            actionEditProfile.setDebouncedClickListener {
                HapticManager.light(it)
                showEditProfileBottomSheet()
            }
            actionEditProfile.addPressScale()

            actionChangePassword.setDebouncedClickListener {
                HapticManager.light(it)
                showChangePasswordBottomSheet()
            }
            actionChangePassword.addPressScale()

            actionActivitySummary.setDebouncedClickListener {
                HapticManager.light(it)
                showInfo("Activity summary coming soon")
            }
            actionActivitySummary.addPressScale()

            actionLogout.setDebouncedClickListener {
                HapticManager.medium(it)
                showLogoutConfirmation()
            }
            actionLogout.addPressScale()

            // ── App Info ─────────────────────────────────
            actionPrivacyPolicy.setDebouncedClickListener {
                HapticManager.light(it)
                showInfo("Privacy policy coming soon")
            }
            actionPrivacyPolicy.addPressScale()

            actionTerms.setDebouncedClickListener {
                HapticManager.light(it)
                showInfo("Terms of service coming soon")
            }
            actionTerms.addPressScale()
        }
    }

    // ─── Observe Data ───────────────────────────────────────────

    override fun observeData() {
        // Profile state
        collectLatestFlow(viewModel.profileState) { state ->
            handleProfileState(state)
        }

        // Edit profile state
        collectLatestFlow(viewModel.editProfileState) { state ->
            handleEditProfileState(state)
        }

        // Change password state
        collectLatestFlow(viewModel.changePasswordState) { state ->
            handleChangePasswordState(state)
        }

        // Logout state
        collectLatestFlow(viewModel.logoutState) { state ->
            handleLogoutState(state)
        }

        // ViewModel error/success events
        collectFlow(viewModel.errorEvent) { message ->
            showError(message)
        }

        collectFlow(viewModel.successEvent) { message ->
            showSuccess(message)
        }

        // Loading state
        collectLatestFlow(viewModel.isLoading) { isLoading ->
            if (isLoading) showLoading() else hideLoading()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STATE HANDLERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Handle profile loading state changes.
     */
    private fun handleProfileState(state: ResultState<User>) {
        when (state) {
            is ResultState.Loading -> {
                binding.progressLoading.visibility = View.VISIBLE
            }
            is ResultState.Success -> {
                binding.progressLoading.visibility = View.GONE
                populateProfile(state.data)
            }
            is ResultState.Error -> {
                binding.progressLoading.visibility = View.GONE
                showError(state.message)
            }
            is ResultState.Idle -> {
                // No-op
            }
        }
    }

    /**
     * Handle edit profile result state.
     */
    private fun handleEditProfileState(state: ResultState<User>) {
        when (state) {
            is ResultState.Loading -> {
                setEditProfileLoading(true)
            }
            is ResultState.Success -> {
                setEditProfileLoading(false)
                populateProfile(state.data)
                editProfileSheet?.dismiss()
                viewModel.resetEditProfileState()
            }
            is ResultState.Error -> {
                setEditProfileLoading(false)
                // Don't dismiss — show error inline
            }
            is ResultState.Idle -> {
                // No-op
            }
        }
    }

    /**
     * Handle change password result state.
     */
    private fun handleChangePasswordState(state: ResultState<Unit>) {
        when (state) {
            is ResultState.Loading -> {
                setChangePasswordLoading(true)
            }
            is ResultState.Success -> {
                setChangePasswordLoading(false)
                changePasswordSheet?.dismiss()
                viewModel.resetChangePasswordState()
            }
            is ResultState.Error -> {
                setChangePasswordLoading(false)
            }
            is ResultState.Idle -> {
                // No-op
            }
        }
    }

    /**
     * Handle logout state — navigate to login on success.
     */
    private fun handleLogoutState(state: ResultState<Unit>) {
        when (state) {
            is ResultState.Loading -> {
                showLoading()
            }
            is ResultState.Success -> {
                hideLoading()
                navigateToLogin()
            }
            is ResultState.Error -> {
                hideLoading()
                showError(state.message)
                viewModel.resetLogoutState()
            }
            is ResultState.Idle -> {
                // No-op
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UI POPULATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Populate all profile UI fields from a [User] object.
     */
    private fun populateProfile(user: User) {
        with(binding) {
            // Header
            tvAvatarInitials.text = user.initials
            tvProfileName.text = user.displayName
            tvRoleBadge.text = user.role.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }

            // Account info
            tvInfoName.text = user.fullName.ifBlank { getString(R.string.profile_not_set) }
            tvInfoEmail.text = user.email
            tvInfoPhone.text = user.phone.ifBlank { getString(R.string.profile_not_set) }

            // Email verified badge
            ivEmailVerified.visibility = if (user.isVerified) View.VISIBLE else View.GONE
            tvInfoEmailLabel.text = if (user.isVerified) {
                getString(R.string.profile_label_email_verified)
            } else {
                getString(R.string.profile_label_email)
            }

            // Member since
            tvInfoMemberSince.text = if (user.createdAt > 0) {
                dateFormatter.format(Date(user.createdAt))
            } else {
                getString(R.string.profile_not_set)
            }

            // App version
            try {
                val packageInfo = requireContext().packageManager
                    .getPackageInfo(requireContext().packageName, 0)
                tvAppVersion.text = "v${packageInfo.versionName}"
            } catch (e: Exception) {
                tvAppVersion.text = getString(R.string.profile_app_version_value)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // EDIT PROFILE BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    /**
     * Show the edit profile bottom sheet pre-populated with current values.
     */
    private fun showEditProfileBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetEditProfileBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Pre-populate fields
        val currentUser = viewModel.cachedUser
        sheetBinding.etEditName.setText(currentUser?.fullName ?: "")
        sheetBinding.etEditPhone.setText(currentUser?.phone ?: "")

        // Clear errors on text change
        sheetBinding.tilEditName.clearErrorOnTextChange()
        sheetBinding.tilEditPhone.clearErrorOnTextChange()

        // Save button
        sheetBinding.btnSaveProfile.setDebouncedClickListener { btn ->
            HapticManager.light(btn)
            val name = sheetBinding.etEditName.trimmedText()
            val phone = sheetBinding.etEditPhone.trimmedText()

            // Inline validation
            var isValid = true
            if (name.isBlank()) {
                sheetBinding.tilEditName.error = getString(R.string.error_field_required)
                isValid = false
            } else if (name.length < 2) {
                sheetBinding.tilEditName.error = getString(R.string.error_name_too_short)
                isValid = false
            }

            if (isValid) {
                hideKeyboard()
                viewModel.updateProfile(name, phone)
            }
        }

        // Cancel button
        sheetBinding.btnCancelEdit.setDebouncedClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            viewModel.resetEditProfileState()
            editProfileSheet = null
            editProfileSheetBinding = null
        }

        editProfileSheet = dialog
        editProfileSheetBinding = sheetBinding
        dialog.show()

        // Animate sheet content
        sheetBinding.root.fadeSlideIn()
    }

    /**
     * Toggle loading state on the edit profile bottom sheet.
     */
    private fun setEditProfileLoading(loading: Boolean) {
        val sb = editProfileSheetBinding ?: return
        sb.progressEditProfile.visibility = if (loading) View.VISIBLE else View.GONE
        sb.btnSaveProfile.isEnabled = !loading
        sb.etEditName.isEnabled = !loading
        sb.etEditPhone.isEnabled = !loading
    }

    // ═══════════════════════════════════════════════════════════
    // CHANGE PASSWORD BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    /**
     * Show the change password bottom sheet.
     */
    private fun showChangePasswordBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetChangePasswordBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Clear errors on text change
        sheetBinding.tilCurrentPassword.clearErrorOnTextChange()
        sheetBinding.tilNewPassword.clearErrorOnTextChange()
        sheetBinding.tilConfirmPassword.clearErrorOnTextChange()

        // Update password button
        sheetBinding.btnUpdatePassword.setDebouncedClickListener { btn ->
            HapticManager.light(btn)
            val currentPass = sheetBinding.etCurrentPassword.trimmedText()
            val newPass = sheetBinding.etNewPassword.trimmedText()
            val confirmPass = sheetBinding.etConfirmPassword.trimmedText()

            // Inline validation
            var isValid = true

            if (currentPass.isBlank()) {
                sheetBinding.tilCurrentPassword.error = getString(R.string.error_field_required)
                isValid = false
            }
            if (newPass.isBlank()) {
                sheetBinding.tilNewPassword.error = getString(R.string.error_field_required)
                isValid = false
            } else if (newPass.length < 6) {
                sheetBinding.tilNewPassword.error = getString(R.string.error_password_short)
                isValid = false
            }
            if (confirmPass.isBlank()) {
                sheetBinding.tilConfirmPassword.error = getString(R.string.error_field_required)
                isValid = false
            } else if (newPass != confirmPass) {
                sheetBinding.tilConfirmPassword.error = getString(R.string.error_password_mismatch)
                isValid = false
            }

            if (isValid) {
                hideKeyboard()
                viewModel.changePassword(currentPass, newPass, confirmPass)
            }
        }

        // Cancel button
        sheetBinding.btnCancelPassword.setDebouncedClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            viewModel.resetChangePasswordState()
            changePasswordSheet = null
            changePasswordSheetBinding = null
        }

        changePasswordSheet = dialog
        changePasswordSheetBinding = sheetBinding
        dialog.show()

        // Animate sheet content
        sheetBinding.root.fadeSlideIn()
    }

    /**
     * Toggle loading state on the change password bottom sheet.
     */
    private fun setChangePasswordLoading(loading: Boolean) {
        val sb = changePasswordSheetBinding ?: return
        sb.progressChangePassword.visibility = if (loading) View.VISIBLE else View.GONE
        sb.btnUpdatePassword.isEnabled = !loading
        sb.etCurrentPassword.isEnabled = !loading
        sb.etNewPassword.isEnabled = !loading
        sb.etConfirmPassword.isEnabled = !loading
    }

    // ═══════════════════════════════════════════════════════════
    // LOGOUT CONFIRMATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Show a Material confirmation dialog for logout.
     */
    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.logout_confirm_title))
            .setMessage(getString(R.string.logout_confirm_message))
            .setPositiveButton(getString(R.string.logout_confirm_yes)) { dialog, _ ->
                dialog.dismiss()
                HapticManager.mediumVibrate(requireContext())
                viewModel.logout()
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
            // Use the parent NavController (nav_graph level) for global action
            val navController = requireActivity().let { activity ->
                androidx.navigation.Navigation.findNavController(
                    activity, R.id.navHostFragment
                )
            }
            navController.navigate(R.id.action_global_login)
        } catch (e: Exception) {
            // Fallback — try from fragment's own nav controller
            try {
                findNavController().navigate(R.id.action_global_login)
            } catch (e2: Exception) {
                showError("Navigation error. Please restart the app.")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ANIMATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Staggered card entrance animation on initial load.
     */
    private fun animateCardEntrance() {
        with(binding) {
            // Start invisible
            cardProfileHeader.alpha = 0f
            cardAccountInfo.alpha = 0f
            cardQuickActions.alpha = 0f
            cardAppInfo.alpha = 0f
            tvSectionAccount.alpha = 0f
            tvSectionActions.alpha = 0f
            tvSectionAppInfo.alpha = 0f

            // Header — bounceIn for avatar + fadeSlideIn for card
            avatarContainer.bounceIn(delay = 100)
            cardProfileHeader.fadeSlideIn(delay = 50)

            // Account section
            tvSectionAccount.fadeSlideIn(delay = 200)
            cardAccountInfo.fadeSlideIn(delay = 250)

            // Quick actions section
            tvSectionActions.fadeSlideIn(delay = 350)
            cardQuickActions.fadeSlideIn(delay = 400)

            // App info section
            tvSectionAppInfo.fadeSlideIn(delay = 500)
            cardAppInfo.fadeSlideIn(delay = 550)
        }
    }
}
