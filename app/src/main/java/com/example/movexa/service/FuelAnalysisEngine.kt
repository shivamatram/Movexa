package com.example.movexa.service

import android.util.Log
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.FuelLog
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.AlertType
import com.example.movexa.data.repository.impl.AlertRepositoryImpl
import com.example.movexa.data.repository.impl.FuelLogRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * ═══════════════════════════════════════════════════════════════
 *  FUEL ANALYSIS ENGINE
 * ═══════════════════════════════════════════════════════════════
 *
 * Detects anomalies in fuel consumption and generates alerts
 * for fleet managers. Runs post-fuel-log-submission to identify:
 *
 * ● **Very low mileage** — significantly below expected range
 * ● **Sudden mileage drop** — sharp decline from recent average
 * ● **Possible fuel theft** — suspiciously low efficiency
 * ● **Overfueling** — quantity exceeds tank capacity norms
 * ● **Invalid odometer** — odometer went backwards or jumped
 *
 * ═══════════════════════════════════════════════════════════════
 * THRESHOLDS
 * ═══════════════════════════════════════════════════════════════
 *
 * Each threshold is tuned for typical Indian fleet vehicles
 * (trucks, tempos, cars). These can be made configurable via
 * Firestore remote config in the future.
 *
 * ═══════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════
 *
 *   val engine = FuelAnalysisEngine(viewModelScope)
 *   val anomalies = engine.analyzeFuelLog(fuelLog, vehicle, history)
 *   // anomalies is a list of detected issues
 *   engine.createAnomalyAlerts(anomalies, fuelLog)
 *   // fires alerts asynchronously
 *
 * ═══════════════════════════════════════════════════════════════
 */
class FuelAnalysisEngine(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "FuelAnalysisEngine"

        // ── Mileage thresholds (km/L) ───────────────────────────
        /** Absolute minimum mileage — anything below is anomalous */
        const val ABSOLUTE_MIN_MILEAGE = 2.0

        /** Percentage drop from average to flag as suspicious */
        const val MILEAGE_DROP_PERCENT = 0.40 // 40% drop

        /** Percentage drop from average to flag as possible theft */
        const val THEFT_THRESHOLD_PERCENT = 0.60 // 60% drop

        // ── Fuel quantity thresholds ────────────────────────────
        /** Max litres for a single fill — beyond is suspicious */
        const val MAX_SINGLE_FILL_LITRES = 500.0

        /** Min litres for a valid entry */
        const val MIN_FUEL_QUANTITY = 1.0

        // ── Odometer thresholds ─────────────────────────────────
        /** Max km between fills — beyond 5000 km is suspicious */
        const val MAX_DISTANCE_BETWEEN_FILLS = 5000L

        /** Min distance for mileage to be meaningful (km) */
        const val MIN_DISTANCE_FOR_MILEAGE = 5L

        // ── History requirements ────────────────────────────────
        /** Minimum history entries needed for trend analysis */
        const val MIN_HISTORY_FOR_TREND = 3

        // ── Expected mileage ranges per vehicle type (km/L) ────
        /** Default expected mileage ranges for common fleet vehicles */
        val MILEAGE_RANGES: Map<String, Pair<Double, Double>> = mapOf(
            "TRUCK" to (3.0 to 8.0),
            "MINI_TRUCK" to (6.0 to 12.0),
            "TEMPO" to (8.0 to 14.0),
            "CAR" to (10.0 to 22.0),
            "SUV" to (8.0 to 16.0),
            "VAN" to (8.0 to 14.0),
            "BUS" to (3.0 to 7.0),
            "AUTO" to (20.0 to 35.0),
            "BIKE" to (30.0 to 60.0),
            "OTHER" to (5.0 to 20.0)
        )
    }

    // ─── Repositories ───────────────────────────────────────────
    private val alertRepository = AlertRepositoryImpl()
    private val fuelLogRepository = FuelLogRepositoryImpl()
    private val vehicleRepository = VehicleRepositoryImpl()

    // ─── Concurrency ────────────────────────────────────────────
    private val analysisLock = Mutex()

    // ═══════════════════════════════════════════════════════════
    //  PRIMARY ANALYSIS
    // ═══════════════════════════════════════════════════════════

    /**
     * Analyse a newly submitted fuel log against the vehicle data
     * and historical fuel logs. Returns a list of detected anomalies.
     *
     * @param fuelLog       The newly created fuel log.
     * @param vehicle       The vehicle associated with the log.
     * @param recentHistory Previous fuel logs for the same vehicle,
     *                      ordered by timestamp descending (newest first).
     *                      Pass null to auto-fetch from Firestore.
     * @return List of [FuelAnomaly] objects describing detected issues.
     */
    suspend fun analyzeFuelLog(
        fuelLog: FuelLog,
        vehicle: Vehicle,
        recentHistory: List<FuelLog>? = null
    ): List<FuelAnomaly> = analysisLock.withLock {
        val anomalies = mutableListOf<FuelAnomaly>()

        // Fetch history if not provided
        val history = recentHistory ?: fetchVehicleFuelHistory(vehicle.vehicleId)

        Log.d(TAG, "Analyzing fuel log ${fuelLog.fuelId} for vehicle ${vehicle.number}")
        Log.d(TAG, "  mileage=${fuelLog.mileage}, qty=${fuelLog.quantity}, " +
                "odometer=${fuelLog.odometer}, history=${history.size} entries")

        // ── 1. Basic Validation Anomalies ───────────────────────
        checkBasicValidation(fuelLog, anomalies)

        // ── 2. Mileage Analysis ─────────────────────────────────
        if (fuelLog.mileage > 0) {
            checkAbsoluteMileage(fuelLog, vehicle, anomalies)
            checkMileageTrend(fuelLog, vehicle, history, anomalies)
        }

        // ── 3. Fuel Quantity Anomalies ──────────────────────────
        checkFuelQuantity(fuelLog, vehicle, anomalies)

        // ── 4. Odometer Anomalies ───────────────────────────────
        checkOdometerAnomalies(fuelLog, vehicle, history, anomalies)

        // ── 5. Fuel Theft Detection ─────────────────────────────
        checkPossibleTheft(fuelLog, vehicle, history, anomalies)

        // ── 6. Cost Anomalies ───────────────────────────────────
        checkCostAnomalies(fuelLog, history, anomalies)

        Log.d(TAG, "Analysis complete: ${anomalies.size} anomalies detected")
        anomalies.forEach { Log.d(TAG, "  → ${it.type}: ${it.title}") }

        return anomalies
    }

    /**
     * Fire-and-forget wrapper that analyses and creates alerts.
     * Meant to be called right after a successful fuel log creation.
     */
    fun analyzeAndAlertAsync(
        fuelLog: FuelLog,
        vehicle: Vehicle,
        recentHistory: List<FuelLog>? = null
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val anomalies = analyzeFuelLog(fuelLog, vehicle, recentHistory)
                if (anomalies.isNotEmpty()) {
                    createAnomalyAlerts(anomalies, fuelLog)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Async analysis failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ANOMALY CHECKS
    // ═══════════════════════════════════════════════════════════

    /**
     * Basic validation — catches obviously invalid entries.
     */
    private fun checkBasicValidation(
        fuelLog: FuelLog,
        anomalies: MutableList<FuelAnomaly>
    ) {
        // Zero or negative quantity
        if (fuelLog.quantity <= 0) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.INVALID_ENTRY,
                    severity = AnomalySeverity.HIGH,
                    title = "Invalid Fuel Quantity",
                    message = "Fuel quantity is zero or negative (${fuelLog.quantity}L). " +
                            "This entry may be erroneous.",
                    mileageValue = fuelLog.mileage,
                    expectedRange = "$MIN_FUEL_QUANTITY+ litres"
                )
            )
        }

        // Zero odometer
        if (fuelLog.odometer <= 0) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.INVALID_ENTRY,
                    severity = AnomalySeverity.MEDIUM,
                    title = "Missing Odometer Reading",
                    message = "Odometer reading is zero. " +
                            "Cannot calculate mileage without valid odometer.",
                    mileageValue = 0.0,
                    expectedRange = "Valid odometer > 0"
                )
            )
        }
    }

    /**
     * Check if the absolute mileage falls below expected range
     * for the vehicle type.
     */
    private fun checkAbsoluteMileage(
        fuelLog: FuelLog,
        vehicle: Vehicle,
        anomalies: MutableList<FuelAnomaly>
    ) {
        val vehicleType = vehicle.type.name
        val expectedRange = MILEAGE_RANGES[vehicleType] ?: MILEAGE_RANGES["OTHER"]!!

        // Below absolute minimum
        if (fuelLog.mileage < ABSOLUTE_MIN_MILEAGE) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.VERY_LOW_MILEAGE,
                    severity = AnomalySeverity.CRITICAL,
                    title = "Critically Low Mileage",
                    message = "Mileage of %.1f km/L is critically low for a %s. ".format(
                        fuelLog.mileage, vehicle.type.displayName
                    ) + "Expected range: %.1f–%.1f km/L. ".format(
                        expectedRange.first, expectedRange.second
                    ) + "Please investigate for mechanical issues or data errors.",
                    mileageValue = fuelLog.mileage,
                    expectedRange = "%.1f–%.1f km/L".format(
                        expectedRange.first, expectedRange.second
                    )
                )
            )
        }
        // Below expected range minimum
        else if (fuelLog.mileage < expectedRange.first) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.VERY_LOW_MILEAGE,
                    severity = AnomalySeverity.HIGH,
                    title = "Below-Average Mileage",
                    message = "Mileage of %.1f km/L is below the expected range for a %s ".format(
                        fuelLog.mileage, vehicle.type.displayName
                    ) + "(%.1f–%.1f km/L). Consider checking vehicle condition.".format(
                        expectedRange.first, expectedRange.second
                    ),
                    mileageValue = fuelLog.mileage,
                    expectedRange = "%.1f–%.1f km/L".format(
                        expectedRange.first, expectedRange.second
                    )
                )
            )
        }
    }

    /**
     * Compare current mileage against recent average to detect
     * sudden drops.
     */
    private fun checkMileageTrend(
        fuelLog: FuelLog,
        vehicle: Vehicle,
        history: List<FuelLog>,
        anomalies: MutableList<FuelAnomaly>
    ) {
        if (history.size < MIN_HISTORY_FOR_TREND) return

        // Calculate average mileage from history (exclude zero mileage)
        val validHistory = history.filter { it.mileage > 0 }
        if (validHistory.isEmpty()) return

        val avgMileage = validHistory.map { it.mileage }.average()
        if (avgMileage <= 0) return

        val dropPercent = (avgMileage - fuelLog.mileage) / avgMileage

        // Significant drop (> 40%)
        if (dropPercent >= MILEAGE_DROP_PERCENT && dropPercent < THEFT_THRESHOLD_PERCENT) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.SUDDEN_DROP,
                    severity = AnomalySeverity.HIGH,
                    title = "Sudden Mileage Drop",
                    message = "Mileage dropped %.0f%% from your average of %.1f km/L ".format(
                        dropPercent * 100, avgMileage
                    ) + "to %.1f km/L. This may indicate a vehicle issue, ".format(
                        fuelLog.mileage
                    ) + "heavy load, or driving conditions change.",
                    mileageValue = fuelLog.mileage,
                    expectedRange = "≥ %.1f km/L (recent average)".format(avgMileage)
                )
            )
        }
    }

    /**
     * Check fuel quantity for overfill or suspicious amounts.
     */
    private fun checkFuelQuantity(
        fuelLog: FuelLog,
        vehicle: Vehicle,
        anomalies: MutableList<FuelAnomaly>
    ) {
        // Overfill — exceeds max single fill
        if (fuelLog.quantity > MAX_SINGLE_FILL_LITRES) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.OVERFUELING,
                    severity = AnomalySeverity.HIGH,
                    title = "Excessive Fuel Quantity",
                    message = "Fuel entry of %.1f litres exceeds the maximum ".format(
                        fuelLog.quantity
                    ) + "expected single fill of %.0f litres. ".format(
                        MAX_SINGLE_FILL_LITRES
                    ) + "Please verify the entry is correct.",
                    mileageValue = fuelLog.mileage,
                    expectedRange = "≤ %.0f litres per fill".format(MAX_SINGLE_FILL_LITRES)
                )
            )
        }

        // Very small quantity (possible accidental entry)
        if (fuelLog.quantity in 0.01..MIN_FUEL_QUANTITY) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.INVALID_ENTRY,
                    severity = AnomalySeverity.LOW,
                    title = "Very Small Fuel Quantity",
                    message = "Fuel entry of %.2f litres is unusually small. ".format(
                        fuelLog.quantity
                    ) + "This may be a partial fill or data entry error.",
                    mileageValue = fuelLog.mileage,
                    expectedRange = "> %.0f litres".format(MIN_FUEL_QUANTITY)
                )
            )
        }
    }

    /**
     * Check for odometer inconsistencies — rollback, excessive jump,
     * or mismatch with vehicle's lastOdometer.
     */
    private fun checkOdometerAnomalies(
        fuelLog: FuelLog,
        vehicle: Vehicle,
        history: List<FuelLog>,
        anomalies: MutableList<FuelAnomaly>
    ) {
        val lastOdometer = vehicle.lastOdometer

        // Odometer went backwards
        if (lastOdometer > 0 && fuelLog.odometer < lastOdometer) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.ODOMETER_ROLLBACK,
                    severity = AnomalySeverity.CRITICAL,
                    title = "Odometer Rollback Detected",
                    message = "New odometer (%,d km) is less than the previous ".format(
                        fuelLog.odometer
                    ) + "reading (%,d km). This could indicate ".format(lastOdometer) +
                            "odometer tampering, incorrect entry, or vehicle swap.",
                    mileageValue = fuelLog.mileage,
                    expectedRange = "> %,d km".format(lastOdometer)
                )
            )
        }

        // Excessive distance between fills
        if (lastOdometer > 0) {
            val distance = fuelLog.odometer - lastOdometer
            if (distance > MAX_DISTANCE_BETWEEN_FILLS) {
                anomalies.add(
                    FuelAnomaly(
                        type = AnomalyType.EXCESSIVE_DISTANCE,
                        severity = AnomalySeverity.MEDIUM,
                        title = "Unusually Long Distance",
                        message = "Distance since last fill is %,d km, ".format(distance) +
                                "which exceeds the expected maximum of %,d km. ".format(
                                    MAX_DISTANCE_BETWEEN_FILLS
                                ) + "Possible missed fuel entries or long-haul trip.",
                        mileageValue = fuelLog.mileage,
                        expectedRange = "≤ %,d km between fills".format(
                            MAX_DISTANCE_BETWEEN_FILLS
                        )
                    )
                )
            }
        }
    }

    /**
     * Detect possible fuel theft by comparing mileage drop against
     * the theft threshold.
     */
    private fun checkPossibleTheft(
        fuelLog: FuelLog,
        vehicle: Vehicle,
        history: List<FuelLog>,
        anomalies: MutableList<FuelAnomaly>
    ) {
        if (history.size < MIN_HISTORY_FOR_TREND) return

        val validHistory = history.filter { it.mileage > 0 }
        if (validHistory.isEmpty()) return

        val avgMileage = validHistory.map { it.mileage }.average()
        if (avgMileage <= 0 || fuelLog.mileage <= 0) return

        val dropPercent = (avgMileage - fuelLog.mileage) / avgMileage

        // Severe drop (> 60%) — possible theft
        if (dropPercent >= THEFT_THRESHOLD_PERCENT) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.POSSIBLE_THEFT,
                    severity = AnomalySeverity.CRITICAL,
                    title = "Possible Fuel Theft",
                    message = "Mileage dropped %.0f%% from average ".format(dropPercent * 100) +
                            "(%.1f → %.1f km/L). ".format(avgMileage, fuelLog.mileage) +
                            "This severe deviation may indicate fuel siphoning, " +
                            "odometer manipulation, or significant vehicle malfunction. " +
                            "Immediate investigation is recommended.",
                    mileageValue = fuelLog.mileage,
                    expectedRange = "≥ %.1f km/L (historical avg)".format(avgMileage)
                )
            )
        }
    }

    /**
     * Check fuel cost against recent entries for significant
     * deviations (price gouging or incorrect entry).
     */
    private fun checkCostAnomalies(
        fuelLog: FuelLog,
        history: List<FuelLog>,
        anomalies: MutableList<FuelAnomaly>
    ) {
        if (history.size < MIN_HISTORY_FOR_TREND) return
        if (fuelLog.cost <= 0 || fuelLog.quantity <= 0) return

        val currentRate = fuelLog.costPerLitre

        val validHistory = history.filter { it.costPerLitre > 0 }
        if (validHistory.isEmpty()) return

        val avgRate = validHistory.map { it.costPerLitre }.average()
        if (avgRate <= 0) return

        val deviation = kotlin.math.abs(currentRate - avgRate) / avgRate

        // More than 30% deviation in price per litre
        if (deviation > 0.30) {
            anomalies.add(
                FuelAnomaly(
                    type = AnomalyType.COST_ANOMALY,
                    severity = AnomalySeverity.MEDIUM,
                    title = "Unusual Fuel Cost",
                    message = "Fuel rate of ₹%.2f/L deviates %.0f%% from ".format(
                        currentRate, deviation * 100
                    ) + "the recent average of ₹%.2f/L. ".format(avgRate) +
                            "Verify the bill amount and quantity entered.",
                    mileageValue = fuelLog.mileage,
                    expectedRange = "~₹%.2f/L (recent avg)".format(avgRate)
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ALERT GENERATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Create fleet alerts for each detected anomaly.
     * Fires asynchronously — does not block the caller.
     */
    suspend fun createAnomalyAlerts(
        anomalies: List<FuelAnomaly>,
        fuelLog: FuelLog
    ) {
        anomalies.forEach { anomaly ->
            try {
                val alert = Alert(
                    alertId = UUID.randomUUID().toString(),
                    type = anomaly.alertType,
                    vehicleId = fuelLog.vehicleId,
                    driverId = fuelLog.driverId,
                    companyId = fuelLog.companyId,
                    priority = anomaly.alertPriority,
                    title = anomaly.title,
                    message = anomaly.message,
                    timestamp = System.currentTimeMillis(),
                    actionRequired = anomaly.severity == AnomalySeverity.CRITICAL,
                    autoGenerated = true,
                    metadata = mapOf(
                        "fuelLogId" to fuelLog.fuelId,
                        "anomalyType" to anomaly.type.name,
                        "mileageValue" to anomaly.mileageValue,
                        "expectedRange" to anomaly.expectedRange,
                        "fuelQuantity" to fuelLog.quantity,
                        "odometer" to fuelLog.odometer,
                        "severity" to anomaly.severity.name
                    )
                )

                val result = alertRepository.createAlert(alert)
                when (result) {
                    is ResultState.Success -> {
                        Log.d(TAG, "Created alert for anomaly: ${anomaly.type}")
                    }
                    is ResultState.Error -> {
                        Log.w(TAG, "Failed to create alert: ${result.message}")
                    }
                    else -> { /* Ignore loading/idle */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating anomaly alert", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MILEAGE CALCULATION HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Calculate mileage from the new odometer reading, the vehicle's
     * last odometer, and the fuel quantity.
     *
     * @param newOdometer   Current odometer reading in km.
     * @param lastOdometer  Previous odometer reading from vehicle doc.
     * @param fuelQuantity  Litres of fuel filled.
     * @return Mileage in km/L, or null if not calculable.
     */
    fun calculateMileage(
        newOdometer: Long,
        lastOdometer: Long,
        fuelQuantity: Double
    ): MileageResult {
        // Validate inputs
        if (newOdometer <= 0) {
            return MileageResult.Invalid("Enter a valid odometer reading")
        }
        if (fuelQuantity <= 0) {
            return MileageResult.Invalid("Enter fuel quantity")
        }
        if (lastOdometer <= 0) {
            return MileageResult.FirstEntry(
                "First fuel entry — mileage will be available from next fill"
            )
        }
        if (newOdometer <= lastOdometer) {
            return MileageResult.Invalid(
                "Odometer must be greater than last reading (%,d km)".format(lastOdometer)
            )
        }

        val distance = newOdometer - lastOdometer

        if (distance < MIN_DISTANCE_FOR_MILEAGE) {
            return MileageResult.Invalid(
                "Distance too short (%d km). Drive at least %d km for accurate mileage.".format(
                    distance, MIN_DISTANCE_FOR_MILEAGE
                )
            )
        }

        val mileage = distance.toDouble() / fuelQuantity

        // Sanity check the result
        val quality = when {
            mileage < ABSOLUTE_MIN_MILEAGE -> MileageQuality.SUSPICIOUS
            mileage > 100 -> MileageQuality.SUSPICIOUS
            mileage < 5 -> MileageQuality.POOR
            mileage < 8 -> MileageQuality.BELOW_AVERAGE
            mileage < 15 -> MileageQuality.AVERAGE
            mileage < 25 -> MileageQuality.GOOD
            else -> MileageQuality.EXCELLENT
        }

        return MileageResult.Calculated(
            mileage = mileage,
            distance = distance,
            quality = quality
        )
    }

    /**
     * Get expected mileage range for a vehicle type.
     */
    fun getExpectedMileageRange(vehicleType: String): Pair<Double, Double> {
        return MILEAGE_RANGES[vehicleType] ?: MILEAGE_RANGES["OTHER"]!!
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA FETCHING
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch recent fuel history for a vehicle, sorted newest first.
     * Used internally when no history is provided to [analyzeFuelLog].
     */
    private suspend fun fetchVehicleFuelHistory(
        vehicleId: String
    ): List<FuelLog> {
        return when (val result = fuelLogRepository.getFuelLogsByVehicle(vehicleId)) {
            is ResultState.Success -> result.data
                .sortedByDescending { it.timestamp }
                .take(20) // Keep last 20 entries for analysis
            else -> {
                Log.w(TAG, "Failed to fetch fuel history for vehicle=$vehicleId")
                emptyList()
            }
        }
    }

    /**
     * Fetch the vehicle object for a given vehicleId.
     */
    suspend fun fetchVehicle(vehicleId: String): Vehicle? {
        return when (val result = vehicleRepository.getVehicleById(vehicleId)) {
            is ResultState.Success -> result.data
            else -> {
                Log.w(TAG, "Failed to fetch vehicle=$vehicleId")
                null
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA CLASSES & ENUMS
    // ═══════════════════════════════════════════════════════════

    /**
     * Represents a detected fuel anomaly.
     */
    data class FuelAnomaly(
        val type: AnomalyType,
        val severity: AnomalySeverity,
        val title: String,
        val message: String,
        val mileageValue: Double,
        val expectedRange: String
    ) {
        /** Map anomaly type to the appropriate [AlertType]. */
        val alertType: AlertType
            get() = when (type) {
                AnomalyType.VERY_LOW_MILEAGE -> AlertType.LOW_FUEL
                AnomalyType.SUDDEN_DROP -> AlertType.LOW_FUEL
                AnomalyType.POSSIBLE_THEFT -> AlertType.LOW_FUEL
                AnomalyType.OVERFUELING -> AlertType.LOW_FUEL
                AnomalyType.ODOMETER_ROLLBACK -> AlertType.GENERAL
                AnomalyType.EXCESSIVE_DISTANCE -> AlertType.GENERAL
                AnomalyType.INVALID_ENTRY -> AlertType.GENERAL
                AnomalyType.COST_ANOMALY -> AlertType.GENERAL
            }

        /** Map severity to [AlertPriority]. */
        val alertPriority: AlertPriority
            get() = when (severity) {
                AnomalySeverity.CRITICAL -> AlertPriority.CRITICAL
                AnomalySeverity.HIGH -> AlertPriority.HIGH
                AnomalySeverity.MEDIUM -> AlertPriority.MEDIUM
                AnomalySeverity.LOW -> AlertPriority.LOW
            }
    }

    /**
     * Types of fuel anomalies detectable by the engine.
     */
    enum class AnomalyType(val displayName: String) {
        VERY_LOW_MILEAGE("Very Low Mileage"),
        SUDDEN_DROP("Sudden Mileage Drop"),
        POSSIBLE_THEFT("Possible Fuel Theft"),
        OVERFUELING("Overfueling"),
        ODOMETER_ROLLBACK("Odometer Rollback"),
        EXCESSIVE_DISTANCE("Excessive Distance"),
        INVALID_ENTRY("Invalid Entry"),
        COST_ANOMALY("Cost Anomaly")
    }

    /**
     * Severity levels for fuel anomalies.
     */
    enum class AnomalySeverity(val level: Int) {
        LOW(0),
        MEDIUM(1),
        HIGH(2),
        CRITICAL(3)
    }

    /**
     * Result of a mileage calculation.
     */
    sealed class MileageResult {
        /** Valid mileage was calculated. */
        data class Calculated(
            val mileage: Double,
            val distance: Long,
            val quality: MileageQuality
        ) : MileageResult()

        /** First entry for this vehicle — no previous odometer. */
        data class FirstEntry(val message: String) : MileageResult()

        /** Invalid inputs — cannot calculate. */
        data class Invalid(val message: String) : MileageResult()
    }

    /**
     * Quality rating for a calculated mileage value.
     */
    enum class MileageQuality(val displayName: String, val colorLabel: String) {
        EXCELLENT("Excellent", "green"),
        GOOD("Good", "green"),
        AVERAGE("Average", "amber"),
        BELOW_AVERAGE("Below Average", "orange"),
        POOR("Poor", "red"),
        SUSPICIOUS("Suspicious", "red")
    }
}
