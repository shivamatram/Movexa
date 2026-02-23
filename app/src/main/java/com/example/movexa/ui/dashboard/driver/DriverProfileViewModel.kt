package com.example.movexa.ui.dashboard.driver

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.User
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.repository.DriverProfileRepository
import com.example.movexa.data.session.SessionManager
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * ViewModel for the Driver Profile screen.
 *
 * Manages:
 * - User profile loading from Firestore (with SessionManager cache fallback)
 * - Driver profile loading (license, ID proof, verification status)
 * - Assigned vehicle information
 * - Document upload (license & ID proof) with image compression
 * - Profile editing (fullName, phone, emergencyContact, bloodGroup)
 * - Password change via re-authentication
 * - Real-time Firestore listeners for user & driver docs
 * - Logout (clear session + Firebase sign-out)
 *
 * Uses [DriverProfileRepository] for Firebase operations and
 * [SessionManager] for cached session state.
 */
class DriverProfileViewModel : BaseViewModel() {

    private val repository = DriverProfileRepository()
    private val sessionManager = SessionManager.getInstance()

    // ─── Image Compression Constants ────────────────────────────

    companion object {
        private const val MAX_IMAGE_SIZE = 1024   // pixels
        private const val JPEG_QUALITY = 70       // percent
        private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024  // 5 MB
    }

    // ─── Profile States ─────────────────────────────────────────

    private val _profileState = MutableStateFlow<ResultState<User>>(ResultState.Idle)
    val profileState: StateFlow<ResultState<User>> = _profileState.asStateFlow()

    private val _driverState = MutableStateFlow<ResultState<Driver>>(ResultState.Idle)
    val driverState: StateFlow<ResultState<Driver>> = _driverState.asStateFlow()

    private val _vehicleState = MutableStateFlow<ResultState<Vehicle>>(ResultState.Idle)
    val vehicleState: StateFlow<ResultState<Vehicle>> = _vehicleState.asStateFlow()

    // ─── Operation States ───────────────────────────────────────

    private val _editProfileState = MutableStateFlow<ResultState<User>>(ResultState.Idle)
    val editProfileState: StateFlow<ResultState<User>> = _editProfileState.asStateFlow()

    private val _editDriverState = MutableStateFlow<ResultState<Driver>>(ResultState.Idle)
    val editDriverState: StateFlow<ResultState<Driver>> = _editDriverState.asStateFlow()

    private val _changePasswordState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val changePasswordState: StateFlow<ResultState<Unit>> = _changePasswordState.asStateFlow()

    private val _logoutState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val logoutState: StateFlow<ResultState<Unit>> = _logoutState.asStateFlow()

    // ─── Document Upload States ─────────────────────────────────

    private val _licenseUploadState = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val licenseUploadState: StateFlow<ResultState<String>> = _licenseUploadState.asStateFlow()

    private val _idProofUploadState = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val idProofUploadState: StateFlow<ResultState<String>> = _idProofUploadState.asStateFlow()

    // ─── Cached Data ────────────────────────────────────────────

    val cachedUser: User?
        get() = sessionManager.currentUser.value

    val sessionUser: StateFlow<User?> = sessionManager.currentUser

    private var cachedDriver: Driver? = null
    private var cachedVehicle: Vehicle? = null

    // ─── Real-time listener jobs ────────────────────────────────

    private var userListenerJob: Job? = null
    private var driverListenerJob: Job? = null

    // ═══════════════════════════════════════════════════════════
    // PROFILE LOADING
    // ═══════════════════════════════════════════════════════════

    /**
     * Load the driver's full profile:
     * 1. User profile from users/{uid}
     * 2. Driver profile from drivers/ where userId == uid
     * 3. Assigned vehicle from vehicles/{vehicleId}
     *
     * Uses SessionManager cache for immediate display, then
     * fetches fresh data from Firestore.
     */
    fun loadFullProfile() {
        loadUserProfile()
        loadDriverProfile()
    }

    /**
     * Load user profile from Firestore with cache fallback.
     */
    private fun loadUserProfile() {
        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = false,
            onError = { e ->
                _profileState.value = ResultState.Error(
                    message = e.message ?: "Failed to load profile"
                )
            }
        ) {
            _profileState.value = ResultState.Loading

            val uid = sessionManager.getCachedUserId()
                ?: repository.getCurrentUserId()
                ?: run {
                    _profileState.value = ResultState.Error("User not authenticated")
                    return@launchWithLoading
                }

            when (val result = repository.fetchUserProfile(uid)) {
                is ResultState.Success -> {
                    sessionManager.updateUser(result.data)
                    _profileState.value = result
                }
                is ResultState.Error -> {
                    val cached = sessionManager.currentUser.value
                    if (cached != null) {
                        _profileState.value = ResultState.Success(cached)
                        emitError("Using cached profile. ${result.message}")
                    } else {
                        _profileState.value = result
                    }
                }
                else -> _profileState.value = result
            }
        }
    }

    /**
     * Load driver-specific profile (license, ID proof, verification, etc.).
     */
    private fun loadDriverProfile() {
        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = false,
            onError = { e ->
                _driverState.value = ResultState.Error(
                    message = e.message ?: "Failed to load driver profile"
                )
            }
        ) {
            _driverState.value = ResultState.Loading

            val uid = sessionManager.getCachedUserId()
                ?: repository.getCurrentUserId()
                ?: run {
                    _driverState.value = ResultState.Error("User not authenticated")
                    return@launchWithLoading
                }

            when (val result = repository.fetchDriverProfile(uid)) {
                is ResultState.Success -> {
                    cachedDriver = result.data
                    _driverState.value = result

                    // Load assigned vehicle if available
                    result.data.assignedVehicleId?.let { vehicleId ->
                        loadAssignedVehicle(vehicleId)
                    }

                    // Start real-time listener for driver changes
                    startDriverListener(result.data.driverId)
                }
                is ResultState.Error -> {
                    _driverState.value = result
                }
                else -> _driverState.value = result
            }
        }
    }

    /**
     * Load the vehicle assigned to this driver.
     */
    private fun loadAssignedVehicle(vehicleId: String) {
        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = false,
            onError = { e ->
                _vehicleState.value = ResultState.Error(
                    message = e.message ?: "Failed to load vehicle"
                )
            }
        ) {
            when (val result = repository.fetchAssignedVehicle(vehicleId)) {
                is ResultState.Success -> {
                    cachedVehicle = result.data
                    _vehicleState.value = result
                }
                is ResultState.Error -> {
                    _vehicleState.value = result
                }
                else -> _vehicleState.value = result
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME LISTENERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Start real-time listener on the driver document for
     * verification status changes, document URL updates, etc.
     */
    private fun startDriverListener(driverId: String) {
        driverListenerJob?.cancel()
        driverListenerJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeDriverProfile(driverId).collect { result ->
                if (result is ResultState.Success) {
                    cachedDriver = result.data
                    _driverState.value = result

                    // Refresh vehicle if assignment changed
                    val newVehicleId = result.data.assignedVehicleId
                    if (newVehicleId != null && newVehicleId != cachedVehicle?.vehicleId) {
                        loadAssignedVehicle(newVehicleId)
                    }
                }
            }
        }
    }

    /**
     * Start real-time listener on the user document.
     */
    fun startUserListener() {
        userListenerJob?.cancel()
        userListenerJob = viewModelScope.launch(Dispatchers.IO) {
            val uid = sessionManager.getCachedUserId()
                ?: repository.getCurrentUserId()
                ?: return@launch
            repository.observeUserProfile(uid).collect { result ->
                if (result is ResultState.Success) {
                    sessionManager.updateUser(result.data)
                    _profileState.value = result
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT UPLOAD
    // ═══════════════════════════════════════════════════════════

    /**
     * Upload a driving license document.
     *
     * Compresses the bitmap, uploads to Firebase Storage,
     * and updates the driver record with the URL + resets
     * verification to PENDING.
     *
     * @param bitmap The license image from camera/gallery
     */
    fun uploadLicenseDocument(bitmap: Bitmap) {
        val driverId = cachedDriver?.driverId
        if (driverId.isNullOrBlank()) {
            emitError("Driver profile not loaded yet. Please wait.")
            return
        }

        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = true,
            onError = { e ->
                _licenseUploadState.value = ResultState.Error(
                    message = e.message ?: "Failed to upload license"
                )
                emitError("License upload failed. Please try again.")
            }
        ) {
            _licenseUploadState.value = ResultState.Loading

            val compressedBytes = compressBitmap(bitmap)

            when (val result = repository.uploadDocument(driverId, "license", compressedBytes)) {
                is ResultState.Success -> {
                    _licenseUploadState.value = result
                    emitSuccess("Driving license uploaded successfully")
                    // Driver listener will auto-refresh the driver state
                }
                is ResultState.Error -> {
                    _licenseUploadState.value = result
                    emitError(result.message)
                }
                else -> _licenseUploadState.value = result
            }
        }
    }

    /**
     * Upload an ID proof document.
     *
     * @param bitmap The ID proof image from camera/gallery
     */
    fun uploadIdProofDocument(bitmap: Bitmap) {
        val driverId = cachedDriver?.driverId
        if (driverId.isNullOrBlank()) {
            emitError("Driver profile not loaded yet. Please wait.")
            return
        }

        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = true,
            onError = { e ->
                _idProofUploadState.value = ResultState.Error(
                    message = e.message ?: "Failed to upload ID proof"
                )
                emitError("ID proof upload failed. Please try again.")
            }
        ) {
            _idProofUploadState.value = ResultState.Loading

            val compressedBytes = compressBitmap(bitmap)

            when (val result = repository.uploadDocument(driverId, "idproof", compressedBytes)) {
                is ResultState.Success -> {
                    _idProofUploadState.value = result
                    emitSuccess("ID proof uploaded successfully")
                }
                is ResultState.Error -> {
                    _idProofUploadState.value = result
                    emitError(result.message)
                }
                else -> _idProofUploadState.value = result
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PROFILE EDITING
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the driver's user profile fields (name, phone).
     *
     * @param fullName Updated full name
     * @param phone    Updated phone number
     */
    fun updateProfile(fullName: String, phone: String) {
        if (fullName.isBlank()) {
            emitError("Name cannot be empty")
            return
        }
        if (fullName.length < 2) {
            emitError("Name must be at least 2 characters")
            return
        }

        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = true,
            onError = { e ->
                _editProfileState.value = ResultState.Error(
                    message = e.message ?: "Failed to update profile"
                )
            }
        ) {
            _editProfileState.value = ResultState.Loading

            val uid = sessionManager.getCachedUserId()
                ?: repository.getCurrentUserId()
                ?: run {
                    _editProfileState.value = ResultState.Error("User not authenticated")
                    return@launchWithLoading
                }

            when (val result = repository.updateUserProfile(uid, fullName, phone)) {
                is ResultState.Success -> {
                    sessionManager.updateUser(result.data)
                    _editProfileState.value = result
                    _profileState.value = result
                    emitSuccess("Profile updated successfully")
                }
                is ResultState.Error -> {
                    _editProfileState.value = result
                    emitError(result.message)
                }
                else -> _editProfileState.value = result
            }
        }
    }

    /**
     * Update driver-specific details (emergency contact, blood group).
     *
     * @param emergencyContact Updated emergency contact number
     * @param bloodGroup       Updated blood group
     */
    fun updateDriverDetails(emergencyContact: String, bloodGroup: String) {
        val driverId = cachedDriver?.driverId
        if (driverId.isNullOrBlank()) {
            emitError("Driver profile not loaded yet.")
            return
        }

        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = true,
            onError = { e ->
                _editDriverState.value = ResultState.Error(
                    message = e.message ?: "Failed to update driver details"
                )
            }
        ) {
            _editDriverState.value = ResultState.Loading

            when (val result = repository.updateDriverDetails(driverId, emergencyContact, bloodGroup)) {
                is ResultState.Success -> {
                    cachedDriver = result.data
                    _editDriverState.value = result
                    _driverState.value = result
                    emitSuccess("Driver details updated successfully")
                }
                is ResultState.Error -> {
                    _editDriverState.value = result
                    emitError(result.message)
                }
                else -> _editDriverState.value = result
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PASSWORD CHANGE
    // ═══════════════════════════════════════════════════════════

    /**
     * Change the user's password.
     *
     * @param currentPassword Existing password for verification
     * @param newPassword     New password to set
     * @param confirmPassword Confirmation of new password
     */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        if (currentPassword.isBlank()) {
            emitError("Current password is required")
            return
        }
        if (newPassword.isBlank()) {
            emitError("New password is required")
            return
        }
        if (newPassword.length < 6) {
            emitError("Password must be at least 6 characters")
            return
        }
        if (newPassword != confirmPassword) {
            emitError("Passwords do not match")
            return
        }
        if (currentPassword == newPassword) {
            emitError("New password must be different from current password")
            return
        }

        launchWithLoading(
            dispatcher = Dispatchers.IO,
            showLoading = true,
            onError = { e ->
                _changePasswordState.value = ResultState.Error(
                    message = e.message ?: "Failed to change password"
                )
            }
        ) {
            _changePasswordState.value = ResultState.Loading

            when (val result = repository.changePassword(currentPassword, newPassword)) {
                is ResultState.Success -> {
                    _changePasswordState.value = result
                    emitSuccess("Password changed successfully")
                }
                is ResultState.Error -> {
                    _changePasswordState.value = result
                    emitError(result.message)
                }
                else -> _changePasswordState.value = result
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LOGOUT
    // ═══════════════════════════════════════════════════════════

    /**
     * Sign out the user:
     * 1. Cancel real-time listeners
     * 2. Clear SessionManager (DataStore + in-memory)
     * 3. Sign out Firebase Auth
     */
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _logoutState.value = ResultState.Loading

                // Cancel listeners
                userListenerJob?.cancel()
                driverListenerJob?.cancel()

                // Clear session
                sessionManager.clearSession()

                // Sign out Firebase
                repository.signOut()

                _logoutState.value = ResultState.Success(Unit)
            } catch (e: Exception) {
                _logoutState.value = ResultState.Error(
                    message = e.message ?: "Failed to sign out"
                )
                emitError("Logout failed. Please try again.")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STATE RESET
    // ═══════════════════════════════════════════════════════════

    fun resetEditProfileState() {
        _editProfileState.value = ResultState.Idle
    }

    fun resetEditDriverState() {
        _editDriverState.value = ResultState.Idle
    }

    fun resetChangePasswordState() {
        _changePasswordState.value = ResultState.Idle
    }

    fun resetLogoutState() {
        _logoutState.value = ResultState.Idle
    }

    fun resetLicenseUploadState() {
        _licenseUploadState.value = ResultState.Idle
    }

    fun resetIdProofUploadState() {
        _idProofUploadState.value = ResultState.Idle
    }

    // ═══════════════════════════════════════════════════════════
    // IMAGE COMPRESSION
    // ═══════════════════════════════════════════════════════════

    /**
     * Compress a bitmap to a JPEG byte array with size constraints.
     *
     * Scales down if larger than MAX_IMAGE_SIZE, then compresses
     * to JPEG at JPEG_QUALITY.
     *
     * @param bitmap The source bitmap
     * @return Compressed byte array
     */
    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        // Scale down if necessary
        val scaledBitmap = if (width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE) {
            val ratio = minOf(
                MAX_IMAGE_SIZE.toFloat() / width,
                MAX_IMAGE_SIZE.toFloat() / height
            )
            val newWidth = (width * ratio).toInt()
            val newHeight = (height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        // Compress to JPEG
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)

        // Recycle scaled bitmap if it's a new one
        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        return outputStream.toByteArray()
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the current user email.
     */
    fun getCurrentEmail(): String {
        return cachedUser?.email ?: ""
    }

    /**
     * Get the cached driver record.
     */
    fun getCachedDriver(): Driver? = cachedDriver

    /**
     * Get the cached vehicle record.
     */
    fun getCachedVehicle(): Vehicle? = cachedVehicle

    /**
     * Get formatted role string.
     */
    fun getFormattedRole(): String {
        val role = cachedUser?.role ?: sessionManager.currentRole.value
        return role?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Driver"
    }

    /**
     * Get account creation timestamp.
     */
    fun getAccountCreatedAt(): Long {
        return cachedUser?.createdAt ?: 0L
    }

    // ─── Cleanup ────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        userListenerJob?.cancel()
        driverListenerJob?.cancel()
    }
}
