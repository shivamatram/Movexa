package com.example.movexa.ui.dashboard.driver

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movexa.R
import com.example.movexa.data.model.FuelLog
import com.example.movexa.data.model.Vehicle
import com.example.movexa.databinding.FragmentDriverFuelBinding
import com.example.movexa.service.FuelAnalysisEngine
import com.example.movexa.ui.base.BaseFragment
import java.io.File
import java.io.InputStream
import java.text.NumberFormat
import java.util.Locale

/**
 * ═══════════════════════════════════════════════════════════════
 *  DRIVER FUEL FRAGMENT
 * ═══════════════════════════════════════════════════════════════
 *
 * Production-grade Fuel Management screen for the driver dashboard.
 *
 * ═══════════════════════════════════════════════════════════════
 * SECTIONS
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. **Vehicle Info Card** — displays the assigned vehicle's plate
 *    number, type, fuel type, and last known odometer reading.
 *
 * 2. **Fuel Entry Form** — five fields:
 *    - Fuel quantity (litres, decimal)
 *    - Total cost (₹, decimal) with live per-litre rate
 *    - Current odometer (km, integer)
 *    - Station name (text, optional)
 *    - Notes (multiline, optional, 250 char counter)
 *
 * 3. **Receipt Image** — camera / gallery picker with image
 *    preview, remove button, and upload progress bar.
 *
 * 4. **Live Mileage Preview** — real-time km/L calculation as
 *    odometer + quantity inputs change. Colour-coded quality
 *    badge (Excellent/Good/Average/Below Average/Poor/Suspicious).
 *
 * 5. **Submit Button** — animates through states:
 *    Idle → Validating → Uploading → Submitting → Success.
 *    Disables inputs during submission, re-enables on error.
 *
 * 6. **Recent Fuel Logs** — RecyclerView of last 10 entries for
 *    the assigned vehicle with date, station, quantity, cost,
 *    odometer, and mileage badge.
 *
 * ═══════════════════════════════════════════════════════════════
 * SCREEN STATES
 * ═══════════════════════════════════════════════════════════════
 *
 * - **Loading** — centre spinner while driver/vehicle data loads
 * - **Ready** — full form + recent logs visible
 * - **NoVehicle** — friendly message when no vehicle is assigned
 * - **Error** — message + retry button
 *
 * ═══════════════════════════════════════════════════════════════
 * IMAGE HANDLING
 * ═══════════════════════════════════════════════════════════════
 *
 * Camera:  launches via FileProvider temporary file URI →
 *          decodes with BitmapFactory + inSampleSize for memory
 *          safety (target 1024px max dimension).
 *
 * Gallery: Photo Picker (Android 13+) or ACTION_GET_CONTENT
 *          fallback via PickVisualMedia contract.
 *
 * The ViewModel compresses the bitmap to JPEG (70% quality,
 * ≤1024px) before uploading to Firebase Storage.
 *
 * ═══════════════════════════════════════════════════════════════
 */
class DriverFuelFragment : BaseFragment<FragmentDriverFuelBinding>(
    FragmentDriverFuelBinding::inflate
) {

    companion object {
        private const val TAG = "DriverFuelFragment"
        private const val MAX_DECODE_SIZE = 1600 // px — decode cap for image preview
    }

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: DriverFuelViewModel by viewModels()

    // ─── Adapter ────────────────────────────────────────────────
    private lateinit var fuelLogAdapter: FuelLogHistoryAdapter

    // ─── Number formatters ──────────────────────────────────────
    private val indiaNumberFmt = NumberFormat.getNumberInstance(Locale("en", "IN"))

    // ─── Camera temp file URI ───────────────────────────────────
    private var cameraPhotoUri: Uri? = null

    // ─── Text-watcher throttle flag ─────────────────────────────
    private var isMileageUpdatePending = false

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
                viewModel.setReceiptImage(bitmap, cameraPhotoUri)
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
                viewModel.setReceiptImage(bitmap, uri)
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
            showError("Camera permission is required to take receipt photos")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        setupRecyclerView()
        viewModel.initialize()
    }

    /**
     * Configure the recent fuel logs RecyclerView.
     */
    private fun setupRecyclerView() {
        fuelLogAdapter = FuelLogHistoryAdapter()
        binding.rvRecentLogs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fuelLogAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═══════════════════════════════════════════════════════════

    override fun setupListeners() {
        setupFormTextWatchers()
        setupReceiptButtons()
        setupSubmitButton()
        setupRetryButton()
        setupNotesImeAction()
    }

    // ── Form Text Watchers ──────────────────────────────────────

    /**
     * Attach text watchers to odometer + quantity fields
     * to trigger live mileage preview recalculation.
     * Also attach watcher to cost + quantity for cost/litre.
     */
    private fun setupFormTextWatchers() {
        val mileageWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateMileagePreview()
            }
        }

        binding.etOdometer.addTextChangedListener(mileageWatcher)
        binding.etQuantity.addTextChangedListener(mileageWatcher)

        // Cost-per-litre calculation
        val costWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCostPerLitre()
            }
        }

        binding.etCost.addTextChangedListener(costWatcher)
        // quantity also affects cost/litre
        binding.etQuantity.addTextChangedListener(costWatcher)
    }

    /**
     * Send current odometer + quantity to the ViewModel for
     * mileage preview recalculation.
     */
    private fun updateMileagePreview() {
        val odometerText = binding.etOdometer.text?.toString().orEmpty()
        val quantityText = binding.etQuantity.text?.toString().orEmpty()
        viewModel.updateMileagePreview(odometerText, quantityText)
    }

    /**
     * Calculate and display live cost-per-litre below the cost
     * field.
     */
    private fun updateCostPerLitre() {
        val cost = binding.etCost.text?.toString()?.toDoubleOrNull()
        val qty = binding.etQuantity.text?.toString()?.toDoubleOrNull()

        if (cost != null && cost > 0 && qty != null && qty > 0) {
            val rate = cost / qty
            binding.tvCostPerLitre.text = getString(R.string.fuel_rate_display)
                .replace("km/L", "₹%.2f/L".format(rate))
            // Actually just show the rate
            binding.tvCostPerLitre.text = "₹%.2f/L".format(rate)
            binding.tvCostPerLitre.visibility = View.VISIBLE
        } else {
            binding.tvCostPerLitre.visibility = View.GONE
        }
    }

    // ── Receipt Buttons ─────────────────────────────────────────

    /**
     * Wire up camera, gallery, and remove receipt buttons.
     */
    private fun setupReceiptButtons() {
        binding.btnCamera.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnRemoveReceipt.setOnClickListener {
            viewModel.clearReceiptImage()
        }
    }

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
                showInfo("Camera permission is needed to capture receipt photos")
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
                "fuel_receipt_",
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

    // ── Submit Button ───────────────────────────────────────────

    /**
     * Wire up the submit button — collects all field values and
     * delegates to the ViewModel.
     */
    private fun setupSubmitButton() {
        binding.btnSubmit.setOnClickListener {
            hideKeyboard()
            submitForm()
        }
    }

    /**
     * Gather form inputs and call the ViewModel's submit method.
     */
    private fun submitForm() {
        val quantity = binding.etQuantity.text?.toString().orEmpty()
        val cost = binding.etCost.text?.toString().orEmpty()
        val odometer = binding.etOdometer.text?.toString().orEmpty()
        val station = binding.etStation.text?.toString().orEmpty()
        val notes = binding.etNotes.text?.toString().orEmpty()

        viewModel.submitFuelLog(quantity, cost, odometer, station, notes)
    }

    // ── Retry Button ────────────────────────────────────────────

    /**
     * Retry loading driver/vehicle data.
     */
    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewModel.initialize()
        }
    }

    // ── Notes IME Action ────────────────────────────────────────

    /**
     * When the user presses Done on the notes field,
     * dismiss the keyboard.
     */
    private fun setupNotesImeAction() {
        binding.etNotes.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                true
            } else false
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  OBSERVE DATA
    // ═══════════════════════════════════════════════════════════

    override fun observeData() {
        observeScreenState()
        observeVehicle()
        observeMileagePreview()
        observeReceiptImage()
        observeUploadProgress()
        observeSubmissionState()
        observeRecentLogs()
        observeErrorAndSuccess()
    }

    // ── Screen State ────────────────────────────────────────────

    /**
     * Show/hide the appropriate top-level layout based on
     * the current screen state (Loading, Ready, NoVehicle, Error).
     */
    private fun observeScreenState() {
        collectLatestFlow(viewModel.screenState) { state ->
            binding.layoutLoading.visibility = View.GONE
            binding.layoutError.visibility = View.GONE
            binding.layoutNoVehicle.visibility = View.GONE
            binding.scrollContent.visibility = View.GONE

            when (state) {
                is DriverFuelViewModel.ScreenState.Loading -> {
                    binding.layoutLoading.visibility = View.VISIBLE
                }
                is DriverFuelViewModel.ScreenState.Ready -> {
                    binding.scrollContent.visibility = View.VISIBLE
                }
                is DriverFuelViewModel.ScreenState.NoVehicle -> {
                    binding.layoutNoVehicle.visibility = View.VISIBLE
                }
                is DriverFuelViewModel.ScreenState.Error -> {
                    binding.layoutError.visibility = View.VISIBLE
                    binding.tvErrorMessage.text = state.message
                }
            }
        }
    }

    // ── Vehicle Info ────────────────────────────────────────────

    /**
     * Populate the vehicle info card when the vehicle is loaded.
     */
    private fun observeVehicle() {
        collectLatestFlow(viewModel.vehicle) { vehicle ->
            vehicle?.let { bindVehicleInfo(it) }
        }

        collectLatestFlow(viewModel.lastOdometer) { odometer ->
            if (odometer > 0) {
                binding.tvLastOdometer.text = "%s km".format(
                    indiaNumberFmt.format(odometer)
                )
            } else {
                binding.tvLastOdometer.text = "—"
            }
        }
    }

    /**
     * Bind vehicle data to the vehicle info card views.
     */
    private fun bindVehicleInfo(vehicle: Vehicle) {
        binding.tvVehicleNumber.text = vehicle.number
        binding.tvVehicleType.text = buildString {
            append(vehicle.type.name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() })
            if (vehicle.fuelType.isNotBlank()) {
                append(" • ")
                append(vehicle.fuelType.replaceFirstChar { it.uppercase() })
            }
        }
    }

    // ── Mileage Preview ─────────────────────────────────────────

    /**
     * Update the mileage preview card in real time as the user
     * types odometer and quantity values.
     */
    private fun observeMileagePreview() {
        collectLatestFlow(viewModel.mileagePreview) { result ->
            when (result) {
                is FuelAnalysisEngine.MileageResult.Calculated -> {
                    showMileageCalculated(result)
                }
                is FuelAnalysisEngine.MileageResult.FirstEntry -> {
                    showMileageFirstEntry()
                }
                is FuelAnalysisEngine.MileageResult.Invalid -> {
                    hideMileagePreview()
                }
                null -> {
                    hideMileagePreview()
                }
            }
        }
    }

    /**
     * Display the calculated mileage value with quality badge.
     */
    private fun showMileageCalculated(result: FuelAnalysisEngine.MileageResult.Calculated) {
        binding.cardMileagePreview.visibility = View.VISIBLE
        binding.layoutMileageCalculated.visibility = View.VISIBLE
        binding.layoutMileageFirstEntry.visibility = View.GONE
        binding.layoutMileageNotAvailable.visibility = View.GONE

        // Mileage value
        binding.tvMileageValue.text = "%.1f".format(result.mileage)

        // Distance travelled
        binding.tvDistanceTravelled.text = "%s km".format(
            indiaNumberFmt.format(result.distance)
        )

        // Quality badge
        binding.tvMileageQuality.text = result.quality.displayName

        // Colour coding based on quality
        val (textColor, bgColor) = getMileageQualityColors(result.quality)
        val textColorRes = ContextCompat.getColor(requireContext(), textColor)
        val bgColorRes = ContextCompat.getColor(requireContext(), bgColor)

        binding.tvMileageValue.setTextColor(textColorRes)
        binding.tvMileageQuality.setTextColor(textColorRes)
        binding.tvMileageQuality.background?.setTint(bgColorRes)
    }

    /**
     * Show the first entry message when there's no prior
     * odometer reading for comparison.
     */
    private fun showMileageFirstEntry() {
        binding.cardMileagePreview.visibility = View.VISIBLE
        binding.layoutMileageCalculated.visibility = View.GONE
        binding.layoutMileageFirstEntry.visibility = View.VISIBLE
        binding.layoutMileageNotAvailable.visibility = View.GONE
    }

    /**
     * Hide the mileage preview card entirely.
     */
    private fun hideMileagePreview() {
        binding.cardMileagePreview.visibility = View.GONE
    }

    /**
     * Map [FuelAnalysisEngine.MileageQuality] to colour resource pairs.
     */
    private fun getMileageQualityColors(
        quality: FuelAnalysisEngine.MileageQuality
    ): Pair<Int, Int> {
        return when (quality) {
            FuelAnalysisEngine.MileageQuality.EXCELLENT -> Pair(
                R.color.mileage_excellent, R.color.mileage_excellent_bg
            )
            FuelAnalysisEngine.MileageQuality.GOOD -> Pair(
                R.color.mileage_good, R.color.mileage_good_bg
            )
            FuelAnalysisEngine.MileageQuality.AVERAGE -> Pair(
                R.color.mileage_average, R.color.mileage_average_bg
            )
            FuelAnalysisEngine.MileageQuality.BELOW_AVERAGE -> Pair(
                R.color.mileage_below_average, R.color.mileage_below_average_bg
            )
            FuelAnalysisEngine.MileageQuality.POOR -> Pair(
                R.color.mileage_poor, R.color.mileage_poor_bg
            )
            FuelAnalysisEngine.MileageQuality.SUSPICIOUS -> Pair(
                R.color.mileage_suspicious, R.color.mileage_suspicious_bg
            )
        }
    }

    // ── Receipt Image ───────────────────────────────────────────

    /**
     * Toggle between receipt placeholder and image preview
     * depending on whether a receipt bitmap is set.
     */
    private fun observeReceiptImage() {
        collectLatestFlow(viewModel.receiptBitmap) { bitmap ->
            if (bitmap != null) {
                // Show image preview, hide placeholder
                binding.layoutReceiptPlaceholder.visibility = View.GONE
                binding.layoutReceiptImage.visibility = View.VISIBLE
                binding.ivReceiptPreview.setImageBitmap(bitmap)
            } else {
                // Show placeholder, hide image preview
                binding.layoutReceiptPlaceholder.visibility = View.VISIBLE
                binding.layoutReceiptImage.visibility = View.GONE
                binding.ivReceiptPreview.setImageBitmap(null)
            }
        }
    }

    // ── Upload Progress ─────────────────────────────────────────

    /**
     * Show upload progress bar and percentage text during
     * receipt image upload.
     */
    private fun observeUploadProgress() {
        collectLatestFlow(viewModel.uploadProgress) { progress ->
            if (progress in 1..99) {
                binding.progressUpload.visibility = View.VISIBLE
                binding.progressUpload.setProgressCompat(progress, true)
                binding.tvUploadStatus.visibility = View.VISIBLE
                binding.tvUploadStatus.text = getString(R.string.fuel_receipt_uploading)
                    .replace("…", "… $progress%")
            } else {
                binding.progressUpload.visibility = View.GONE
                binding.tvUploadStatus.visibility = View.GONE
            }
        }
    }

    // ── Submission State ────────────────────────────────────────

    /**
     * React to submission state changes — update submit button
     * text, enable/disable form fields, show validation errors,
     * and handle success/error states.
     */
    private fun observeSubmissionState() {
        collectLatestFlow(viewModel.submissionState) { state ->
            when (state) {
                is DriverFuelViewModel.SubmissionState.Idle -> {
                    setFormEnabled(true)
                    binding.btnSubmit.text = getString(R.string.fuel_submit)
                    binding.btnSubmit.isEnabled = true
                    clearFieldErrors()
                }
                is DriverFuelViewModel.SubmissionState.Validating -> {
                    setFormEnabled(false)
                    binding.btnSubmit.text = getString(R.string.fuel_validating)
                    binding.btnSubmit.isEnabled = false
                }
                is DriverFuelViewModel.SubmissionState.Uploading -> {
                    setFormEnabled(false)
                    binding.btnSubmit.text = getString(R.string.fuel_uploading)
                    binding.btnSubmit.isEnabled = false
                }
                is DriverFuelViewModel.SubmissionState.Submitting -> {
                    setFormEnabled(false)
                    binding.btnSubmit.text = getString(R.string.fuel_submitting)
                    binding.btnSubmit.isEnabled = false
                }
                is DriverFuelViewModel.SubmissionState.ValidationError -> {
                    setFormEnabled(true)
                    binding.btnSubmit.text = getString(R.string.fuel_submit)
                    binding.btnSubmit.isEnabled = true
                    showValidationError(state.error)
                }
                is DriverFuelViewModel.SubmissionState.Error -> {
                    setFormEnabled(true)
                    binding.btnSubmit.text = getString(R.string.fuel_submit)
                    binding.btnSubmit.isEnabled = true
                }
                is DriverFuelViewModel.SubmissionState.Success -> {
                    handleSubmissionSuccess(state.fuelLog)
                }
            }
        }
    }

    /**
     * Display a validation error on the relevant TextInputLayout.
     */
    private fun showValidationError(error: DriverFuelViewModel.ValidationError) {
        clearFieldErrors()
        when (error.field) {
            DriverFuelViewModel.FormField.QUANTITY -> {
                binding.tilQuantity.error = error.message
                binding.etQuantity.requestFocus()
            }
            DriverFuelViewModel.FormField.COST -> {
                binding.tilCost.error = error.message
                binding.etCost.requestFocus()
            }
            DriverFuelViewModel.FormField.ODOMETER -> {
                binding.tilOdometer.error = error.message
                binding.etOdometer.requestFocus()
            }
        }
    }

    /**
     * Clear all TextInputLayout error states.
     */
    private fun clearFieldErrors() {
        binding.tilQuantity.error = null
        binding.tilCost.error = null
        binding.tilOdometer.error = null
    }

    /**
     * Handle successful fuel log submission — clear the form
     * fields, show a success message, and scroll to top.
     */
    private fun handleSubmissionSuccess(fuelLog: FuelLog) {
        // Clear form inputs
        binding.etQuantity.text?.clear()
        binding.etCost.text?.clear()
        binding.etOdometer.text?.clear()
        binding.etStation.text?.clear()
        binding.etNotes.text?.clear()

        // Reset ViewModel form state
        viewModel.resetForm()

        // Re-enable form
        setFormEnabled(true)
        binding.btnSubmit.text = getString(R.string.fuel_submit)
        binding.btnSubmit.isEnabled = true

        // Show success toast
        showSuccess(getString(R.string.fuel_submit_success))

        // Scroll to top
        binding.scrollContent.smoothScrollTo(0, 0)
    }

    /**
     * Enable or disable all form fields + buttons during
     * submission to prevent double-submit.
     */
    private fun setFormEnabled(enabled: Boolean) {
        binding.etQuantity.isEnabled = enabled
        binding.etCost.isEnabled = enabled
        binding.etOdometer.isEnabled = enabled
        binding.etStation.isEnabled = enabled
        binding.etNotes.isEnabled = enabled
        binding.btnCamera.isEnabled = enabled
        binding.btnGallery.isEnabled = enabled
        binding.btnRemoveReceipt.isEnabled = enabled
    }

    // ── Recent Fuel Logs ────────────────────────────────────────

    /**
     * Update the recent logs RecyclerView and toggle visibility
     * of the section header and empty state.
     */
    private fun observeRecentLogs() {
        collectLatestFlow(viewModel.recentLogs) { logs ->
            if (logs.isNotEmpty()) {
                binding.layoutRecentLogs.visibility = View.VISIBLE
                binding.rvRecentLogs.visibility = View.VISIBLE
                binding.layoutNoRecentLogs.visibility = View.GONE

                binding.tvRecentSubtitle.text = getString(
                    R.string.fuel_recent_subtitle
                ).replace("{count}", logs.size.toString())

                fuelLogAdapter.submitList(logs)
            } else {
                binding.layoutRecentLogs.visibility = View.VISIBLE
                binding.rvRecentLogs.visibility = View.GONE
                binding.layoutNoRecentLogs.visibility = View.VISIBLE
            }
        }
    }

    // ── Error & Success Events ──────────────────────────────────

    /**
     * Observe one-shot error and success events from the
     * ViewModel and display them as snackbars/toasts.
     */
    private fun observeErrorAndSuccess() {
        collectFlow(viewModel.errorEvent) { message ->
            showError(message)
        }

        collectFlow(viewModel.successEvent) { message ->
            showSuccess(message)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  IMAGE HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Decode a URI-based image with down-sampling to avoid
     * loading extremely large bitmaps into memory.
     *
     * Uses [BitmapFactory.Options.inSampleSize] to keep the
     * decoded bitmap within [MAX_DECODE_SIZE] pixels.
     *
     * @param uri Content URI of the image.
     * @return Decoded bitmap, or null on failure.
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
    //  UTILITY
    // ═══════════════════════════════════════════════════════════

    /**
     * Hide the soft keyboard.
     */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(
            android.content.Context.INPUT_METHOD_SERVICE
        ) as? InputMethodManager
        val focus = requireActivity().currentFocus ?: binding.root
        imm?.hideSoftInputFromWindow(focus.windowToken, 0)
    }
}
