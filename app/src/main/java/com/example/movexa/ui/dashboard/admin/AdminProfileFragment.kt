package com.example.movexa.ui.dashboard.admin

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.databinding.BottomSheetChangePasswordBinding
import com.example.movexa.databinding.BottomSheetCompanySettingsBinding
import com.example.movexa.databinding.BottomSheetEditProfileBinding
import com.example.movexa.databinding.FragmentAdminProfileBinding
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
 * Full-featured Admin Profile screen.
 *
 * ┌──────────────────────────────────────────────────┐
 * │  EXECUTIVE HEADER (gradient, avatar, name, badge,│
 * │  company, status)                                │
 * ├──────────────────────────────────────────────────┤
 * │  PERSONAL INFORMATION (name, email, phone,       │
 * │  member since, user ID)                          │
 * ├──────────────────────────────────────────────────┤
 * │  COMPANY SETTINGS (name, phone, email, GST)      │
 * │    → Edit via BottomSheetDialog                  │
 * ├──────────────────────────────────────────────────┤
 * │  SYSTEM CONTROL (maintenance, notifications,     │
 * │  GPS tracking, auto-assign toggles)              │
 * ├──────────────────────────────────────────────────┤
 * │  SECURITY CONTROLS                                │
 * │    • Edit Profile  → BottomSheetDialog            │
 * │    • Change Pass.  → BottomSheetDialog            │
 * │    • Active Sessions → Info placeholder           │
 * │    • Logout        → MaterialAlertDialog → Login  │
 * ├──────────────────────────────────────────────────┤
 * │  AUDIT PREVIEW (recent 5 audit log entries)       │
 * ├──────────────────────────────────────────────────┤
 * │  APP INFO (version, privacy, terms)               │
 * └──────────────────────────────────────────────────┘
 *
 * Features:
 * - Staggered card entrance animations
 * - Haptic feedback on actions
 * - Edit Profile & Change Password bottom sheets
 * - Company Settings bottom sheet
 * - System toggles with Firestore persistence
 * - Dynamic audit log preview
 * - Logout confirmation dialog with session clearing
 * - Real-time state observation via StateFlow
 */
class AdminProfileFragment : BaseFragment<FragmentAdminProfileBinding>(
    FragmentAdminProfileBinding::inflate
) {

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: AdminProfileViewModel by viewModels()

    // ─── Bottom Sheet Dialogs ───────────────────────────────────
    private var editProfileSheet: BottomSheetDialog? = null
    private var changePasswordSheet: BottomSheetDialog? = null
    private var companySettingsSheet: BottomSheetDialog? = null
    private var editProfileSheetBinding: BottomSheetEditProfileBinding? = null
    private var changePasswordSheetBinding: BottomSheetChangePasswordBinding? = null
    private var companySettingsSheetBinding: BottomSheetCompanySettingsBinding? = null

    // ─── Date Formatters ────────────────────────────────────────
    private val dateFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val auditDateFormatter = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())

    // ─── Toggle Suppression Flag ────────────────────────────────
    private var suppressToggleCallbacks = false

    // ─── Lifecycle ──────────────────────────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Load profile if not already loaded
        if (viewModel.profileState.value is ResultState.Idle) {
            viewModel.loadProfile()
        }
        if (viewModel.companySettingsState.value is ResultState.Idle) {
            viewModel.loadCompanySettings()
        }
        if (viewModel.auditLogsState.value is ResultState.Idle) {
            viewModel.loadAuditLogs()
        }
    }

    override fun onDestroyView() {
        editProfileSheet?.dismiss()
        editProfileSheet = null
        editProfileSheetBinding = null
        changePasswordSheet?.dismiss()
        changePasswordSheet = null
        changePasswordSheetBinding = null
        companySettingsSheet?.dismiss()
        companySettingsSheet = null
        companySettingsSheetBinding = null
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

            // ── Company Settings ─────────────────────────
            actionCompanySettings.setDebouncedClickListener {
                HapticManager.light(it)
                showCompanySettingsBottomSheet()
            }
            actionCompanySettings.addPressScale()

            // ── System Toggles ───────────────────────────
            switchMaintenance.setOnCheckedChangeListener { _, isChecked ->
                if (!suppressToggleCallbacks) {
                    HapticManager.lightVibrate(requireContext())
                    viewModel.toggleSystemSetting("maintenanceMode", isChecked)
                }
            }
            switchNotifications.setOnCheckedChangeListener { _, isChecked ->
                if (!suppressToggleCallbacks) {
                    HapticManager.lightVibrate(requireContext())
                    viewModel.toggleSystemSetting("pushNotifications", isChecked)
                }
            }
            switchTracking.setOnCheckedChangeListener { _, isChecked ->
                if (!suppressToggleCallbacks) {
                    HapticManager.lightVibrate(requireContext())
                    viewModel.toggleSystemSetting("gpsTracking", isChecked)
                }
            }
            switchAutoAssign.setOnCheckedChangeListener { _, isChecked ->
                if (!suppressToggleCallbacks) {
                    HapticManager.lightVibrate(requireContext())
                    viewModel.toggleSystemSetting("autoAssignTrips", isChecked)
                }
            }

            // ── Security Actions ─────────────────────────
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

            actionActiveSessions.setDebouncedClickListener {
                HapticManager.light(it)
                showInfo("Active sessions management coming soon")
            }
            actionActiveSessions.addPressScale()

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

        // Company settings state
        collectLatestFlow(viewModel.companySettingsState) { state ->
            handleCompanySettingsState(state)
        }

        // Company settings update state
        collectLatestFlow(viewModel.companySettingsUpdateState) { state ->
            handleCompanySettingsUpdateState(state)
        }

        // Audit logs state
        collectLatestFlow(viewModel.auditLogsState) { state ->
            handleAuditLogsState(state)
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
            is ResultState.Idle -> { /* No-op */ }
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
            }
            is ResultState.Idle -> { /* No-op */ }
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
            is ResultState.Idle -> { /* No-op */ }
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
            is ResultState.Idle -> { /* No-op */ }
        }
    }

    /**
     * Handle company settings load state.
     */
    private fun handleCompanySettingsState(state: ResultState<Map<String, Any>>) {
        when (state) {
            is ResultState.Success -> {
                populateCompanySettings(state.data)
                populateSystemToggles(state.data)
            }
            is ResultState.Error -> {
                // Silently fail — company settings are optional
            }
            else -> { /* No-op */ }
        }
    }

    /**
     * Handle company settings update state.
     */
    private fun handleCompanySettingsUpdateState(state: ResultState<Unit>) {
        when (state) {
            is ResultState.Loading -> {
                setCompanySettingsLoading(true)
            }
            is ResultState.Success -> {
                setCompanySettingsLoading(false)
                companySettingsSheet?.dismiss()
                viewModel.resetCompanySettingsUpdateState()
            }
            is ResultState.Error -> {
                setCompanySettingsLoading(false)
            }
            is ResultState.Idle -> { /* No-op */ }
        }
    }

    /**
     * Handle audit logs state.
     */
    private fun handleAuditLogsState(state: ResultState<List<Map<String, Any>>>) {
        when (state) {
            is ResultState.Loading -> {
                binding.progressAudit.visibility = View.VISIBLE
                binding.tvAuditEmpty.visibility = View.GONE
            }
            is ResultState.Success -> {
                binding.progressAudit.visibility = View.GONE
                populateAuditLogs(state.data)
            }
            is ResultState.Error -> {
                binding.progressAudit.visibility = View.GONE
                binding.tvAuditEmpty.visibility = View.VISIBLE
            }
            is ResultState.Idle -> { /* No-op */ }
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
            tvRoleBadge.text = getString(R.string.admin_profile_role_label).uppercase()

            // Status badge
            if (user.isActive) {
                tvStatusBadge.text = "● ${getString(R.string.admin_profile_status_active)}"
            } else {
                tvStatusBadge.text = "● ${getString(R.string.admin_profile_status_inactive)}"
            }

            // Personal info
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

            // User ID (truncated)
            tvInfoUid.text = user.uid.take(12) + if (user.uid.length > 12) "..." else ""

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

    /**
     * Populate company settings fields.
     */
    private fun populateCompanySettings(settings: Map<String, Any>) {
        with(binding) {
            val name = settings["companyName"]?.toString()
            tvCompanySettingsName.text = if (!name.isNullOrBlank()) {
                name
            } else {
                getString(R.string.admin_profile_company_name_desc)
            }

            tvCompanyPhone.text = settings["companyPhone"]?.toString()?.ifBlank {
                getString(R.string.profile_not_set)
            } ?: getString(R.string.profile_not_set)

            tvCompanyEmail.text = settings["companyEmail"]?.toString()?.ifBlank {
                getString(R.string.profile_not_set)
            } ?: getString(R.string.profile_not_set)

            tvCompanyGst.text = settings["gstNumber"]?.toString()?.ifBlank {
                getString(R.string.profile_not_set)
            } ?: getString(R.string.profile_not_set)
        }
    }

    /**
     * Populate system toggle states from settings map.
     */
    private fun populateSystemToggles(settings: Map<String, Any>) {
        suppressToggleCallbacks = true
        with(binding) {
            switchMaintenance.isChecked = settings["maintenanceMode"] as? Boolean ?: false
            switchNotifications.isChecked = settings["pushNotifications"] as? Boolean ?: true
            switchTracking.isChecked = settings["gpsTracking"] as? Boolean ?: true
            switchAutoAssign.isChecked = settings["autoAssignTrips"] as? Boolean ?: false
        }
        suppressToggleCallbacks = false
    }

    /**
     * Populate audit log entries dynamically.
     */
    private fun populateAuditLogs(logs: List<Map<String, Any>>) {
        val container = binding.auditLogsContainer
        container.removeAllViews()

        if (logs.isEmpty()) {
            binding.tvAuditEmpty.visibility = View.VISIBLE
            return
        }

        binding.tvAuditEmpty.visibility = View.GONE

        logs.forEachIndexed { index, log ->
            val action = log["action"]?.toString() ?: "Unknown action"
            val details = log["details"]?.toString() ?: ""
            val timestamp = (log["timestamp"] as? Number)?.toLong() ?: 0L
            val performedBy = log["performedBy"]?.toString() ?: ""

            // Create audit log row
            val row = createAuditLogRow(action, details, timestamp, performedBy)
            container.addView(row)

            // Add divider between rows (not after last)
            if (index < logs.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(R.dimen.divider_height)
                    ).apply {
                        marginStart = resources.getDimensionPixelSize(R.dimen.spacing_xxl)
                    }
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                }
                container.addView(divider)
            }
        }
    }

    /**
     * Create a single audit log row view.
     */
    private fun createAuditLogRow(
        action: String,
        details: String,
        timestamp: Long,
        performedBy: String
    ): LinearLayout {
        val ctx = requireContext()
        val dp = { value: Int -> (value * resources.displayMetrics.density).toInt() }

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.card_padding),
                resources.getDimensionPixelSize(R.dimen.spacing_medium),
                resources.getDimensionPixelSize(R.dimen.card_padding),
                resources.getDimensionPixelSize(R.dimen.spacing_medium)
            )

            // Timeline dot icon
            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                setImageResource(R.drawable.ic_schedule)
                setColorFilter(ContextCompat.getColor(ctx, R.color.info))
                contentDescription = getString(R.string.cd_admin_audit_log)
            }
            addView(icon)

            // Text container
            val textContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    marginStart = resources.getDimensionPixelSize(R.dimen.spacing_medium)
                }
            }

            // Action text — formatted label
            val actionLabel = action.replace("_", " ")
                .replaceFirstChar { it.uppercase() }
            val actionText = TextView(ctx).apply {
                text = actionLabel
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            textContainer.addView(actionText)

            // Details + timestamp
            val timeStr = if (timestamp > 0) {
                auditDateFormatter.format(Date(timestamp))
            } else {
                ""
            }
            val subtitleText = if (details.isNotBlank()) {
                "$details — $timeStr"
            } else {
                timeStr
            }
            val subtitle = TextView(ctx).apply {
                text = subtitleText
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                textSize = 12f
                maxLines = 2
            }
            textContainer.addView(subtitle)

            addView(textContainer)
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
    // COMPANY SETTINGS BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    /**
     * Show the company settings bottom sheet pre-populated with current values.
     */
    private fun showCompanySettingsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetCompanySettingsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Pre-populate from cached settings
        val settings = viewModel.cachedCompanySettings
        sheetBinding.etCompanyName.setText(settings["companyName"]?.toString() ?: "")
        sheetBinding.etCompanyAddress.setText(settings["companyAddress"]?.toString() ?: "")
        sheetBinding.etCompanyPhone.setText(settings["companyPhone"]?.toString() ?: "")
        sheetBinding.etCompanyEmail.setText(settings["companyEmail"]?.toString() ?: "")
        sheetBinding.etGstNumber.setText(settings["gstNumber"]?.toString() ?: "")

        // Clear errors on text change
        sheetBinding.tilCompanyName.clearErrorOnTextChange()
        sheetBinding.tilCompanyAddress.clearErrorOnTextChange()
        sheetBinding.tilCompanyPhone.clearErrorOnTextChange()
        sheetBinding.tilCompanyEmail.clearErrorOnTextChange()
        sheetBinding.tilGstNumber.clearErrorOnTextChange()

        // Save button
        sheetBinding.btnSaveCompanySettings.setDebouncedClickListener { btn ->
            HapticManager.light(btn)
            val name = sheetBinding.etCompanyName.trimmedText()
            val address = sheetBinding.etCompanyAddress.trimmedText()
            val phone = sheetBinding.etCompanyPhone.trimmedText()
            val email = sheetBinding.etCompanyEmail.trimmedText()
            val gst = sheetBinding.etGstNumber.trimmedText()

            // Inline validation
            var isValid = true
            if (name.isBlank()) {
                sheetBinding.tilCompanyName.error = getString(R.string.error_field_required)
                isValid = false
            }

            if (isValid) {
                hideKeyboard()
                viewModel.updateCompanySettings(name, address, phone, email, gst)
            }
        }

        // Cancel button
        sheetBinding.btnCancelCompanySettings.setDebouncedClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            viewModel.resetCompanySettingsUpdateState()
            companySettingsSheet = null
            companySettingsSheetBinding = null
        }

        companySettingsSheet = dialog
        companySettingsSheetBinding = sheetBinding
        dialog.show()

        // Animate sheet content
        sheetBinding.root.fadeSlideIn()
    }

    /**
     * Toggle loading state on the company settings bottom sheet.
     */
    private fun setCompanySettingsLoading(loading: Boolean) {
        val sb = companySettingsSheetBinding ?: return
        sb.progressCompanySettings.visibility = if (loading) View.VISIBLE else View.GONE
        sb.btnSaveCompanySettings.isEnabled = !loading
        sb.etCompanyName.isEnabled = !loading
        sb.etCompanyAddress.isEnabled = !loading
        sb.etCompanyPhone.isEnabled = !loading
        sb.etCompanyEmail.isEnabled = !loading
        sb.etGstNumber.isEnabled = !loading
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
            cardPersonalInfo.alpha = 0f
            cardCompanySettings.alpha = 0f
            cardSystemControl.alpha = 0f
            cardSecurityControls.alpha = 0f
            cardAuditPreview.alpha = 0f
            cardAppInfo.alpha = 0f
            tvSectionPersonal.alpha = 0f
            tvSectionCompany.alpha = 0f
            tvSectionSystem.alpha = 0f
            tvSectionSecurity.alpha = 0f
            tvSectionAudit.alpha = 0f
            tvSectionAppInfo.alpha = 0f

            // Header — bounceIn for avatar + fadeSlideIn for card
            avatarContainer.bounceIn(delay = 100)
            cardProfileHeader.fadeSlideIn(delay = 50)

            // Personal info section
            tvSectionPersonal.fadeSlideIn(delay = 200)
            cardPersonalInfo.fadeSlideIn(delay = 250)

            // Company settings section
            tvSectionCompany.fadeSlideIn(delay = 350)
            cardCompanySettings.fadeSlideIn(delay = 400)

            // System control section
            tvSectionSystem.fadeSlideIn(delay = 500)
            cardSystemControl.fadeSlideIn(delay = 550)

            // Security controls section
            tvSectionSecurity.fadeSlideIn(delay = 650)
            cardSecurityControls.fadeSlideIn(delay = 700)

            // Audit preview section
            tvSectionAudit.fadeSlideIn(delay = 800)
            cardAuditPreview.fadeSlideIn(delay = 850)

            // App info section
            tvSectionAppInfo.fadeSlideIn(delay = 950)
            cardAppInfo.fadeSlideIn(delay = 1000)
        }
    }
}
