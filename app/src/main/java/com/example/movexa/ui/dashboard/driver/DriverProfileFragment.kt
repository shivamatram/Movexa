package com.example.movexa.ui.dashboard.driver

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.movexa.R
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VerificationStatus
import com.example.movexa.databinding.BottomSheetChangePasswordBinding
import com.example.movexa.databinding.BottomSheetDocumentUploadBinding
import com.example.movexa.databinding.BottomSheetEditDriverProfileBinding
import com.example.movexa.databinding.FragmentDriverProfileBinding
import com.example.movexa.ui.base.BaseFragment
import com.example.movexa.utils.HapticManager
import com.example.movexa.utils.addLiftOnTouch
import com.example.movexa.utils.addPressScale
import com.example.movexa.utils.bounceIn
import com.example.movexa.utils.clearErrorOnTextChange
import com.example.movexa.utils.fadeSlideIn
import com.example.movexa.utils.hideKeyboard
import com.example.movexa.utils.setDebouncedClickListener
import com.example.movexa.utils.trimmedText
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══════════════════════════════════════════════════════════════
 *  DRIVER PROFILE FRAGMENT
 * ═══════════════════════════════════════════════════════════════
 *
 * Production-grade profile screen for the Driver dashboard.
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │  PROFILE HEADER (gradient, avatar initials, name, badge,    │
 * │   verification badge, company, member since)                │
 * ├──────────────────────────────────────────────────────────────┤
 * │  VERIFICATION BANNER (status-specific colour & message)     │
 * ├──────────────────────────────────────────────────────────────┤
 * │  ACCOUNT INFORMATION (name, email, phone, license, vehicle, │
 * │   emergency contact, blood group, rating)                   │
 * ├──────────────────────────────────────────────────────────────┤
 * │  DOCUMENT VERIFICATION                                      │
 * │    • Driving License  → upload/replace + status + expiry    │
 * │    • ID Proof         → upload/replace + status             │
 * ├──────────────────────────────────────────────────────────────┤
 * │  QUICK ACTIONS                                              │
 * │    • Edit Profile   → BottomSheetDialog (4 fields)          │
 * │    • Change Password → BottomSheetDialog                    │
 * │    • Logout          → MaterialAlertDialog → Login          │
 * ├──────────────────────────────────────────────────────────────┤
 * │  APP INFO (version, privacy, terms)                         │
 * └──────────────────────────────────────────────────────────────┘
 *
 * Features:
 * - Document upload via camera or gallery with image compression
 * - Verification status banner with colour-coded messages
 * - Real-time Firestore listeners for profile/driver changes
 * - Staggered card entrance animations
 * - Haptic feedback on all interactive elements
 * - Full lifecycle safety (dismiss sheets in onDestroyView)
 * - State observation via StateFlow for 10+ states
 */
class DriverProfileFragment : BaseFragment<FragmentDriverProfileBinding>(
    FragmentDriverProfileBinding::inflate
) {

    companion object {
        private const val TAG = "DriverProfileFragment"
        private const val MAX_DECODE_SIZE = 1600 // px — decode cap for image preview

        private const val DOC_TYPE_LICENSE = "license"
        private const val DOC_TYPE_ID_PROOF = "idproof"
    }

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: DriverProfileViewModel by viewModels()

    // ─── Bottom Sheet Dialogs ───────────────────────────────────
    private var editProfileSheet: BottomSheetDialog? = null
    private var changePasswordSheet: BottomSheetDialog? = null
    private var documentUploadSheet: BottomSheetDialog? = null
    private var editProfileSheetBinding: BottomSheetEditDriverProfileBinding? = null
    private var changePasswordSheetBinding: BottomSheetChangePasswordBinding? = null
    private var documentUploadSheetBinding: BottomSheetDocumentUploadBinding? = null

    // ─── Document upload tracking ───────────────────────────────
    private var currentUploadType: String = DOC_TYPE_LICENSE

    // ─── Camera temp file URI ───────────────────────────────────
    private var cameraPhotoUri: Uri? = null

    // ─── Date Formatters ────────────────────────────────────────
    private val dateFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val expiryFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // ═══════════════════════════════════════════════════════════
    //  ACTIVITY RESULT CONTRACTS
    // ═══════════════════════════════════════════════════════════

    /**
     * Camera — captures full-resolution photo and decodes from
     * the temporary file URI stored in [cameraPhotoUri].
     */
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraPhotoUri != null) {
            val bitmap = decodeSampledBitmap(cameraPhotoUri!!)
            if (bitmap != null) {
                handleCapturedDocument(bitmap)
            } else {
                showError("Failed to load captured image")
            }
        }
    }

    /**
     * Gallery — picks a single image via Photo Picker
     * (Android 13+) or intent fallback.
     */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = decodeSampledBitmap(uri)
            if (bitmap != null) {
                handleCapturedDocument(bitmap)
            } else {
                showError("Failed to load selected image")
            }
        }
    }

    /**
     * Camera permission request result.
     */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            showError("Camera permission is required to take document photos")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════

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
        documentUploadSheet?.dismiss()
        documentUploadSheet = null
        documentUploadSheetBinding = null
        super.onDestroyView()
    }

    // ═══════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        // Populate from cached data immediately
        viewModel.cachedUser?.let { populateProfile(it) }
        viewModel.cachedDriver?.let { populateDriverInfo(it) }
        viewModel.cachedVehicle?.let { populateVehicle(it) }

        // Entrance animations
        animateCardEntrance()
    }

    // ═══════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═══════════════════════════════════════════════════════════

    override fun setupListeners() {
        with(binding) {
            // ── Profile Header ───────────────────────────
            cardProfileHeader.addLiftOnTouch()
            ivEditAvatar.setDebouncedClickListener {
                HapticManager.light(it)
                showInfo("Photo upload coming soon")
            }

            // ── Document Upload Areas ────────────────────
            frameLicenseUpload.setDebouncedClickListener {
                HapticManager.light(it)
                currentUploadType = DOC_TYPE_LICENSE
                showDocumentUploadSheet(getString(R.string.driver_doc_license_title))
            }

            frameIdProofUpload.setDebouncedClickListener {
                HapticManager.light(it)
                currentUploadType = DOC_TYPE_ID_PROOF
                showDocumentUploadSheet(getString(R.string.driver_doc_idproof_title))
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

    // ═══════════════════════════════════════════════════════════
    //  OBSERVE DATA
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        // User profile state
        collectLatestFlow(viewModel.profileState) { state ->
            handleProfileState(state)
        }

        // Driver-specific state
        collectLatestFlow(viewModel.driverState) { state ->
            handleDriverState(state)
        }

        // Vehicle state
        collectLatestFlow(viewModel.vehicleState) { state ->
            handleVehicleState(state)
        }

        // Edit profile result
        collectLatestFlow(viewModel.editProfileState) { state ->
            handleEditProfileState(state)
        }

        // Edit driver details result
        collectLatestFlow(viewModel.editDriverState) { state ->
            handleEditDriverState(state)
        }

        // Change password result
        collectLatestFlow(viewModel.changePasswordState) { state ->
            handleChangePasswordState(state)
        }

        // Logout result
        collectLatestFlow(viewModel.logoutState) { state ->
            handleLogoutState(state)
        }

        // License upload state
        collectLatestFlow(viewModel.licenseUploadState) { state ->
            handleLicenseUploadState(state)
        }

        // ID proof upload state
        collectLatestFlow(viewModel.idProofUploadState) { state ->
            handleIdProofUploadState(state)
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
            binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  STATE HANDLERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Handle user profile loading state.
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
     * Handle driver-specific profile state (license, verification, etc.).
     */
    private fun handleDriverState(state: ResultState<Driver>) {
        when (state) {
            is ResultState.Loading -> { /* Handled by main loading */ }
            is ResultState.Success -> {
                populateDriverInfo(state.data)
            }
            is ResultState.Error -> {
                showError(state.message)
            }
            is ResultState.Idle -> { /* No-op */ }
        }
    }

    /**
     * Handle assigned vehicle state.
     */
    private fun handleVehicleState(state: ResultState<Vehicle>) {
        when (state) {
            is ResultState.Success -> {
                populateVehicle(state.data)
            }
            is ResultState.Error -> {
                binding.tvInfoVehicle.text = getString(R.string.driver_profile_no_vehicle)
            }
            else -> { /* No-op */ }
        }
    }

    /**
     * Handle edit profile (user fields) result state.
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
     * Handle edit driver details (emergency, blood group) result state.
     */
    private fun handleEditDriverState(state: ResultState<Driver>) {
        when (state) {
            is ResultState.Loading -> {
                setEditProfileLoading(true)
            }
            is ResultState.Success -> {
                setEditProfileLoading(false)
                populateDriverInfo(state.data)
                // Don't dismiss yet — user updates handled together
                viewModel.resetEditDriverState()
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
     * Handle license upload state — show/hide progress on license card.
     */
    private fun handleLicenseUploadState(state: ResultState<String>) {
        when (state) {
            is ResultState.Loading -> {
                binding.progressLicenseUpload.visibility = View.VISIBLE
                binding.frameLicenseUpload.isEnabled = false
            }
            is ResultState.Success -> {
                binding.progressLicenseUpload.visibility = View.GONE
                binding.frameLicenseUpload.isEnabled = true
                // Show uploaded state
                binding.layoutLicenseEmpty.visibility = View.GONE
                binding.layoutLicenseUploaded.visibility = View.VISIBLE
                binding.tvLicenseUploadedLabel.text = getString(R.string.driver_doc_tap_replace)
                viewModel.resetLicenseUploadState()
            }
            is ResultState.Error -> {
                binding.progressLicenseUpload.visibility = View.GONE
                binding.frameLicenseUpload.isEnabled = true
                viewModel.resetLicenseUploadState()
            }
            is ResultState.Idle -> { /* No-op */ }
        }
    }

    /**
     * Handle ID proof upload state — show/hide progress on ID card.
     */
    private fun handleIdProofUploadState(state: ResultState<String>) {
        when (state) {
            is ResultState.Loading -> {
                binding.progressIdProofUpload.visibility = View.VISIBLE
                binding.frameIdProofUpload.isEnabled = false
            }
            is ResultState.Success -> {
                binding.progressIdProofUpload.visibility = View.GONE
                binding.frameIdProofUpload.isEnabled = true
                // Show uploaded state
                binding.layoutIdProofEmpty.visibility = View.GONE
                binding.layoutIdProofUploaded.visibility = View.VISIBLE
                binding.tvIdProofUploadedLabel.text = getString(R.string.driver_doc_tap_replace)
                viewModel.resetIdProofUploadState()
            }
            is ResultState.Error -> {
                binding.progressIdProofUpload.visibility = View.GONE
                binding.frameIdProofUpload.isEnabled = true
                viewModel.resetIdProofUploadState()
            }
            is ResultState.Idle -> { /* No-op */ }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  UI POPULATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Populate all user-related profile UI fields.
     */
    private fun populateProfile(user: User) {
        with(binding) {
            // Header
            tvAvatarInitials.text = user.initials
            tvProfileName.text = user.displayName
            tvRoleBadge.text = user.role.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
            tvCompanyName.text = getString(R.string.driver_profile_company)

            // Member since
            tvMemberSince.text = if (user.createdAt > 0) {
                getString(
                    R.string.driver_profile_member_since,
                    dateFormatter.format(Date(user.createdAt))
                )
            } else {
                getString(R.string.profile_not_set)
            }

            // Account info — user fields
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
     * Populate driver-specific information (license, verification,
     * emergency contact, documents, etc.).
     */
    private fun populateDriverInfo(driver: Driver) {
        with(binding) {
            // License number
            tvInfoLicenseNumber.text = driver.licenseNumber.ifBlank {
                getString(R.string.profile_not_set)
            }

            // Emergency contact
            tvInfoEmergency.text = driver.emergencyContact.ifBlank {
                getString(R.string.profile_not_set)
            }

            // Blood group
            tvInfoBloodGroup.text = driver.bloodGroup.ifBlank {
                getString(R.string.profile_not_set)
            }

            // Rating
            tvInfoRating.text = getString(
                R.string.driver_profile_rating_format,
                driver.ratingDisplay
            )

            // ── Verification Badge (Header) ──────────────
            updateVerificationBadge(driver.verificationStatus)

            // ── Verification Banner ──────────────────────
            updateVerificationBanner(driver.verificationStatus)

            // ── License Document State ───────────────────
            if (!driver.licenseUrl.isNullOrBlank()) {
                layoutLicenseEmpty.visibility = View.GONE
                layoutLicenseUploaded.visibility = View.VISIBLE
                tvLicenseUploadedLabel.text = getString(R.string.driver_doc_tap_replace)
            } else {
                layoutLicenseEmpty.visibility = View.VISIBLE
                layoutLicenseUploaded.visibility = View.GONE
            }

            // License status chip
            updateDocumentStatusChip(
                tvLicenseStatus,
                if (!driver.licenseUrl.isNullOrBlank()) driver.verificationStatus
                else null
            )

            // License expiry
            if (driver.licenseExpiry > 0) {
                tvLicenseExpiry.visibility = View.VISIBLE
                tvLicenseExpiry.text = getString(
                    R.string.driver_doc_license_expiry,
                    expiryFormatter.format(Date(driver.licenseExpiry))
                )
            } else {
                tvLicenseExpiry.visibility = View.GONE
            }

            // ── ID Proof Document State ──────────────────
            if (!driver.idProofUrl.isNullOrBlank()) {
                layoutIdProofEmpty.visibility = View.GONE
                layoutIdProofUploaded.visibility = View.VISIBLE
                tvIdProofUploadedLabel.text = getString(R.string.driver_doc_tap_replace)
            } else {
                layoutIdProofEmpty.visibility = View.VISIBLE
                layoutIdProofUploaded.visibility = View.GONE
            }

            // ID proof status chip
            updateDocumentStatusChip(
                tvIdProofStatus,
                if (!driver.idProofUrl.isNullOrBlank()) driver.verificationStatus
                else null
            )
        }
    }

    /**
     * Populate assigned vehicle information.
     */
    private fun populateVehicle(vehicle: Vehicle) {
        binding.tvInfoVehicle.text = vehicle.displayLabel
    }

    // ═══════════════════════════════════════════════════════════
    //  VERIFICATION UI
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the verification badge in the profile header.
     */
    private fun updateVerificationBadge(status: VerificationStatus) {
        with(binding.tvVerificationBadge) {
            visibility = View.VISIBLE
            when (status) {
                VerificationStatus.APPROVED -> {
                    text = getString(R.string.driver_profile_verified)
                    setBackgroundResource(R.drawable.bg_verification_approved)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_verified, 0, 0, 0
                    )
                }
                VerificationStatus.PENDING -> {
                    text = getString(R.string.driver_profile_pending)
                    setBackgroundResource(R.drawable.bg_verification_pending)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_pending, 0, 0, 0
                    )
                }
                VerificationStatus.UNDER_REVIEW -> {
                    text = getString(R.string.driver_profile_under_review)
                    setBackgroundResource(R.drawable.bg_verification_under_review)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_info, 0, 0, 0
                    )
                }
                VerificationStatus.REJECTED -> {
                    text = getString(R.string.driver_profile_rejected)
                    setBackgroundResource(R.drawable.bg_verification_rejected)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_warning, 0, 0, 0
                    )
                }
                VerificationStatus.EXPIRED -> {
                    text = getString(R.string.driver_profile_expired)
                    setBackgroundResource(R.drawable.bg_verification_rejected)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_warning, 0, 0, 0
                    )
                }
            }
        }
    }

    /**
     * Update the verification status banner card with
     * status-specific colour, icon, title, and message.
     */
    private fun updateVerificationBanner(status: VerificationStatus) {
        with(binding) {
            cardVerificationBanner.visibility = View.VISIBLE

            when (status) {
                VerificationStatus.APPROVED -> {
                    cardVerificationBanner.setCardBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.status_approved_bg)
                    )
                    ivBannerIcon.setImageResource(R.drawable.ic_check_circle)
                    ivBannerIcon.setColorFilter(
                        ContextCompat.getColor(requireContext(), R.color.status_approved)
                    )
                    tvBannerTitle.text = getString(R.string.driver_verification_approved_title)
                    tvBannerTitle.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_approved)
                    )
                    tvBannerMessage.text = getString(R.string.driver_verification_approved_message)
                }
                VerificationStatus.PENDING -> {
                    cardVerificationBanner.setCardBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.status_pending_bg)
                    )
                    ivBannerIcon.setImageResource(R.drawable.ic_pending)
                    ivBannerIcon.setColorFilter(
                        ContextCompat.getColor(requireContext(), R.color.status_pending)
                    )
                    tvBannerTitle.text = getString(R.string.driver_verification_pending_title)
                    tvBannerTitle.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_pending)
                    )
                    tvBannerMessage.text = getString(R.string.driver_verification_pending_message)
                }
                VerificationStatus.UNDER_REVIEW -> {
                    cardVerificationBanner.setCardBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.status_under_review_bg)
                    )
                    ivBannerIcon.setImageResource(R.drawable.ic_info)
                    ivBannerIcon.setColorFilter(
                        ContextCompat.getColor(requireContext(), R.color.status_under_review)
                    )
                    tvBannerTitle.text = getString(R.string.driver_verification_under_review_title)
                    tvBannerTitle.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_under_review)
                    )
                    tvBannerMessage.text = getString(R.string.driver_verification_under_review_message)
                }
                VerificationStatus.REJECTED -> {
                    cardVerificationBanner.setCardBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.status_rejected_bg)
                    )
                    ivBannerIcon.setImageResource(R.drawable.ic_warning)
                    ivBannerIcon.setColorFilter(
                        ContextCompat.getColor(requireContext(), R.color.status_rejected)
                    )
                    tvBannerTitle.text = getString(R.string.driver_verification_rejected_title)
                    tvBannerTitle.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_rejected)
                    )
                    tvBannerMessage.text = getString(R.string.driver_verification_rejected_message)
                }
                VerificationStatus.EXPIRED -> {
                    cardVerificationBanner.setCardBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.status_rejected_bg)
                    )
                    ivBannerIcon.setImageResource(R.drawable.ic_warning)
                    ivBannerIcon.setColorFilter(
                        ContextCompat.getColor(requireContext(), R.color.status_rejected)
                    )
                    tvBannerTitle.text = getString(R.string.driver_verification_expired_title)
                    tvBannerTitle.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_rejected)
                    )
                    tvBannerMessage.text = getString(R.string.driver_verification_expired_message)
                }
            }
        }
    }

    /**
     * Update a document status chip TextView with the appropriate
     * background and text based on the verification status.
     *
     * @param chipView The status chip TextView
     * @param status   Verification status, or null if not uploaded
     */
    private fun updateDocumentStatusChip(
        chipView: android.widget.TextView,
        status: VerificationStatus?
    ) {
        if (status == null) {
            chipView.text = getString(R.string.driver_doc_not_uploaded)
            chipView.setBackgroundResource(R.drawable.bg_verification_pending)
            return
        }

        chipView.text = status.displayName
        when (status) {
            VerificationStatus.APPROVED -> {
                chipView.setBackgroundResource(R.drawable.bg_verification_approved)
            }
            VerificationStatus.PENDING -> {
                chipView.setBackgroundResource(R.drawable.bg_verification_pending)
            }
            VerificationStatus.UNDER_REVIEW -> {
                chipView.setBackgroundResource(R.drawable.bg_verification_under_review)
            }
            VerificationStatus.REJECTED -> {
                chipView.setBackgroundResource(R.drawable.bg_verification_rejected)
            }
            VerificationStatus.EXPIRED -> {
                chipView.setBackgroundResource(R.drawable.bg_verification_rejected)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EDIT PROFILE BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    /**
     * Show the edit driver profile bottom sheet with 4 fields:
     * Full Name, Phone, Emergency Contact, Blood Group.
     *
     * User fields (name, phone) are saved via [DriverProfileViewModel.updateProfile].
     * Driver fields (emergency, blood group) via [DriverProfileViewModel.updateDriverDetails].
     */
    private fun showEditProfileBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetEditDriverProfileBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Pre-populate fields
        val currentUser = viewModel.cachedUser
        val currentDriver = viewModel.cachedDriver

        sheetBinding.etEditName.setText(currentUser?.fullName ?: "")
        sheetBinding.etEditPhone.setText(currentUser?.phone ?: "")
        sheetBinding.etEditEmergency.setText(currentDriver?.emergencyContact ?: "")
        sheetBinding.etEditBloodGroup.setText(currentDriver?.bloodGroup ?: "")

        // Clear errors on text change
        sheetBinding.tilEditName.clearErrorOnTextChange()
        sheetBinding.tilEditPhone.clearErrorOnTextChange()
        sheetBinding.tilEditEmergency.clearErrorOnTextChange()
        sheetBinding.tilEditBloodGroup.clearErrorOnTextChange()

        // Save button
        sheetBinding.btnSaveProfile.setDebouncedClickListener { btn ->
            HapticManager.light(btn)
            val name = sheetBinding.etEditName.trimmedText()
            val phone = sheetBinding.etEditPhone.trimmedText()
            val emergency = sheetBinding.etEditEmergency.trimmedText()
            val bloodGroup = sheetBinding.etEditBloodGroup.trimmedText()

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
                // Update user fields
                viewModel.updateProfile(name, phone)
                // Update driver fields if changed
                val driverChanged = emergency != (currentDriver?.emergencyContact ?: "") ||
                        bloodGroup != (currentDriver?.bloodGroup ?: "")
                if (driverChanged) {
                    viewModel.updateDriverDetails(emergency, bloodGroup)
                }
                // Dismiss handled by state observer on success
            }
        }

        // Cancel button
        sheetBinding.btnCancelEdit.setDebouncedClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            viewModel.resetEditProfileState()
            viewModel.resetEditDriverState()
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
        sb.etEditEmergency.isEnabled = !loading
        sb.etEditBloodGroup.isEnabled = !loading
    }

    // ═══════════════════════════════════════════════════════════
    //  CHANGE PASSWORD BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    /**
     * Show the change password bottom sheet (reuses shared layout).
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
    //  DOCUMENT UPLOAD BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    /**
     * Show the document upload chooser bottom sheet (camera/gallery).
     *
     * @param documentTitle Title to display (e.g. "Driving License")
     */
    private fun showDocumentUploadSheet(documentTitle: String) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetDocumentUploadBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvUploadTitle.text = getString(R.string.driver_doc_upload_title)
        sheetBinding.tvUploadSubtitle.text = getString(R.string.driver_doc_upload_subtitle)

        // Camera action
        sheetBinding.actionCamera.setDebouncedClickListener {
            HapticManager.light(it)
            dialog.dismiss()
            checkCameraPermissionAndLaunch()
        }
        sheetBinding.actionCamera.addPressScale()

        // Gallery action
        sheetBinding.actionGallery.setDebouncedClickListener {
            HapticManager.light(it)
            dialog.dismiss()
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        sheetBinding.actionGallery.addPressScale()

        // Cancel button
        sheetBinding.btnCancelUpload.setDebouncedClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            documentUploadSheet = null
            documentUploadSheetBinding = null
        }

        documentUploadSheet = dialog
        documentUploadSheetBinding = sheetBinding
        dialog.show()

        // Animate sheet content
        sheetBinding.root.fadeSlideIn()
    }

    // ═══════════════════════════════════════════════════════════
    //  CAMERA & GALLERY HANDLING
    // ═══════════════════════════════════════════════════════════

    /**
     * Check CAMERA permission and launch camera if granted,
     * otherwise request the permission.
     */
    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showInfo("Camera permission is needed to capture document photos")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Create a temporary file and launch the system camera.
     */
    private fun launchCamera() {
        try {
            val photoFile = File.createTempFile(
                "doc_${currentUploadType}_",
                ".jpg",
                requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            )

            cameraPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )

            takePictureLauncher.launch(cameraPhotoUri!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera", e)
            showError("Unable to open camera")
        }
    }

    /**
     * Route the captured/selected bitmap to the correct
     * ViewModel upload method based on [currentUploadType].
     */
    private fun handleCapturedDocument(bitmap: Bitmap) {
        when (currentUploadType) {
            DOC_TYPE_LICENSE -> viewModel.uploadLicense(bitmap)
            DOC_TYPE_ID_PROOF -> viewModel.uploadIdProof(bitmap)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  IMAGE DECODING
    // ═══════════════════════════════════════════════════════════

    /**
     * Decode a bitmap from a content URI with down-sampling
     * for memory safety.
     *
     * @param uri Content URI of the image
     * @return Decoded bitmap, or null on failure
     */
    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        return try {
            val contentResolver = requireContext().contentResolver

            // Pass 1: decode bounds only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                MAX_DECODE_SIZE,
                MAX_DECODE_SIZE
            )
            options.inJustDecodeBounds = false

            // Pass 2: decode at reduced resolution
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from $uri", e)
            null
        }
    }

    /**
     * Calculate the largest inSampleSize that keeps both
     * dimensions above the requested width/height.
     */
    private fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ═══════════════════════════════════════════════════════════
    //  LOGOUT CONFIRMATION
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

    // ═══════════════════════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════════════════════

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
    //  ANIMATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Staggered card entrance animation on initial load.
     */
    private fun animateCardEntrance() {
        with(binding) {
            // Start invisible
            cardProfileHeader.alpha = 0f
            cardVerificationBanner.alpha = 0f
            cardAccountInfo.alpha = 0f
            cardLicense.alpha = 0f
            cardIdProof.alpha = 0f
            cardQuickActions.alpha = 0f
            cardAppInfo.alpha = 0f
            tvSectionAccount.alpha = 0f
            tvSectionDocuments.alpha = 0f
            tvSectionActions.alpha = 0f
            tvSectionAppInfo.alpha = 0f

            // Header — bounceIn for avatar + fadeSlideIn for card
            avatarContainer.bounceIn(delay = 100)
            cardProfileHeader.fadeSlideIn(delay = 50)

            // Verification banner
            cardVerificationBanner.fadeSlideIn(delay = 150)

            // Account section
            tvSectionAccount.fadeSlideIn(delay = 200)
            cardAccountInfo.fadeSlideIn(delay = 250)

            // Documents section
            tvSectionDocuments.fadeSlideIn(delay = 350)
            cardLicense.fadeSlideIn(delay = 400)
            cardIdProof.fadeSlideIn(delay = 450)

            // Quick actions section
            tvSectionActions.fadeSlideIn(delay = 550)
            cardQuickActions.fadeSlideIn(delay = 600)

            // App info section
            tvSectionAppInfo.fadeSlideIn(delay = 700)
            cardAppInfo.fadeSlideIn(delay = 750)
        }
    }
}
