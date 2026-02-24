package com.example.movexa.ui.dashboard.driver

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.FuelLog
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.FuelLogRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.service.DriverScoringEngine
import com.example.movexa.service.FuelAnalysisEngine
import com.example.movexa.ui.base.BaseViewModel
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * ═══════════════════════════════════════════════════════════════
 *  DRIVER FUEL VIEW MODEL
 * ═══════════════════════════════════════════════════════════════
 *
 * Manages the state and business logic for the Driver Fuel screen.
 *
 * Responsibilities:
 *  ● Load driver info + assigned vehicle on init
 *  ● Provide live mileage preview as odometer/qty change
 *  ● Compress and upload receipt images to Firebase Storage
 *  ● Create fuel log in Firestore
 *  ● Update vehicle odometer after submission
 *  ● Trigger fuel anomaly analysis
 *  ● Trigger driver performance scoring update
 *  ● Load recent fuel log history for the vehicle
 *
 * ═══════════════════════════════════════════════════════════════
 */
class DriverFuelViewModel : BaseViewModel() {

    companion object {
        private const val TAG = "DriverFuelViewModel"
        private const val MAX_IMAGE_SIZE = 1024 // px — compress to this max dimension
        private const val JPEG_QUALITY = 70 // % quality for compressed JPEG
        private const val STORAGE_PATH = "fuel_receipts"
        private const val MAX_RECENT_LOGS = 10
    }

    // ─── Repositories ───────────────────────────────────────────
    private val driverRepository = DriverRepositoryImpl()
    private val vehicleRepository = VehicleRepositoryImpl()
    private val fuelLogRepository = FuelLogRepositoryImpl()

    // ─── Engines ────────────────────────────────────────────────
    private val analysisEngine = FuelAnalysisEngine(viewModelScope)
    private val scoringEngine = DriverScoringEngine(viewModelScope)

    // ─── Firebase Storage ───────────────────────────────────────
    private val storage = FirebaseStorage.getInstance()

    // ═══════════════════════════════════════════════════════════
    //  STATE FLOWS
    // ═══════════════════════════════════════════════════════════

    // ── Screen State ────────────────────────────────────────────
    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Loading)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    // ── Driver & Vehicle ────────────────────────────────────────
    private val _driver = MutableStateFlow<Driver?>(null)
    val driver: StateFlow<Driver?> = _driver.asStateFlow()

    private val _vehicle = MutableStateFlow<Vehicle?>(null)
    val vehicle: StateFlow<Vehicle?> = _vehicle.asStateFlow()

    // ── Mileage Preview ─────────────────────────────────────────
    private val _mileagePreview = MutableStateFlow<FuelAnalysisEngine.MileageResult?>(null)
    val mileagePreview: StateFlow<FuelAnalysisEngine.MileageResult?> =
        _mileagePreview.asStateFlow()

    // ── Receipt Image ───────────────────────────────────────────
    private val _receiptBitmap = MutableStateFlow<Bitmap?>(null)
    val receiptBitmap: StateFlow<Bitmap?> = _receiptBitmap.asStateFlow()

    private val _receiptUri = MutableStateFlow<Uri?>(null)
    val receiptUri: StateFlow<Uri?> = _receiptUri.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    // ── Submission State ────────────────────────────────────────
    private val _submissionState = MutableStateFlow<SubmissionState>(SubmissionState.Idle)
    val submissionState: StateFlow<SubmissionState> = _submissionState.asStateFlow()

    // ── Recent Fuel Logs ────────────────────────────────────────
    private val _recentLogs = MutableStateFlow<List<FuelLog>>(emptyList())
    val recentLogs: StateFlow<List<FuelLog>> = _recentLogs.asStateFlow()

    // ── Form Fields (for validation references) ─────────────────
    private val _lastOdometer = MutableStateFlow(0L)
    val lastOdometer: StateFlow<Long> = _lastOdometer.asStateFlow()

    // ── Internal state ──────────────────────────────────────────
    private var driverId: String? = null
    private var companyId: String? = null

    // ═══════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Load driver info, resolve assigned vehicle, fetch recent logs.
     * Call from fragment's [initViews].
     */
    fun initialize() {
        launchWithLoading(Dispatchers.IO) {
            _screenState.value = ScreenState.Loading

            // 1. Get current user ID
            val userId = SessionManager.getInstance().getCachedUserId()
            if (userId.isNullOrBlank()) {
                _screenState.value = ScreenState.Error("Not logged in")
                emitError("Please sign in again.")
                return@launchWithLoading
            }

            // 2. Resolve driver profile (auto-create if missing)
            val driverResult = driverRepository.getOrCreateDriverByUserId(userId)
            if (driverResult !is ResultState.Success) {
                val errorMsg = (driverResult as? ResultState.Error)?.message
                    ?: "Failed to load driver profile."
                _screenState.value = ScreenState.Error(errorMsg)
                emitError(errorMsg)
                return@launchWithLoading
            }

            val driverObj = driverResult.data
            _driver.value = driverObj
            driverId = driverObj.driverId
            companyId = driverObj.companyId

            Log.d(TAG, "Driver loaded: ${driverObj.driverId}, " +
                    "vehicle=${driverObj.assignedVehicleId}")

            // 3. Resolve assigned vehicle
            val vehicleId = driverObj.assignedVehicleId
            if (vehicleId.isNullOrBlank()) {
                _screenState.value = ScreenState.NoVehicle
                emitError("No vehicle assigned. Contact your manager.")
                return@launchWithLoading
            }

            val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
            if (vehicleResult is ResultState.Success && vehicleResult.data != null) {
                val vehicleObj = vehicleResult.data
                _vehicle.value = vehicleObj
                _lastOdometer.value = vehicleObj.lastOdometer

                Log.d(TAG, "Vehicle loaded: ${vehicleObj.number}, " +
                        "lastOdometer=${vehicleObj.lastOdometer}")
            } else {
                _screenState.value = ScreenState.Error("Vehicle not found")
                emitError("Assigned vehicle not found.")
                return@launchWithLoading
            }

            // 4. Load recent fuel logs
            loadRecentLogs(vehicleId)

            // 5. Set ready state
            _screenState.value = ScreenState.Ready
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LIVE MILEAGE PREVIEW
    // ═══════════════════════════════════════════════════════════

    /**
     * Recalculate mileage preview whenever odometer or quantity
     * changes. Called from fragment's text watchers.
     *
     * @param odometerText  Raw odometer input string.
     * @param quantityText  Raw fuel quantity input string.
     */
    fun updateMileagePreview(odometerText: String, quantityText: String) {
        val odometer = odometerText.toLongOrNull() ?: 0L
        val quantity = quantityText.toDoubleOrNull() ?: 0.0
        val lastOdo = _lastOdometer.value

        if (odometer <= 0 && quantity <= 0) {
            _mileagePreview.value = null
            return
        }

        _mileagePreview.value = analysisEngine.calculateMileage(
            newOdometer = odometer,
            lastOdometer = lastOdo,
            fuelQuantity = quantity
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  RECEIPT IMAGE
    // ═══════════════════════════════════════════════════════════

    /**
     * Set the receipt image from camera or gallery picker.
     * The bitmap will be compressed before upload.
     */
    fun setReceiptImage(bitmap: Bitmap?, uri: Uri?) {
        _receiptBitmap.value = bitmap
        _receiptUri.value = uri
    }

    /**
     * Clear the selected receipt image.
     */
    fun clearReceiptImage() {
        _receiptBitmap.value = null
        _receiptUri.value = null
        _uploadProgress.value = 0
    }

    /**
     * Compress a bitmap to JPEG and return the byte array.
     * Resizes if dimensions exceed [MAX_IMAGE_SIZE].
     */
    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val scaledBitmap = if (bitmap.width > MAX_IMAGE_SIZE ||
            bitmap.height > MAX_IMAGE_SIZE
        ) {
            val scale = MAX_IMAGE_SIZE.toFloat() /
                    maxOf(bitmap.width, bitmap.height).toFloat()
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }

    /**
     * Upload the receipt image to Firebase Storage.
     *
     * @return Download URL string, or null if upload failed or
     *         no image was selected.
     */
    private suspend fun uploadReceiptImage(): String? {
        val bitmap = _receiptBitmap.value ?: return null

        return try {
            _uploadProgress.value = 10

            // Compress
            val imageBytes = compressBitmap(bitmap)
            _uploadProgress.value = 30

            // Build storage path
            val fileName = "${UUID.randomUUID()}.jpg"
            val ref = storage.reference
                .child(STORAGE_PATH)
                .child(companyId ?: "unknown")
                .child(fileName)

            // Upload with progress tracking
            val uploadTask = ref.putBytes(imageBytes)
            uploadTask.addOnProgressListener { snapshot ->
                val progress = (30 + (snapshot.bytesTransferred * 50 /
                        snapshot.totalByteCount)).toInt()
                _uploadProgress.value = progress.coerceIn(30, 80)
            }
            uploadTask.await()
            _uploadProgress.value = 85

            // Get download URL
            val url = ref.downloadUrl.await().toString()
            _uploadProgress.value = 100
            Log.d(TAG, "Receipt uploaded: $url")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Receipt upload failed", e)
            _uploadProgress.value = 0
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FUEL LOG SUBMISSION
    // ═══════════════════════════════════════════════════════════

    /**
     * Validate inputs and submit a new fuel log.
     *
     * Flow:
     *  1. Validate all fields
     *  2. Upload receipt image (if present)
     *  3. Calculate mileage
     *  4. Create FuelLog in Firestore
     *  5. Update vehicle odometer
     *  6. Trigger anomaly analysis (async)
     *  7. Trigger driver scoring update (async)
     *
     * @param quantity     Fuel in litres.
     * @param cost         Total cost in ₹.
     * @param odometer     Current odometer reading in km.
     * @param stationName  Fuel station name (optional).
     * @param notes        Additional notes (optional).
     */
    fun submitFuelLog(
        quantity: String,
        cost: String,
        odometer: String,
        stationName: String,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _submissionState.value = SubmissionState.Validating

                // ── 1. Validate ─────────────────────────────────
                val validation = validateInputs(quantity, cost, odometer)
                if (validation != null) {
                    _submissionState.value = SubmissionState.ValidationError(validation)
                    return@launch
                }

                val qty = quantity.toDouble()
                val totalCost = cost.toDouble()
                val odo = odometer.toLong()
                val vehicleObj = _vehicle.value ?: run {
                    _submissionState.value = SubmissionState.Error("Vehicle not loaded")
                    return@launch
                }
                val driverObj = _driver.value ?: run {
                    _submissionState.value = SubmissionState.Error("Driver not loaded")
                    return@launch
                }

                // ── 2. Upload receipt ───────────────────────────
                _submissionState.value = SubmissionState.Uploading
                val receiptUrl = uploadReceiptImage()

                // ── 3. Calculate mileage ────────────────────────
                _submissionState.value = SubmissionState.Submitting
                val lastOdo = _lastOdometer.value
                var mileage = 0.0
                var distance = 0L

                if (lastOdo > 0 && odo > lastOdo) {
                    distance = odo - lastOdo
                    mileage = distance.toDouble() / qty
                }

                // ── 4. Create FuelLog ───────────────────────────
                val fuelLog = FuelLog(
                    fuelId = UUID.randomUUID().toString(),
                    vehicleId = vehicleObj.vehicleId,
                    driverId = driverObj.driverId,
                    companyId = driverObj.companyId,
                    quantity = qty,
                    cost = totalCost,
                    odometer = odo,
                    mileage = mileage,
                    fuelType = vehicleObj.fuelType,
                    billUrl = receiptUrl,
                    stationName = stationName.trim(),
                    timestamp = System.currentTimeMillis(),
                    notes = notes.trim(),
                    metadata = mapOf(
                        "distance" to distance,
                        "lastOdometer" to lastOdo,
                        "vehicleNumber" to vehicleObj.number
                    )
                )

                val createResult = fuelLogRepository.createFuelLog(fuelLog)
                if (createResult is ResultState.Error) {
                    _submissionState.value = SubmissionState.Error(
                        createResult.message
                    )
                    return@launch
                }

                Log.d(TAG, "Fuel log created: ${fuelLog.fuelId}, " +
                        "mileage=%.1f km/L, distance=%d km".format(mileage, distance))

                // ── 5. Update vehicle odometer ──────────────────
                vehicleRepository.updateOdometer(vehicleObj.vehicleId, odo)
                _lastOdometer.value = odo
                _vehicle.value = vehicleObj.copy(lastOdometer = odo)

                // ── 6. Anomaly analysis (async, non-blocking) ───
                analysisEngine.analyzeAndAlertAsync(fuelLog, vehicleObj)

                // ── 7. Scoring update (async, non-blocking) ─────
                scoringEngine.applyFuelBonusAsync(
                    driverObj.driverId,
                    driverObj.companyId,
                    fuelLog
                )

                // ── 8. Success ──────────────────────────────────
                _submissionState.value = SubmissionState.Success(fuelLog)
                emitSuccess("Fuel log submitted successfully!")

                // Refresh recent logs
                loadRecentLogs(vehicleObj.vehicleId)

            } catch (e: Exception) {
                Log.e(TAG, "Fuel log submission failed", e)
                _submissionState.value = SubmissionState.Error(
                    e.message ?: "Submission failed"
                )
                emitError("Failed to submit fuel log. Please try again.")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VALIDATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Validate all fuel log input fields.
     * Returns null if valid, or a [ValidationError] describing
     * the first problem found.
     */
    private fun validateInputs(
        quantity: String,
        cost: String,
        odometer: String
    ): ValidationError? {
        // Fuel quantity
        val qty = quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            return ValidationError(
                field = FormField.QUANTITY,
                message = "Enter a valid fuel quantity"
            )
        }
        if (qty > FuelAnalysisEngine.MAX_SINGLE_FILL_LITRES) {
            return ValidationError(
                field = FormField.QUANTITY,
                message = "Fuel quantity exceeds maximum " +
                        "(${FuelAnalysisEngine.MAX_SINGLE_FILL_LITRES.toInt()}L)"
            )
        }

        // Fuel cost
        val totalCost = cost.toDoubleOrNull()
        if (totalCost == null || totalCost <= 0) {
            return ValidationError(
                field = FormField.COST,
                message = "Enter a valid fuel cost"
            )
        }

        // Odometer
        val odo = odometer.toLongOrNull()
        if (odo == null || odo <= 0) {
            return ValidationError(
                field = FormField.ODOMETER,
                message = "Enter a valid odometer reading"
            )
        }

        // Odometer must be >= lastOdometer (prevent rollback)
        val lastOdo = _lastOdometer.value
        if (lastOdo > 0 && odo < lastOdo) {
            return ValidationError(
                field = FormField.ODOMETER,
                message = "Odometer must be ≥ last reading (%,d km)".format(lastOdo)
            )
        }

        return null
    }

    // ═══════════════════════════════════════════════════════════
    //  RECENT LOGS
    // ═══════════════════════════════════════════════════════════

    /**
     * Load recent fuel logs for the vehicle.
     */
    private suspend fun loadRecentLogs(vehicleId: String) {
        try {
            val result = fuelLogRepository.getFuelLogsByVehicle(vehicleId)
            if (result is ResultState.Success) {
                _recentLogs.value = result.data
                    .sortedByDescending { it.timestamp }
                    .take(MAX_RECENT_LOGS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load recent logs", e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FORM RESET
    // ═══════════════════════════════════════════════════════════

    /**
     * Reset the form to initial state for a new entry.
     */
    fun resetForm() {
        _submissionState.value = SubmissionState.Idle
        _mileagePreview.value = null
        clearReceiptImage()
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA CLASSES & ENUMS
    // ═══════════════════════════════════════════════════════════

    /** Overall screen state. */
    sealed class ScreenState {
        data object Loading : ScreenState()
        data object Ready : ScreenState()
        data object NoVehicle : ScreenState()
        data class Error(val message: String) : ScreenState()
    }

    /** Fuel log submission lifecycle. */
    sealed class SubmissionState {
        data object Idle : SubmissionState()
        data object Validating : SubmissionState()
        data object Uploading : SubmissionState()
        data object Submitting : SubmissionState()
        data class ValidationError(val error: DriverFuelViewModel.ValidationError) :
            SubmissionState()
        data class Error(val message: String) : SubmissionState()
        data class Success(val fuelLog: FuelLog) : SubmissionState()
    }

    /** Validation error targeting a specific form field. */
    data class ValidationError(
        val field: FormField,
        val message: String
    )

    /** Enum of form fields for targeted error display. */
    enum class FormField {
        QUANTITY,
        COST,
        ODOMETER
    }
}
