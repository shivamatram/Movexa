package com.example.movexa.service

import android.util.Log
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.PartHistory
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.AlertStatus
import com.example.movexa.data.model.enums.AlertType
import com.example.movexa.data.model.enums.ServiceType
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import com.example.movexa.data.repository.impl.PartHistoryRepositoryImpl
import com.example.movexa.data.repository.impl.ServiceRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 *  MAINTENANCE SCHEDULER ENGINE
 * ═══════════════════════════════════════════════════════════════════
 *
 * Central engine for all maintenance & service scheduling logic.
 *
 * Responsibilities:
 *  ● Auto-calculate nextServiceKm based on ServiceType
 *  ● Detect when currentOdometer >= nextServiceKm → create SERVICE_DUE alert
 *  ● Detect when part life is exhausted → create alert
 *  ● Evaluate fleet-wide maintenance readiness
 *  ● Set vehicle status to SERVICE when maintenance begins
 *  ● Set vehicle status to AVAILABLE when maintenance completes
 *  ● Prevent trip assignment if service is overdue
 *  ● Calculate maintenance cost summaries
 *  ● Generate priority badges for overdue vehicles
 *
 * Service Interval Defaults (km):
 *  OIL_CHANGE        →  5 000 km
 *  TIRE_ROTATION      → 10 000 km
 *  BRAKE_INSPECTION   → 15 000 km
 *  ENGINE_TUNE        → 20 000 km
 *  TRANSMISSION       → 40 000 km
 *  BATTERY_CHECK      → 25 000 km
 *  AIR_FILTER         →  8 000 km
 *  COOLANT_FLUSH      → 30 000 km
 *  FULL_SERVICE       → 15 000 km
 *  PERIODIC_MAINTENANCE → 10 000 km
 *  INSURANCE_RENEWAL  → N/A (date-based)
 *  FITNESS_CHECK      → N/A (date-based)
 *  EMISSION_TEST      → 20 000 km
 *  OTHER              → 10 000 km (fallback)
 *
 * ═══════════════════════════════════════════════════════════════════
 */
class MaintenanceScheduler(
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "MaintenanceScheduler"

        // ── Service Interval Defaults (km) ──────────────────────
        val SERVICE_INTERVALS: Map<ServiceType, Long> = mapOf(
            ServiceType.OIL_CHANGE to 5_000L,
            ServiceType.TIRE_ROTATION to 10_000L,
            ServiceType.BRAKE_INSPECTION to 15_000L,
            ServiceType.ENGINE_TUNE to 20_000L,
            ServiceType.TRANSMISSION to 40_000L,
            ServiceType.BATTERY_CHECK to 25_000L,
            ServiceType.AIR_FILTER to 8_000L,
            ServiceType.COOLANT_FLUSH to 30_000L,
            ServiceType.FULL_SERVICE to 15_000L,
            ServiceType.PERIODIC_MAINTENANCE to 10_000L,
            ServiceType.EMISSION_TEST to 20_000L,
            ServiceType.OTHER to 10_000L
        )

        // ── Overdue Threshold Bands ─────────────────────────────
        private const val WARNING_THRESHOLD_KM = 500L    // <500 km → HIGH priority
        private const val UPCOMING_THRESHOLD_KM = 2_000L // <2000 km → MEDIUM priority
        private const val PART_WARNING_KM = 1_000L       // Part nearing expiry threshold
    }

    // ─── Repositories ───────────────────────────────────────────
    private val serviceRepository = ServiceRepositoryImpl()
    private val vehicleRepository = VehicleRepositoryImpl()
    private val alertRepository = AlertRepositoryImpl()
    private val partHistoryRepository = PartHistoryRepositoryImpl()

    // ═══════════════════════════════════════════════════════════
    //  SERVICE INTERVAL CALCULATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the default service interval for a given service type.
     *
     * @param serviceType The type of service.
     * @return Default interval in km, or 0 if date-based.
     */
    fun getDefaultInterval(serviceType: ServiceType): Long {
        return SERVICE_INTERVALS[serviceType] ?: 10_000L
    }

    /**
     * Auto-calculate the next service km from odometer and service type.
     *
     * @param currentOdometer Current vehicle odometer.
     * @param serviceType     The service being performed.
     * @return Next service due odometer reading.
     */
    fun calculateNextServiceKm(currentOdometer: Long, serviceType: ServiceType): Long {
        val interval = getDefaultInterval(serviceType)
        return if (interval > 0) currentOdometer + interval else 0L
    }

    /**
     * Calculate next part replacement km.
     *
     * @param changedAtKm    Odometer when part was installed.
     * @param expectedLifeKm Expected lifespan in km.
     * @return Odometer at which replacement is due.
     */
    fun calculateNextReplacementKm(changedAtKm: Long, expectedLifeKm: Long): Long {
        return if (expectedLifeKm > 0) changedAtKm + expectedLifeKm else 0L
    }

    // ═══════════════════════════════════════════════════════════
    //  SERVICE DUE DETECTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Check a vehicle's latest service records and create alerts for
     * any that are overdue or approaching their due km.
     *
     * @param vehicle The vehicle to check.
     * @return List of maintenance status items.
     */
    suspend fun checkServiceDue(vehicle: Vehicle): List<MaintenanceStatus> {
        val statuses = mutableListOf<MaintenanceStatus>()
        val currentOdo = vehicle.lastOdometer

        // Get all services for this vehicle
        val servicesResult = serviceRepository.getServicesByVehicle(vehicle.vehicleId)
        if (servicesResult !is ResultState.Success) return statuses

        val services = servicesResult.data

        // Group by service type and get latest completed per type
        val latestByType = services
            .filter { it.completed }
            .groupBy { it.serviceType }
            .mapValues { entry -> entry.value.maxByOrNull { it.date } }

        // Check each service type that has interval-based scheduling
        for ((serviceType, interval) in SERVICE_INTERVALS) {
            val lastService = latestByType[serviceType]

            if (lastService != null && lastService.nextServiceKm > 0) {
                val remaining = lastService.nextServiceKm - currentOdo
                val status = when {
                    remaining <= 0 -> MaintenanceStatus(
                        vehicleId = vehicle.vehicleId,
                        vehicleNumber = vehicle.number,
                        serviceType = serviceType,
                        lastServiceOdometer = lastService.odometer,
                        nextServiceKm = lastService.nextServiceKm,
                        currentOdometer = currentOdo,
                        remainingKm = remaining,
                        priority = AlertPriority.CRITICAL,
                        statusLabel = StatusLabel.OVERDUE
                    )
                    remaining <= WARNING_THRESHOLD_KM -> MaintenanceStatus(
                        vehicleId = vehicle.vehicleId,
                        vehicleNumber = vehicle.number,
                        serviceType = serviceType,
                        lastServiceOdometer = lastService.odometer,
                        nextServiceKm = lastService.nextServiceKm,
                        currentOdometer = currentOdo,
                        remainingKm = remaining,
                        priority = AlertPriority.HIGH,
                        statusLabel = StatusLabel.DUE_SOON
                    )
                    remaining <= UPCOMING_THRESHOLD_KM -> MaintenanceStatus(
                        vehicleId = vehicle.vehicleId,
                        vehicleNumber = vehicle.number,
                        serviceType = serviceType,
                        lastServiceOdometer = lastService.odometer,
                        nextServiceKm = lastService.nextServiceKm,
                        currentOdometer = currentOdo,
                        remainingKm = remaining,
                        priority = AlertPriority.MEDIUM,
                        statusLabel = StatusLabel.UPCOMING
                    )
                    else -> MaintenanceStatus(
                        vehicleId = vehicle.vehicleId,
                        vehicleNumber = vehicle.number,
                        serviceType = serviceType,
                        lastServiceOdometer = lastService.odometer,
                        nextServiceKm = lastService.nextServiceKm,
                        currentOdometer = currentOdo,
                        remainingKm = remaining,
                        priority = AlertPriority.LOW,
                        statusLabel = StatusLabel.OK
                    )
                }
                statuses.add(status)
            } else {
                // No record for this service type — mark as NO_RECORD
                statuses.add(
                    MaintenanceStatus(
                        vehicleId = vehicle.vehicleId,
                        vehicleNumber = vehicle.number,
                        serviceType = serviceType,
                        lastServiceOdometer = 0L,
                        nextServiceKm = 0L,
                        currentOdometer = currentOdo,
                        remainingKm = 0L,
                        priority = AlertPriority.LOW,
                        statusLabel = StatusLabel.NO_RECORD
                    )
                )
            }
        }

        return statuses.sortedByDescending { it.priority.ordinal }
    }

    // ═══════════════════════════════════════════════════════════
    //  PART EXPIRY DETECTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Check all parts for a vehicle and identify expired or near-expiry.
     *
     * @param vehicleId      Vehicle to check.
     * @param currentOdometer Current odometer reading.
     * @return List of part status items sorted by urgency.
     */
    suspend fun checkPartExpiry(
        vehicleId: String,
        currentOdometer: Long
    ): List<PartStatus> {
        val statuses = mutableListOf<PartStatus>()

        val partsResult = partHistoryRepository.getPartsByVehicle(vehicleId)
        if (partsResult !is ResultState.Success) return statuses

        // Group by part name, take latest entry per part
        val latestParts = partsResult.data
            .groupBy { it.partName.lowercase() }
            .mapValues { entry -> entry.value.maxByOrNull { it.changedAtKm } }

        for ((_, part) in latestParts) {
            if (part == null || part.expectedLifeKm <= 0) continue

            val nextReplacementKm = part.changedAtKm + part.expectedLifeKm
            val remaining = nextReplacementKm - currentOdometer
            val usagePercent = part.usagePercent(currentOdometer)

            val status = when {
                remaining <= 0 -> PartStatus(
                    partId = part.partId,
                    partName = part.partName,
                    changedAtKm = part.changedAtKm,
                    expectedLifeKm = part.expectedLifeKm,
                    nextReplacementKm = nextReplacementKm,
                    remainingKm = remaining,
                    usagePercent = usagePercent,
                    priority = AlertPriority.CRITICAL,
                    statusLabel = StatusLabel.OVERDUE
                )
                remaining <= PART_WARNING_KM -> PartStatus(
                    partId = part.partId,
                    partName = part.partName,
                    changedAtKm = part.changedAtKm,
                    expectedLifeKm = part.expectedLifeKm,
                    nextReplacementKm = nextReplacementKm,
                    remainingKm = remaining,
                    usagePercent = usagePercent,
                    priority = AlertPriority.HIGH,
                    statusLabel = StatusLabel.DUE_SOON
                )
                remaining <= PART_WARNING_KM * 3 -> PartStatus(
                    partId = part.partId,
                    partName = part.partName,
                    changedAtKm = part.changedAtKm,
                    expectedLifeKm = part.expectedLifeKm,
                    nextReplacementKm = nextReplacementKm,
                    remainingKm = remaining,
                    usagePercent = usagePercent,
                    priority = AlertPriority.MEDIUM,
                    statusLabel = StatusLabel.UPCOMING
                )
                else -> PartStatus(
                    partId = part.partId,
                    partName = part.partName,
                    changedAtKm = part.changedAtKm,
                    expectedLifeKm = part.expectedLifeKm,
                    nextReplacementKm = nextReplacementKm,
                    remainingKm = remaining,
                    usagePercent = usagePercent,
                    priority = AlertPriority.LOW,
                    statusLabel = StatusLabel.OK
                )
            }
            statuses.add(status)
        }

        return statuses.sortedByDescending { it.priority.ordinal }
    }

    // ═══════════════════════════════════════════════════════════
    //  ALERT GENERATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Generate alerts for overdue / due-soon service items.
     * Only creates alerts for OVERDUE and DUE_SOON statuses.
     *
     * @param vehicle  The vehicle being checked.
     * @param statuses Maintenance status list from [checkServiceDue].
     */
    suspend fun generateServiceAlerts(
        vehicle: Vehicle,
        statuses: List<MaintenanceStatus>
    ) {
        val alertable = statuses.filter {
            it.statusLabel == StatusLabel.OVERDUE || it.statusLabel == StatusLabel.DUE_SOON
        }

        for (status in alertable) {
            val title = when (status.statusLabel) {
                StatusLabel.OVERDUE ->
                    "${status.serviceType.displayName} OVERDUE — ${vehicle.number}"
                StatusLabel.DUE_SOON ->
                    "${status.serviceType.displayName} due soon — ${vehicle.number}"
                else -> continue
            }

            val message = when (status.statusLabel) {
                StatusLabel.OVERDUE ->
                    "${status.serviceType.displayName} is overdue by " +
                            "${kotlin.math.abs(status.remainingKm)} km. " +
                            "Current: ${status.currentOdometer} km, " +
                            "Due at: ${status.nextServiceKm} km."
                StatusLabel.DUE_SOON ->
                    "${status.serviceType.displayName} is due within " +
                            "${status.remainingKm} km. " +
                            "Current: ${status.currentOdometer} km, " +
                            "Due at: ${status.nextServiceKm} km."
                else -> continue
            }

            val alert = Alert(
                type = AlertType.SERVICE_DUE,
                vehicleId = vehicle.vehicleId,
                companyId = vehicle.companyId,
                priority = status.priority,
                status = AlertStatus.ACTIVE,
                title = title,
                message = message,
                actionRequired = true,
                autoGenerated = true,
                metadata = mapOf(
                    "serviceType" to status.serviceType.name,
                    "nextServiceKm" to status.nextServiceKm,
                    "currentOdometer" to status.currentOdometer,
                    "remainingKm" to status.remainingKm
                )
            )

            val result = alertRepository.createAlert(alert)
            if (result is ResultState.Success) {
                Log.d(TAG, "Service alert created: ${result.data}")
            } else if (result is ResultState.Error) {
                Log.e(TAG, "Failed to create service alert: ${result.message}")
            }
        }
    }

    /**
     * Generate alerts for expired / near-expiry parts.
     *
     * @param vehicleId Vehicle ID.
     * @param companyId Company ID.
     * @param statuses  Part status list from [checkPartExpiry].
     */
    suspend fun generatePartAlerts(
        vehicleId: String,
        companyId: String,
        statuses: List<PartStatus>
    ) {
        val alertable = statuses.filter {
            it.statusLabel == StatusLabel.OVERDUE || it.statusLabel == StatusLabel.DUE_SOON
        }

        for (status in alertable) {
            val title = when (status.statusLabel) {
                StatusLabel.OVERDUE ->
                    "Part EXPIRED: ${status.partName}"
                StatusLabel.DUE_SOON ->
                    "Part expiring soon: ${status.partName}"
                else -> continue
            }

            val message = when (status.statusLabel) {
                StatusLabel.OVERDUE ->
                    "${status.partName} has exceeded its expected life by " +
                            "${kotlin.math.abs(status.remainingKm)} km. " +
                            "Replacement due at ${status.nextReplacementKm} km."
                StatusLabel.DUE_SOON ->
                    "${status.partName} will expire in ${status.remainingKm} km. " +
                            "Replacement due at ${status.nextReplacementKm} km."
                else -> continue
            }

            val alert = Alert(
                type = AlertType.SERVICE_DUE,
                vehicleId = vehicleId,
                companyId = companyId,
                priority = status.priority,
                status = AlertStatus.ACTIVE,
                title = title,
                message = message,
                actionRequired = true,
                autoGenerated = true,
                metadata = mapOf(
                    "partName" to status.partName,
                    "partId" to status.partId,
                    "nextReplacementKm" to status.nextReplacementKm,
                    "remainingKm" to status.remainingKm,
                    "usagePercent" to status.usagePercent
                )
            )

            val result = alertRepository.createAlert(alert)
            if (result is ResultState.Success) {
                Log.d(TAG, "Part alert created: ${result.data}")
            } else if (result is ResultState.Error) {
                Log.e(TAG, "Failed to create part alert: ${result.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE STATUS MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    /**
     * Set vehicle to SERVICE status when maintenance begins.
     *
     * @param vehicleId The vehicle entering service.
     * @return Success or error result.
     */
    suspend fun markVehicleInService(vehicleId: String): ResultState<Unit> {
        Log.d(TAG, "Marking vehicle $vehicleId as IN SERVICE")
        return vehicleRepository.updateVehicleStatus(vehicleId, VehicleStatus.SERVICE)
    }

    /**
     * Set vehicle back to AVAILABLE after maintenance completes.
     *
     * @param vehicleId The vehicle leaving service.
     * @return Success or error result.
     */
    suspend fun markVehicleAvailable(vehicleId: String): ResultState<Unit> {
        Log.d(TAG, "Marking vehicle $vehicleId as AVAILABLE")
        return vehicleRepository.updateVehicleStatus(vehicleId, VehicleStatus.AVAILABLE)
    }

    /**
     * Update vehicle odometer reading after a service or repair.
     *
     * @param vehicleId Vehicle ID.
     * @param odometer  New odometer reading.
     * @return Success or error result.
     */
    suspend fun updateVehicleOdometer(
        vehicleId: String,
        odometer: Long
    ): ResultState<Unit> {
        Log.d(TAG, "Updating vehicle $vehicleId odometer to $odometer")
        return vehicleRepository.updateOdometer(vehicleId, odometer)
    }

    // ═══════════════════════════════════════════════════════════
    //  FLEET-WIDE SCAN
    // ═══════════════════════════════════════════════════════════

    /**
     * Run a fleet-wide maintenance scan and generate alerts for
     * any vehicles with overdue or due-soon services/parts.
     *
     * @param companyId The company to scan.
     */
    fun runFleetMaintenanceScan(companyId: String) {
        scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting fleet maintenance scan for company $companyId")

            // 1. Get all vehicles
            val vehiclesResult = vehicleRepository.getVehiclesByCompany(companyId)
            if (vehiclesResult !is ResultState.Success) {
                Log.e(TAG, "Failed to load fleet vehicles")
                return@launch
            }

            val vehicles = vehiclesResult.data

            // 2. Check each vehicle
            for (vehicle in vehicles) {
                try {
                    // Service checks
                    val serviceStatuses = checkServiceDue(vehicle)
                    generateServiceAlerts(vehicle, serviceStatuses)

                    // Part checks
                    val partStatuses = checkPartExpiry(
                        vehicle.vehicleId,
                        vehicle.lastOdometer
                    )
                    generatePartAlerts(vehicle.vehicleId, vehicle.companyId, partStatuses)
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning vehicle ${vehicle.vehicleId}", e)
                }
            }

            Log.d(TAG, "Fleet maintenance scan complete. " +
                    "Scanned ${vehicles.size} vehicles.")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  COST ANALYSIS
    // ═══════════════════════════════════════════════════════════

    /**
     * Calculate total maintenance costs for a vehicle across
     * services, repairs, and parts.
     *
     * @param vehicleId Vehicle to analyze.
     * @return Cost summary breakdown.
     */
    suspend fun calculateMaintenanceCosts(
        vehicleId: String
    ): MaintenanceCostSummary {
        var serviceCost = 0.0
        var repairCost = 0.0
        var partsCost = 0.0

        val servicesResult = serviceRepository.getServicesByVehicle(vehicleId)
        if (servicesResult is ResultState.Success) {
            serviceCost = servicesResult.data.sumOf { it.cost }
        }

        val partsResult = partHistoryRepository.getPartsByVehicle(vehicleId)
        if (partsResult is ResultState.Success) {
            partsCost = partsResult.data.sumOf { it.cost }
        }

        return MaintenanceCostSummary(
            vehicleId = vehicleId,
            totalServiceCost = serviceCost,
            totalRepairCost = repairCost,
            totalPartsCost = partsCost,
            grandTotal = serviceCost + repairCost + partsCost,
            serviceCount = (servicesResult as? ResultState.Success)?.data?.size ?: 0,
            partsCount = (partsResult as? ResultState.Success)?.data?.size ?: 0
        )
    }

    /**
     * Check if a vehicle is eligible for trip assignment.
     * Verifies no OVERDUE services exist and status is not SERVICE.
     *
     * @param vehicle The vehicle to evaluate.
     * @return Eligibility result with reason if ineligible.
     */
    suspend fun isEligibleForTrip(vehicle: Vehicle): TripEligibility {
        // Check if vehicle status blocks assignment
        if (!vehicle.status.canAssignTrip()) {
            return TripEligibility(
                eligible = false,
                reason = "Vehicle status is ${vehicle.status.name}. " +
                        "Only AVAILABLE vehicles can be assigned trips."
            )
        }

        // Check for overdue services
        val statuses = checkServiceDue(vehicle)
        val overdue = statuses.filter { it.statusLabel == StatusLabel.OVERDUE }

        if (overdue.isNotEmpty()) {
            val overdueTypes = overdue.joinToString(", ") {
                it.serviceType.displayName
            }
            return TripEligibility(
                eligible = false,
                reason = "Overdue maintenance: $overdueTypes. " +
                        "Vehicle must be serviced before assignment."
            )
        }

        // Check for expired parts
        val partStatuses = checkPartExpiry(
            vehicle.vehicleId,
            vehicle.lastOdometer
        )
        val expiredParts = partStatuses.filter { it.statusLabel == StatusLabel.OVERDUE }

        if (expiredParts.isNotEmpty()) {
            val partNames = expiredParts.joinToString(", ") { it.partName }
            return TripEligibility(
                eligible = false,
                reason = "Expired parts: $partNames. " +
                        "Parts must be replaced before assignment."
            )
        }

        return TripEligibility(eligible = true, reason = "Vehicle is maintenance-ready.")
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════

    /**
     * Status label for maintenance/part items.
     */
    enum class StatusLabel(val label: String) {
        OVERDUE("Overdue"),
        DUE_SOON("Due Soon"),
        UPCOMING("Upcoming"),
        OK("OK"),
        NO_RECORD("No Record")
    }

    /**
     * Maintenance status for a single service type on a vehicle.
     */
    data class MaintenanceStatus(
        val vehicleId: String,
        val vehicleNumber: String,
        val serviceType: ServiceType,
        val lastServiceOdometer: Long,
        val nextServiceKm: Long,
        val currentOdometer: Long,
        val remainingKm: Long,
        val priority: AlertPriority,
        val statusLabel: StatusLabel
    )

    /**
     * Status of a single tracked part on a vehicle.
     */
    data class PartStatus(
        val partId: String,
        val partName: String,
        val changedAtKm: Long,
        val expectedLifeKm: Long,
        val nextReplacementKm: Long,
        val remainingKm: Long,
        val usagePercent: Float,
        val priority: AlertPriority,
        val statusLabel: StatusLabel
    )

    /**
     * Cost summary across services, repairs, and parts.
     */
    data class MaintenanceCostSummary(
        val vehicleId: String,
        val totalServiceCost: Double,
        val totalRepairCost: Double,
        val totalPartsCost: Double,
        val grandTotal: Double,
        val serviceCount: Int,
        val partsCount: Int
    )

    /**
     * Trip eligibility check result.
     */
    data class TripEligibility(
        val eligible: Boolean,
        val reason: String
    )
}
