package com.example.movexa.ui.dashboard.mechanic

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.movexa.data.model.PartHistory
import com.example.movexa.data.model.Repair
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.ServiceType
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.repository.impl.PartHistoryRepositoryImpl
import com.example.movexa.data.repository.impl.RepairRepositoryImpl
import com.example.movexa.data.repository.impl.ServiceRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import com.example.movexa.data.session.SessionManager
import com.example.movexa.service.MaintenanceScheduler
import com.example.movexa.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 *  MAINTENANCE VIEW MODEL
 * ═══════════════════════════════════════════════════════════════════
 *
 * Shared ViewModel for MechanicServiceFragment, MechanicRepairsFragment,
 * and MechanicPartsFragment.
 *
 * Responsibilities:
 *  ● Load company vehicles for selection
 *  ● Manage service record creation with auto-nextServiceKm
 *  ● Manage repair logging with vehicle status updates
 *  ● Manage part replacement tracking
 *  ● Load recent records per fragment
 *  ● Trigger MaintenanceScheduler for alerts
 *  ● Update vehicle odometer on submission
 *  ● Set vehicle status to SERVICE / AVAILABLE
 *
 * ═══════════════════════════════════════════════════════════════════
 */
class MaintenanceViewModel : BaseViewModel() {

    companion object {
        private const val TAG = "MaintenanceViewModel"
        private const val MAX_RECENT = 15
    }

    // ─── Repositories ───────────────────────────────────────────
    private val vehicleRepository = VehicleRepositoryImpl()
    private val serviceRepository = ServiceRepositoryImpl()
    private val repairRepository = RepairRepositoryImpl()
    private val partHistoryRepository = PartHistoryRepositoryImpl()

    // ─── Engine ─────────────────────────────────────────────────
    private val scheduler = MaintenanceScheduler(viewModelScope)

    // ═══════════════════════════════════════════════════════════
    //  SHARED STATE
    // ═══════════════════════════════════════════════════════════

    // ── Screen State ────────────────────────────────────────────
    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Loading)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    // ── Vehicles List (for spinner/selector) ────────────────────
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    // ── Selected Vehicle ────────────────────────────────────────
    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    // ── Company & User Info ─────────────────────────────────────
    private var companyId: String = ""
    private var userId: String = ""

    // ═══════════════════════════════════════════════════════════
    //  SERVICE TAB STATE
    // ═══════════════════════════════════════════════════════════

    private val _recentServices = MutableStateFlow<List<ServiceRecord>>(emptyList())
    val recentServices: StateFlow<List<ServiceRecord>> = _recentServices.asStateFlow()

    private val _serviceSubmissionState = MutableStateFlow<SubmissionState>(SubmissionState.Idle)
    val serviceSubmissionState: StateFlow<SubmissionState> = _serviceSubmissionState.asStateFlow()

    private val _nextServiceKmPreview = MutableStateFlow(0L)
    val nextServiceKmPreview: StateFlow<Long> = _nextServiceKmPreview.asStateFlow()

    private val _maintenanceStatuses =
        MutableStateFlow<List<MaintenanceScheduler.MaintenanceStatus>>(emptyList())
    val maintenanceStatuses: StateFlow<List<MaintenanceScheduler.MaintenanceStatus>> =
        _maintenanceStatuses.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  REPAIR TAB STATE
    // ═══════════════════════════════════════════════════════════

    private val _recentRepairs = MutableStateFlow<List<Repair>>(emptyList())
    val recentRepairs: StateFlow<List<Repair>> = _recentRepairs.asStateFlow()

    private val _repairSubmissionState = MutableStateFlow<SubmissionState>(SubmissionState.Idle)
    val repairSubmissionState: StateFlow<SubmissionState> = _repairSubmissionState.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  PARTS TAB STATE
    // ═══════════════════════════════════════════════════════════

    private val _recentParts = MutableStateFlow<List<PartHistory>>(emptyList())
    val recentParts: StateFlow<List<PartHistory>> = _recentParts.asStateFlow()

    private val _partSubmissionState = MutableStateFlow<SubmissionState>(SubmissionState.Idle)
    val partSubmissionState: StateFlow<SubmissionState> = _partSubmissionState.asStateFlow()

    private val _partStatuses =
        MutableStateFlow<List<MaintenanceScheduler.PartStatus>>(emptyList())
    val partStatuses: StateFlow<List<MaintenanceScheduler.PartStatus>> =
        _partStatuses.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  ONE-SHOT EVENTS
    // ═══════════════════════════════════════════════════════════

    private val _serviceSuccess = MutableSharedFlow<String>()
    val serviceSuccess: SharedFlow<String> = _serviceSuccess.asSharedFlow()

    private val _repairSuccess = MutableSharedFlow<String>()
    val repairSuccess: SharedFlow<String> = _repairSuccess.asSharedFlow()

    private val _partSuccess = MutableSharedFlow<String>()
    val partSuccess: SharedFlow<String> = _partSuccess.asSharedFlow()

    // ═══════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    fun initialize() {
        if (_screenState.value is ScreenState.Ready) return

        launchWithLoading(Dispatchers.IO) {
            _screenState.value = ScreenState.Loading

            // 1. Get user info
            val cachedUserId = SessionManager.getInstance().getCachedUserId()
            if (cachedUserId.isNullOrBlank()) {
                _screenState.value = ScreenState.Error("Not logged in")
                emitError("Please sign in again.")
                return@launchWithLoading
            }
            userId = cachedUserId

            // 2. Determine company ID
            // In the current data model, the admin's userId serves as the companyId.
            // Mechanic users belong to the company whose admin created them.
            // Use the cached userId as companyId (same pattern as admin/manager ViewModels).
            companyId = cachedUserId

            if (companyId.isBlank()) {
                // Try loading from user data
                _screenState.value = ScreenState.Error("Company not found")
                emitError("Company information not available.")
                return@launchWithLoading
            }

            // 3. Load company vehicles
            val vehiclesResult = vehicleRepository.getVehiclesByCompany(companyId)
            if (vehiclesResult is ResultState.Success) {
                _vehicles.value = vehiclesResult.data
                Log.d(TAG, "Loaded ${vehiclesResult.data.size} vehicles")
            } else if (vehiclesResult is ResultState.Error) {
                Log.e(TAG, "Failed to load vehicles: ${vehiclesResult.message}")
                _screenState.value = ScreenState.Error(vehiclesResult.message)
                emitError("Failed to load fleet vehicles.")
                return@launchWithLoading
            }

            _screenState.value = ScreenState.Ready
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE SELECTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Called when the mechanic selects a vehicle from the dropdown.
     */
    fun selectVehicle(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        Log.d(TAG, "Selected vehicle: ${vehicle.number}")

        // Load data for all tabs
        loadRecentServices(vehicle.vehicleId)
        loadRecentRepairs(vehicle.vehicleId)
        loadRecentParts(vehicle.vehicleId)
        checkMaintenanceStatuses(vehicle)
    }

    /**
     * Called when vehicle selection is cleared.
     */
    fun clearVehicleSelection() {
        _selectedVehicle.value = null
        _recentServices.value = emptyList()
        _recentRepairs.value = emptyList()
        _recentParts.value = emptyList()
        _maintenanceStatuses.value = emptyList()
        _partStatuses.value = emptyList()
        _nextServiceKmPreview.value = 0L
    }

    // ═══════════════════════════════════════════════════════════
    //  NEXT SERVICE KM PREVIEW
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the next service km preview based on current odometer input
     * and selected service type.
     */
    fun updateNextServicePreview(odometerText: String, serviceType: ServiceType) {
        val odometer = odometerText.toLongOrNull() ?: 0L
        if (odometer > 0) {
            _nextServiceKmPreview.value = scheduler.calculateNextServiceKm(odometer, serviceType)
        } else {
            _nextServiceKmPreview.value = 0L
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SERVICE RECORD SUBMISSION
    // ═══════════════════════════════════════════════════════════

    /**
     * Submit a new service record.
     *
     * Flow:
     *  1. Validate inputs
     *  2. Calculate nextServiceKm
     *  3. Create ServiceRecord in Firestore
     *  4. Update vehicle odometer
     *  5. Mark vehicle SERVICE → AVAILABLE (if completing)
     *  6. Run maintenance check (async)
     *  7. Emit success
     */
    fun submitServiceRecord(
        odometerText: String,
        serviceType: ServiceType,
        costText: String,
        description: String,
        workshopName: String,
        markCompleted: Boolean
    ) {
        val vehicle = _selectedVehicle.value
        if (vehicle == null) {
            emitError("Please select a vehicle first.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _serviceSubmissionState.value = SubmissionState.Validating

            // 1. Validate
            val odometer = odometerText.toLongOrNull()
            if (odometer == null || odometer <= 0) {
                _serviceSubmissionState.value =
                    SubmissionState.ValidationError("Please enter a valid odometer reading.")
                return@launch
            }
            if (odometer < vehicle.lastOdometer) {
                _serviceSubmissionState.value =
                    SubmissionState.ValidationError(
                        "Odometer cannot be less than last reading (${vehicle.lastOdometer} km)."
                    )
                return@launch
            }

            val cost = costText.toDoubleOrNull() ?: 0.0

            // 2. Calculate nextServiceKm
            val nextServiceKm = scheduler.calculateNextServiceKm(odometer, serviceType)

            // 3. Create record
            _serviceSubmissionState.value = SubmissionState.Submitting

            val serviceRecord = ServiceRecord(
                vehicleId = vehicle.vehicleId,
                companyId = companyId,
                odometer = odometer,
                serviceType = serviceType,
                nextServiceKm = nextServiceKm,
                date = System.currentTimeMillis(),
                cost = cost,
                description = description,
                servicedBy = userId,
                workshopName = workshopName,
                completed = markCompleted,
                createdBy = userId
            )

            val createResult = serviceRepository.createServiceRecord(serviceRecord)
            if (createResult is ResultState.Error) {
                _serviceSubmissionState.value =
                    SubmissionState.Error("Failed to save: ${createResult.message}")
                emitError("Failed to save service record.")
                return@launch
            }

            Log.d(TAG, "Service record created: ${(createResult as ResultState.Success).data}")

            // 4. Update vehicle odometer
            scheduler.updateVehicleOdometer(vehicle.vehicleId, odometer)

            // 5. If completed, ensure vehicle is AVAILABLE
            if (markCompleted && vehicle.status == VehicleStatus.SERVICE) {
                scheduler.markVehicleAvailable(vehicle.vehicleId)
            }

            // 6. Refresh vehicle data
            refreshVehicle(vehicle.vehicleId)

            // 7. Reload recent services
            loadRecentServices(vehicle.vehicleId)

            // 8. Run maintenance check async
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val updatedVehicle = _selectedVehicle.value ?: return@launch
                    val statuses = scheduler.checkServiceDue(updatedVehicle)
                    _maintenanceStatuses.value = statuses
                } catch (e: Exception) {
                    Log.e(TAG, "Maintenance check failed", e)
                }
            }

            // 9. Emit success
            _serviceSubmissionState.value = SubmissionState.Success
            val typeDisplay = serviceType.displayName
            _serviceSuccess.emit(
                "$typeDisplay recorded at $odometer km. Next due: $nextServiceKm km."
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  REPAIR RECORD SUBMISSION
    // ═══════════════════════════════════════════════════════════

    /**
     * Submit a new repair record.
     *
     * Flow:
     *  1. Validate inputs
     *  2. Create Repair in Firestore
     *  3. Update vehicle odometer
     *  4. Mark vehicle AVAILABLE (breakdown resolved)
     *  5. Emit success
     */
    fun submitRepairRecord(
        issue: String,
        repairDone: String,
        costText: String,
        odometerText: String,
        partsReplaced: String,
        notes: String,
        workshopName: String,
        markAvailable: Boolean
    ) {
        val vehicle = _selectedVehicle.value
        if (vehicle == null) {
            emitError("Please select a vehicle first.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _repairSubmissionState.value = SubmissionState.Validating

            // 1. Validate
            if (issue.isBlank()) {
                _repairSubmissionState.value =
                    SubmissionState.ValidationError("Please describe the issue.")
                return@launch
            }
            if (repairDone.isBlank()) {
                _repairSubmissionState.value =
                    SubmissionState.ValidationError("Please describe what was repaired.")
                return@launch
            }

            val cost = costText.toDoubleOrNull() ?: 0.0
            val odometer = odometerText.toLongOrNull() ?: vehicle.lastOdometer

            val partsList = if (partsReplaced.isBlank()) {
                emptyList()
            } else {
                partsReplaced.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }

            // 2. Create repair
            _repairSubmissionState.value = SubmissionState.Submitting

            val repair = Repair(
                vehicleId = vehicle.vehicleId,
                companyId = companyId,
                issue = issue,
                repairDone = repairDone,
                cost = cost,
                date = System.currentTimeMillis(),
                odometer = odometer,
                partsReplaced = partsList,
                repairedBy = userId,
                workshopName = workshopName,
                notes = notes,
                createdBy = userId
            )

            val createResult = repairRepository.createRepair(repair)
            if (createResult is ResultState.Error) {
                _repairSubmissionState.value =
                    SubmissionState.Error("Failed to save: ${createResult.message}")
                emitError("Failed to save repair record.")
                return@launch
            }

            Log.d(TAG, "Repair record created: ${(createResult as ResultState.Success).data}")

            // 3. Update odometer
            if (odometer > vehicle.lastOdometer) {
                scheduler.updateVehicleOdometer(vehicle.vehicleId, odometer)
            }

            // 4. Mark available if requested
            if (markAvailable) {
                scheduler.markVehicleAvailable(vehicle.vehicleId)
            }

            // 5. Refresh
            refreshVehicle(vehicle.vehicleId)
            loadRecentRepairs(vehicle.vehicleId)

            // 6. Success
            _repairSubmissionState.value = SubmissionState.Success
            _repairSuccess.emit("Repair logged: $issue. Cost: ₹${"%.0f".format(cost)}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PART REPLACEMENT SUBMISSION
    // ═══════════════════════════════════════════════════════════

    /**
     * Submit a new part replacement record.
     *
     * Flow:
     *  1. Validate inputs
     *  2. Calculate nextReplacementKm
     *  3. Create PartHistory in Firestore
     *  4. Check part expiry (async alert generation)
     *  5. Emit success
     */
    fun submitPartReplacement(
        partName: String,
        partNumber: String,
        changedAtKmText: String,
        expectedLifeKmText: String,
        costText: String,
        brand: String,
        supplierName: String,
        warrantyKmText: String,
        notes: String
    ) {
        val vehicle = _selectedVehicle.value
        if (vehicle == null) {
            emitError("Please select a vehicle first.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _partSubmissionState.value = SubmissionState.Validating

            // 1. Validate
            if (partName.isBlank()) {
                _partSubmissionState.value =
                    SubmissionState.ValidationError("Please enter the part name.")
                return@launch
            }

            val changedAtKm = changedAtKmText.toLongOrNull()
            if (changedAtKm == null || changedAtKm <= 0) {
                _partSubmissionState.value =
                    SubmissionState.ValidationError("Please enter a valid odometer reading.")
                return@launch
            }

            val expectedLifeKm = expectedLifeKmText.toLongOrNull()
            if (expectedLifeKm == null || expectedLifeKm <= 0) {
                _partSubmissionState.value =
                    SubmissionState.ValidationError("Please enter expected part life in km.")
                return@launch
            }

            val cost = costText.toDoubleOrNull() ?: 0.0
            val warrantyKm = warrantyKmText.toLongOrNull() ?: 0L

            // 2. Create record
            _partSubmissionState.value = SubmissionState.Submitting

            val partHistory = PartHistory(
                vehicleId = vehicle.vehicleId,
                companyId = companyId,
                partName = partName,
                partNumber = partNumber,
                changedAtKm = changedAtKm,
                expectedLifeKm = expectedLifeKm,
                cost = cost,
                brand = brand,
                supplierName = supplierName,
                warrantyKm = warrantyKm,
                date = System.currentTimeMillis(),
                installedBy = userId,
                notes = notes,
                createdBy = userId
            )

            val createResult = partHistoryRepository.createPartRecord(partHistory)
            if (createResult is ResultState.Error) {
                _partSubmissionState.value =
                    SubmissionState.Error("Failed to save: ${createResult.message}")
                emitError("Failed to save part record.")
                return@launch
            }

            Log.d(TAG, "Part record created: ${(createResult as ResultState.Success).data}")

            // 3. Update vehicle odometer if changedAtKm > lastOdometer
            if (changedAtKm > vehicle.lastOdometer) {
                scheduler.updateVehicleOdometer(vehicle.vehicleId, changedAtKm)
            }

            // 4. Reload
            refreshVehicle(vehicle.vehicleId)
            loadRecentParts(vehicle.vehicleId)

            // 5. Part expiry check async
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val updatedVehicle = _selectedVehicle.value ?: return@launch
                    val statuses = scheduler.checkPartExpiry(
                        updatedVehicle.vehicleId,
                        updatedVehicle.lastOdometer
                    )
                    _partStatuses.value = statuses
                } catch (e: Exception) {
                    Log.e(TAG, "Part expiry check failed", e)
                }
            }

            // 6. Success
            val nextKm = changedAtKm + expectedLifeKm
            _partSubmissionState.value = SubmissionState.Success
            _partSuccess.emit("$partName recorded. Next replacement: $nextKm km.")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE STATUS ACTIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Mark the selected vehicle as "In Service".
     */
    fun markVehicleInService() {
        val vehicle = _selectedVehicle.value ?: return
        launchSafe(Dispatchers.IO) {
            val result = scheduler.markVehicleInService(vehicle.vehicleId)
            if (result is ResultState.Success) {
                refreshVehicle(vehicle.vehicleId)
                emitSuccess("${vehicle.number} marked as In Service")
            } else if (result is ResultState.Error) {
                emitError("Failed: ${result.message}")
            }
        }
    }

    /**
     * Mark the selected vehicle as "Available".
     */
    fun markVehicleAvailable() {
        val vehicle = _selectedVehicle.value ?: return
        launchSafe(Dispatchers.IO) {
            val result = scheduler.markVehicleAvailable(vehicle.vehicleId)
            if (result is ResultState.Success) {
                refreshVehicle(vehicle.vehicleId)
                emitSuccess("${vehicle.number} marked as Available")
            } else if (result is ResultState.Error) {
                emitError("Failed: ${result.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA LOADERS
    // ═══════════════════════════════════════════════════════════

    private fun loadRecentServices(vehicleId: String) {
        launchSafe(Dispatchers.IO) {
            val result = serviceRepository.getServicesByVehicle(vehicleId)
            if (result is ResultState.Success) {
                _recentServices.value = result.data.take(MAX_RECENT)
            }
        }
    }

    private fun loadRecentRepairs(vehicleId: String) {
        launchSafe(Dispatchers.IO) {
            val result = repairRepository.getRepairsByVehicle(vehicleId)
            if (result is ResultState.Success) {
                _recentRepairs.value = result.data.take(MAX_RECENT)
            }
        }
    }

    private fun loadRecentParts(vehicleId: String) {
        launchSafe(Dispatchers.IO) {
            val result = partHistoryRepository.getPartsByVehicle(vehicleId)
            if (result is ResultState.Success) {
                _recentParts.value = result.data.take(MAX_RECENT)
            }
        }
    }

    private fun checkMaintenanceStatuses(vehicle: Vehicle) {
        launchSafe(Dispatchers.IO) {
            try {
                val statuses = scheduler.checkServiceDue(vehicle)
                _maintenanceStatuses.value = statuses

                val partStats = scheduler.checkPartExpiry(
                    vehicle.vehicleId,
                    vehicle.lastOdometer
                )
                _partStatuses.value = partStats
            } catch (e: Exception) {
                Log.e(TAG, "Maintenance status check failed", e)
            }
        }
    }

    private suspend fun refreshVehicle(vehicleId: String) {
        val result = vehicleRepository.getVehicleById(vehicleId)
        if (result is ResultState.Success && result.data != null) {
            _selectedVehicle.value = result.data
            // Update in vehicles list
            _vehicles.value = _vehicles.value.map {
                if (it.vehicleId == vehicleId) result.data else it
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FORM RESET
    // ═══════════════════════════════════════════════════════════

    fun resetServiceForm() {
        _serviceSubmissionState.value = SubmissionState.Idle
        _nextServiceKmPreview.value = 0L
    }

    fun resetRepairForm() {
        _repairSubmissionState.value = SubmissionState.Idle
    }

    fun resetPartForm() {
        _partSubmissionState.value = SubmissionState.Idle
    }

    // ═══════════════════════════════════════════════════════════
    //  SEALED CLASSES
    // ═══════════════════════════════════════════════════════════

    sealed class ScreenState {
        data object Loading : ScreenState()
        data object Ready : ScreenState()
        data class Error(val message: String) : ScreenState()
    }

    sealed class SubmissionState {
        data object Idle : SubmissionState()
        data object Validating : SubmissionState()
        data object Submitting : SubmissionState()
        data object Success : SubmissionState()
        data class ValidationError(val message: String) : SubmissionState()
        data class Error(val message: String) : SubmissionState()
    }
}
