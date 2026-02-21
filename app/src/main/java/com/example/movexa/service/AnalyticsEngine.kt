package com.example.movexa.service

import android.util.Log
import com.example.movexa.data.model.DriverSummary
import com.example.movexa.data.model.FuelLog
import com.example.movexa.data.model.Repair
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.data.repository.impl.DriverPerformanceRepositoryImpl
import com.example.movexa.data.repository.impl.DriverRepositoryImpl
import com.example.movexa.data.repository.impl.FuelLogRepositoryImpl
import com.example.movexa.data.repository.impl.RepairRepositoryImpl
import com.example.movexa.data.repository.impl.ServiceRepositoryImpl
import com.example.movexa.data.repository.impl.TripRepositoryImpl
import com.example.movexa.data.repository.impl.VehicleRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale

/**
 * AnalyticsEngine — production-grade analytics computation engine for Movexa.
 *
 * All heavy calculations run on [Dispatchers.IO] to keep the UI thread free.
 * Results are returned as immutable data classes that can be cached by the ViewModel.
 *
 * ─── Responsibilities ──────────────────────────────────────────────────
 *  • Monthly / periodic revenue aggregation
 *  • Fuel, service, repair cost breakdowns
 *  • Profit & loss computation (revenue − expenses)
 *  • Vehicle utilization ratio (active trip time / total available time)
 *  • Driver performance ranking using DriverSummary
 *  • Time-series datasets for chart rendering
 *  • Per-vehicle and per-driver drill-down analytics
 *  • Fleet-wide KPI snapshot
 *
 * ─── Thread Safety ─────────────────────────────────────────────────────
 *  Stateless: every public method receives parameters and returns results.
 *  No mutable shared state → safe for concurrent coroutine calls.
 */
class AnalyticsEngine {

    companion object {
        private const val TAG = "AnalyticsEngine"

        // ── Revenue Estimate ────────────────────────────────────────
        // In a real app this would come from an invoicing module.
        // For now we estimate revenue = ₹12 per km of completed trips.
        private const val REVENUE_PER_KM = 12.0

        // ── Utilization ─────────────────────────────────────────────
        // Working hours per day for utilization denominator
        private const val WORKING_HOURS_PER_DAY = 14
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val MILLIS_PER_DAY = 86_400_000L

        // ── Chart Constants ─────────────────────────────────────────
        private const val MAX_TREND_MONTHS = 12
        private const val TOP_DRIVERS_LIMIT = 10

        // ── Formatters ──────────────────────────────────────────────
        private val INR: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            maximumFractionDigits = 0
        }

        fun formatCurrency(amount: Double): String = INR.format(amount)
        fun formatPercent(ratio: Double): String = String.format(Locale.US, "%.1f%%", ratio * 100)
        fun formatKm(km: Double): String = String.format(Locale.US, "%,.1f km", km)
    }

    // ─── Repositories ───────────────────────────────────────────────────
    private val tripRepository = TripRepositoryImpl()
    private val fuelLogRepository = FuelLogRepositoryImpl()
    private val serviceRepository = ServiceRepositoryImpl()
    private val repairRepository = RepairRepositoryImpl()
    private val vehicleRepository = VehicleRepositoryImpl()
    private val driverRepository = DriverRepositoryImpl()
    private val performanceRepository = DriverPerformanceRepositoryImpl()

    // ═════════════════════════════════════════════════════════════════════
    //  1. FINANCIAL SNAPSHOT
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Compute a complete financial overview for a company within a date range.
     *
     * Runs all cost/revenue queries in parallel via [coroutineScope] + [async].
     *
     * @param companyId  Firestore company identifier (admin UID).
     * @param startMs    Start of the date window (epoch millis, inclusive).
     * @param endMs      End of the date window (epoch millis, inclusive).
     * @return [FinancialSnapshot] with revenue, costs, and profit.
     */
    suspend fun computeFinancialSnapshot(
        companyId: String,
        startMs: Long,
        endMs: Long
    ): FinancialSnapshot = withContext(Dispatchers.IO) {
        Log.d(TAG, "computeFinancialSnapshot($companyId, $startMs..$endMs)")

        coroutineScope {
            // Fire all queries in parallel
            val tripsDeferred = async { tripRepository.getTripsByDateRange(companyId, startMs, endMs) }
            val fuelCostDeferred = async { fuelLogRepository.getTotalFuelCost(companyId, startMs, endMs) }
            val serviceCostDeferred = async { serviceRepository.getTotalServiceCost(companyId, startMs, endMs) }
            val repairCostDeferred = async { repairRepository.getTotalRepairCost(companyId, startMs, endMs) }
            val fuelLogsDeferred = async { fuelLogRepository.getFuelLogsByDateRange(companyId, startMs, endMs) }
            val servicesDeferred = async { serviceRepository.getServicesByDateRange(companyId, startMs, endMs) }
            val repairsDeferred = async { repairRepository.getRepairsByDateRange(companyId, startMs, endMs) }

            // Await results
            val trips = extractList(tripsDeferred.await())
            val fuelCost = extractDouble(fuelCostDeferred.await())
            val serviceCost = extractDouble(serviceCostDeferred.await())
            val repairCost = extractDouble(repairCostDeferred.await())
            val fuelLogs = extractList(fuelLogsDeferred.await())
            val services = extractList(servicesDeferred.await())
            val repairs = extractList(repairsDeferred.await())

            // Revenue = sum of completed trip distances × rate
            val completedTrips = trips.filter { it.status == TripStatus.COMPLETED }
            val totalDistanceKm = completedTrips.sumOf { it.distance }
            val revenue = totalDistanceKm * REVENUE_PER_KM

            // Total expenses
            val totalExpenses = fuelCost + serviceCost + repairCost

            // Profit
            val profit = revenue - totalExpenses

            // Trip statistics
            val totalTrips = trips.size
            val completedCount = completedTrips.size
            val cancelledCount = trips.count { it.status == TripStatus.CANCELLED }
            val activeCount = trips.count {
                it.status == TripStatus.STARTED || it.status == TripStatus.ASSIGNED
            }

            // Average trip distance
            val avgTripDistance = if (completedTrips.isNotEmpty())
                totalDistanceKm / completedTrips.size else 0.0

            // Average trip duration (minutes)
            val totalDurationMinutes = completedTrips.sumOf { it.duration }
            val avgTripDuration = if (completedTrips.isNotEmpty())
                totalDurationMinutes / completedTrips.size else 0L

            // Fuel metrics
            val totalFuelLitres = fuelLogs.sumOf { it.quantity }
            val avgMileage = if (totalFuelLitres > 0)
                totalDistanceKm / totalFuelLitres else 0.0

            FinancialSnapshot(
                revenue = revenue,
                fuelCost = fuelCost,
                serviceCost = serviceCost,
                repairCost = repairCost,
                totalExpenses = totalExpenses,
                profit = profit,
                profitMargin = if (revenue > 0) profit / revenue else 0.0,
                totalTrips = totalTrips,
                completedTrips = completedCount,
                cancelledTrips = cancelledCount,
                activeTrips = activeCount,
                totalDistanceKm = totalDistanceKm,
                avgTripDistanceKm = avgTripDistance,
                avgTripDurationMinutes = avgTripDuration,
                totalFuelLitres = totalFuelLitres,
                avgMileageKmPerL = avgMileage,
                totalFuelLogs = fuelLogs.size,
                totalServiceRecords = services.size,
                totalRepairRecords = repairs.size,
                startMs = startMs,
                endMs = endMs
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  2. COST BREAKDOWN
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Build a cost breakdown for pie/donut chart rendering.
     * Categories: Fuel, Services, Repairs.
     */
    suspend fun computeCostBreakdown(
        companyId: String,
        startMs: Long,
        endMs: Long
    ): CostBreakdown = withContext(Dispatchers.IO) {
        coroutineScope {
            val fuelD = async { fuelLogRepository.getTotalFuelCost(companyId, startMs, endMs) }
            val serviceD = async { serviceRepository.getTotalServiceCost(companyId, startMs, endMs) }
            val repairD = async { repairRepository.getTotalRepairCost(companyId, startMs, endMs) }

            val fuel = extractDouble(fuelD.await())
            val service = extractDouble(serviceD.await())
            val repair = extractDouble(repairD.await())
            val total = fuel + service + repair

            CostBreakdown(
                fuelCost = fuel,
                serviceCost = service,
                repairCost = repair,
                totalCost = total,
                fuelPercent = if (total > 0) fuel / total else 0.0,
                servicePercent = if (total > 0) service / total else 0.0,
                repairPercent = if (total > 0) repair / total else 0.0,
                segments = listOf(
                    CostSegment("Fuel", fuel, if (total > 0) fuel / total else 0.0, CostCategory.FUEL),
                    CostSegment("Services", service, if (total > 0) service / total else 0.0, CostCategory.SERVICE),
                    CostSegment("Repairs", repair, if (total > 0) repair / total else 0.0, CostCategory.REPAIR)
                )
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  3. MONTHLY TREND DATA (for line/bar charts)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Generate month-by-month revenue & expense trend for the past N months.
     *
     * Returns a list of [MonthlyDataPoint] ordered chronologically.
     * Each point contains revenue, fuel cost, service cost, repair cost, and profit.
     *
     * @param months Number of months to look back (default 6).
     */
    suspend fun computeMonthlyTrend(
        companyId: String,
        months: Int = 6
    ): List<MonthlyDataPoint> = withContext(Dispatchers.IO) {
        val effectiveMonths = months.coerceIn(1, MAX_TREND_MONTHS)
        val result = mutableListOf<MonthlyDataPoint>()
        val cal = Calendar.getInstance()

        // Snap to start of next month, then walk backwards
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, 1)
        val overallEnd = cal.timeInMillis

        cal.add(Calendar.MONTH, -effectiveMonths)
        val overallStart = cal.timeInMillis

        // Fetch all data for the full range in one shot — much faster than per-month queries
        coroutineScope {
            val tripsD = async { tripRepository.getTripsByDateRange(companyId, overallStart, overallEnd) }
            val fuelD = async { fuelLogRepository.getFuelLogsByDateRange(companyId, overallStart, overallEnd) }
            val serviceD = async { serviceRepository.getServicesByDateRange(companyId, overallStart, overallEnd) }
            val repairD = async { repairRepository.getRepairsByDateRange(companyId, overallStart, overallEnd) }

            val allTrips = extractList(tripsD.await())
            val allFuel = extractList(fuelD.await())
            val allServices = extractList(serviceD.await())
            val allRepairs = extractList(repairD.await())

            // Bucket by month
            val calendar = Calendar.getInstance()
            for (m in 0 until effectiveMonths) {
                calendar.timeInMillis = overallStart
                calendar.add(Calendar.MONTH, m)
                val monthStart = calendar.timeInMillis
                calendar.add(Calendar.MONTH, 1)
                val monthEnd = calendar.timeInMillis

                val monthLabel = String.format(
                    Locale.US, "%tb %tY", calendar.apply { timeInMillis = monthStart },
                    calendar.apply { timeInMillis = monthStart }
                )

                val monthTrips = allTrips.filter {
                    val ts = it.createdAt
                    ts in monthStart until monthEnd
                }
                val monthFuel = allFuel.filter {
                    it.timestamp in monthStart until monthEnd
                }
                val monthServices = allServices.filter {
                    it.date in monthStart until monthEnd
                }
                val monthRepairs = allRepairs.filter {
                    it.date in monthStart until monthEnd
                }

                val completed = monthTrips.filter { it.status == TripStatus.COMPLETED }
                val distanceKm = completed.sumOf { it.distance }
                val revenue = distanceKm * REVENUE_PER_KM

                val fuelCost = monthFuel.sumOf { it.cost }
                val serviceCost = monthServices.sumOf { it.cost }
                val repairCost = monthRepairs.sumOf { it.cost }
                val totalExpense = fuelCost + serviceCost + repairCost
                val profit = revenue - totalExpense

                result.add(
                    MonthlyDataPoint(
                        label = monthLabel,
                        monthStartMs = monthStart,
                        monthEndMs = monthEnd,
                        revenue = revenue,
                        fuelCost = fuelCost,
                        serviceCost = serviceCost,
                        repairCost = repairCost,
                        totalExpense = totalExpense,
                        profit = profit,
                        tripCount = monthTrips.size,
                        completedTrips = completed.size,
                        distanceKm = distanceKm,
                        fuelLitres = monthFuel.sumOf { it.quantity }
                    )
                )
            }
        }
        result
    }

    // ═════════════════════════════════════════════════════════════════════
    //  4. FUEL TREND CHART DATA
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Generate fuel cost trend data bucketed by month.
     */
    suspend fun computeFuelTrend(
        companyId: String,
        months: Int = 6
    ): List<FuelTrendPoint> = withContext(Dispatchers.IO) {
        val effectiveMonths = months.coerceIn(1, MAX_TREND_MONTHS)
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, 1)
        val overallEnd = cal.timeInMillis
        cal.add(Calendar.MONTH, -effectiveMonths)
        val overallStart = cal.timeInMillis

        val allFuel = extractList(
            fuelLogRepository.getFuelLogsByDateRange(companyId, overallStart, overallEnd)
        )

        val result = mutableListOf<FuelTrendPoint>()
        val calendar = Calendar.getInstance()
        for (m in 0 until effectiveMonths) {
            calendar.timeInMillis = overallStart
            calendar.add(Calendar.MONTH, m)
            val monthStart = calendar.timeInMillis
            calendar.add(Calendar.MONTH, 1)
            val monthEnd = calendar.timeInMillis

            val monthLabel = String.format(
                Locale.US, "%tb", calendar.apply { timeInMillis = monthStart }
            )

            val monthLogs = allFuel.filter { it.timestamp in monthStart until monthEnd }
            val totalCost = monthLogs.sumOf { it.cost }
            val totalLitres = monthLogs.sumOf { it.quantity }
            val avgRate = if (totalLitres > 0) totalCost / totalLitres else 0.0
            val logCount = monthLogs.size

            result.add(
                FuelTrendPoint(
                    label = monthLabel,
                    monthStartMs = monthStart,
                    totalCost = totalCost,
                    totalLitres = totalLitres,
                    averageRate = avgRate,
                    logCount = logCount
                )
            )
        }
        result
    }

    // ═════════════════════════════════════════════════════════════════════
    //  5. VEHICLE UTILIZATION
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Calculate utilization for each vehicle in the fleet.
     *
     * Utilization = (total completed trip duration) / (available time in the period).
     * Available time = number of days in range × [WORKING_HOURS_PER_DAY] hours.
     *
     * @return List of [VehicleUtilization] sorted by utilization descending.
     */
    suspend fun computeVehicleUtilization(
        companyId: String,
        startMs: Long,
        endMs: Long
    ): List<VehicleUtilization> = withContext(Dispatchers.IO) {
        coroutineScope {
            val vehiclesD = async { vehicleRepository.getVehiclesByCompany(companyId) }
            val tripsD = async { tripRepository.getTripsByDateRange(companyId, startMs, endMs) }

            val vehicles = extractList(vehiclesD.await())
            val trips = extractList(tripsD.await())

            // Total available hours in the range per vehicle
            val rangeDays = ((endMs - startMs).toDouble() / MILLIS_PER_DAY).coerceAtLeast(1.0)
            val availableMinutes = rangeDays * WORKING_HOURS_PER_DAY * 60

            vehicles.map { vehicle ->
                val vehicleTrips = trips.filter {
                    it.vehicleId == vehicle.vehicleId && it.status == TripStatus.COMPLETED
                }
                val activeMinutes = vehicleTrips.sumOf { it.duration }  // stored in minutes
                val utilization = if (availableMinutes > 0)
                    (activeMinutes.toDouble() / availableMinutes).coerceIn(0.0, 1.0)
                else 0.0
                val totalDistance = vehicleTrips.sumOf { it.distance }
                val tripCount = vehicleTrips.size

                VehicleUtilization(
                    vehicleId = vehicle.vehicleId,
                    vehicleNumber = vehicle.number,
                    vehicleLabel = vehicle.displayLabel,
                    status = vehicle.status,
                    tripCount = tripCount,
                    activeMinutes = activeMinutes,
                    availableMinutes = availableMinutes.toLong(),
                    utilization = utilization,
                    totalDistanceKm = totalDistance
                )
            }.sortedByDescending { it.utilization }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  6. DRIVER PERFORMANCE RANKING
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Get driver performance rankings from the pre-computed driver_summary collection.
     * Enriches each summary with the driver's name by cross-referencing the drivers collection.
     *
     * @return List of [DriverRanking] sorted by score descending.
     */
    suspend fun computeDriverRankings(
        companyId: String
    ): List<DriverRanking> = withContext(Dispatchers.IO) {
        coroutineScope {
            val summariesD = async { performanceRepository.getSummariesByCompany(companyId) }
            val driversD = async { driverRepository.getDriversByCompany(companyId) }

            val summaries = extractList(summariesD.await())
            val drivers = extractList(driversD.await())

            // Build lookup: driverId → driver name
            val driverNameMap = drivers.associate {
                it.driverId to (it.licenseNumber.ifBlank { it.driverId.take(8) })
            }

            summaries.mapIndexed { index, summary ->
                DriverRanking(
                    rank = index + 1,
                    driverId = summary.driverId,
                    driverName = driverNameMap[summary.driverId] ?: summary.driverId.take(8),
                    score = summary.score,
                    grade = summary.grade,
                    completedTrips = summary.completedTrips,
                    totalDistanceKm = summary.totalDistance,
                    violationsCount = summary.violationsCount,
                    avgMileage = summary.averageMileage,
                    trend = summary.trend
                )
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  7. FLEET KPI SNAPSHOT
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Quick fleet-level KPIs: vehicle counts by status, driver counts, etc.
     */
    suspend fun computeFleetKPIs(
        companyId: String
    ): FleetKPIs = withContext(Dispatchers.IO) {
        coroutineScope {
            val vehiclesD = async { vehicleRepository.getVehiclesByCompany(companyId) }
            val driversD = async { driverRepository.getDriversByCompany(companyId) }
            val activeTripsD = async { tripRepository.getActiveTripCount(companyId) }

            val vehicles = extractList(vehiclesD.await())
            val drivers = extractList(driversD.await())
            val activeTripCount = when (val r = activeTripsD.await()) {
                is ResultState.Success -> r.data
                else -> 0
            }

            val totalVehicles = vehicles.size
            val availableVehicles = vehicles.count { it.status == VehicleStatus.AVAILABLE }
            val onTripVehicles = vehicles.count { it.status == VehicleStatus.ON_TRIP }
            val inServiceVehicles = vehicles.count { it.status == VehicleStatus.SERVICE }
            val inactiveVehicles = vehicles.count { it.status == VehicleStatus.INACTIVE }

            val totalDrivers = drivers.size
            val activeDrivers = drivers.count { !it.blocked }
            val blockedDrivers = drivers.count { it.blocked }

            FleetKPIs(
                totalVehicles = totalVehicles,
                availableVehicles = availableVehicles,
                onTripVehicles = onTripVehicles,
                inServiceVehicles = inServiceVehicles,
                inactiveVehicles = inactiveVehicles,
                totalDrivers = totalDrivers,
                activeDrivers = activeDrivers,
                blockedDrivers = blockedDrivers,
                activeTrips = activeTripCount,
                fleetUtilization = if (totalVehicles > 0)
                    onTripVehicles.toDouble() / totalVehicles else 0.0
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  8. PER-VEHICLE ANALYTICS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Compute detailed analytics for a single vehicle.
     */
    suspend fun computeVehicleAnalytics(
        vehicleId: String,
        companyId: String,
        startMs: Long,
        endMs: Long
    ): VehicleAnalytics = withContext(Dispatchers.IO) {
        coroutineScope {
            val vehicleD = async { vehicleRepository.getVehicleById(vehicleId) }
            val tripsD = async { tripRepository.getTripsByVehicle(vehicleId) }
            val fuelD = async { fuelLogRepository.getFuelLogsByVehicle(vehicleId) }
            val servicesD = async { serviceRepository.getServicesByVehicle(vehicleId) }
            val repairsD = async { repairRepository.getRepairsByVehicle(vehicleId) }

            val vehicle = when (val r = vehicleD.await()) {
                is ResultState.Success -> r.data
                else -> null
            }

            val trips = extractList(tripsD.await())
                .filter { it.createdAt in startMs..endMs }
            val fuelLogs = extractList(fuelD.await())
                .filter { it.timestamp in startMs..endMs }
            val services = extractList(servicesD.await())
                .filter { it.date in startMs..endMs }
            val repairs = extractList(repairsD.await())
                .filter { it.date in startMs..endMs }

            val completedTrips = trips.filter { it.status == TripStatus.COMPLETED }
            val totalDistance = completedTrips.sumOf { it.distance }
            val totalDuration = completedTrips.sumOf { it.duration }
            val fuelCost = fuelLogs.sumOf { it.cost }
            val serviceCost = services.sumOf { it.cost }
            val repairCost = repairs.sumOf { it.cost }
            val totalFuel = fuelLogs.sumOf { it.quantity }
            val mileage = if (totalFuel > 0) totalDistance / totalFuel else 0.0

            VehicleAnalytics(
                vehicleId = vehicleId,
                vehicleLabel = vehicle?.displayLabel ?: vehicleId.take(8),
                vehicleNumber = vehicle?.number ?: "",
                status = vehicle?.status ?: VehicleStatus.INACTIVE,
                tripCount = completedTrips.size,
                totalDistanceKm = totalDistance,
                totalDurationMinutes = totalDuration,
                fuelCost = fuelCost,
                serviceCost = serviceCost,
                repairCost = repairCost,
                totalCost = fuelCost + serviceCost + repairCost,
                totalFuelLitres = totalFuel,
                avgMileage = mileage,
                costPerKm = if (totalDistance > 0) (fuelCost + serviceCost + repairCost) / totalDistance else 0.0
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  9. TOP VEHICLE COSTS (for reports)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Rank vehicles by total cost within a date range.
     */
    suspend fun computeVehicleCostRanking(
        companyId: String,
        startMs: Long,
        endMs: Long
    ): List<VehicleCostEntry> = withContext(Dispatchers.IO) {
        coroutineScope {
            val vehiclesD = async { vehicleRepository.getVehiclesByCompany(companyId) }
            val fuelD = async { fuelLogRepository.getFuelLogsByDateRange(companyId, startMs, endMs) }
            val serviceD = async { serviceRepository.getServicesByDateRange(companyId, startMs, endMs) }
            val repairD = async { repairRepository.getRepairsByDateRange(companyId, startMs, endMs) }

            val vehicles = extractList(vehiclesD.await())
            val fuels = extractList(fuelD.await())
            val services = extractList(serviceD.await())
            val repairs = extractList(repairD.await())

            vehicles.map { v ->
                val fCost = fuels.filter { it.vehicleId == v.vehicleId }.sumOf { it.cost }
                val sCost = services.filter { it.vehicleId == v.vehicleId }.sumOf { it.cost }
                val rCost = repairs.filter { it.vehicleId == v.vehicleId }.sumOf { it.cost }
                VehicleCostEntry(
                    vehicleId = v.vehicleId,
                    vehicleLabel = v.displayLabel,
                    vehicleNumber = v.number,
                    fuelCost = fCost,
                    serviceCost = sCost,
                    repairCost = rCost,
                    totalCost = fCost + sCost + rCost
                )
            }.sortedByDescending { it.totalCost }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  10. FULL ANALYTICS REPORT (combines everything)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Generate a comprehensive analytics report for the AdminReportsFragment.
     * Combines financial snapshot, cost breakdown, monthly trend, vehicle utilization,
     * driver rankings, and fleet KPIs.
     */
    suspend fun generateFullReport(
        companyId: String,
        startMs: Long,
        endMs: Long,
        trendMonths: Int = 6
    ): FullAnalyticsReport = withContext(Dispatchers.IO) {
        coroutineScope {
            val snapshotD = async { computeFinancialSnapshot(companyId, startMs, endMs) }
            val costD = async { computeCostBreakdown(companyId, startMs, endMs) }
            val trendD = async { computeMonthlyTrend(companyId, trendMonths) }
            val fuelTrendD = async { computeFuelTrend(companyId, trendMonths) }
            val utilizationD = async { computeVehicleUtilization(companyId, startMs, endMs) }
            val rankingsD = async { computeDriverRankings(companyId) }
            val kpisD = async { computeFleetKPIs(companyId) }
            val costRankD = async { computeVehicleCostRanking(companyId, startMs, endMs) }

            FullAnalyticsReport(
                snapshot = snapshotD.await(),
                costBreakdown = costD.await(),
                monthlyTrend = trendD.await(),
                fuelTrend = fuelTrendD.await(),
                vehicleUtilization = utilizationD.await(),
                driverRankings = rankingsD.await(),
                fleetKPIs = kpisD.await(),
                vehicleCosts = costRankD.await(),
                generatedAt = System.currentTimeMillis()
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  11. DATE RANGE HELPERS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Pre-baked date range presets.
     */
    enum class DatePreset(val label: String) {
        THIS_MONTH("This Month"),
        LAST_MONTH("Last Month"),
        LAST_3_MONTHS("Last 3 Months"),
        LAST_6_MONTHS("Last 6 Months"),
        THIS_YEAR("This Year"),
        LAST_YEAR("Last Year"),
        ALL_TIME("All Time")
    }

    /**
     * Resolve a [DatePreset] to a start/end pair (epoch millis).
     */
    fun resolveDateRange(preset: DatePreset): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        return when (preset) {
            DatePreset.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            DatePreset.LAST_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val end = cal.timeInMillis
                cal.add(Calendar.MONTH, -1)
                cal.timeInMillis to end
            }
            DatePreset.LAST_3_MONTHS -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.MONTH, -3)
                cal.timeInMillis to now
            }
            DatePreset.LAST_6_MONTHS -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.MONTH, -6)
                cal.timeInMillis to now
            }
            DatePreset.THIS_YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            DatePreset.LAST_YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val end = cal.timeInMillis
                cal.add(Calendar.YEAR, -1)
                cal.timeInMillis to end
            }
            DatePreset.ALL_TIME -> {
                // 2020-01-01 as a reasonable lower bound
                cal.set(2020, Calendar.JANUARY, 1, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private fun <T> extractList(result: ResultState<List<T>>): List<T> = when (result) {
        is ResultState.Success -> result.data
        else -> emptyList()
    }

    private fun extractDouble(result: ResultState<Double>): Double = when (result) {
        is ResultState.Success -> result.data
        else -> 0.0
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═════════════════════════════════════════════════════════════════════

    data class FinancialSnapshot(
        val revenue: Double,
        val fuelCost: Double,
        val serviceCost: Double,
        val repairCost: Double,
        val totalExpenses: Double,
        val profit: Double,
        val profitMargin: Double,
        val totalTrips: Int,
        val completedTrips: Int,
        val cancelledTrips: Int,
        val activeTrips: Int,
        val totalDistanceKm: Double,
        val avgTripDistanceKm: Double,
        val avgTripDurationMinutes: Long,
        val totalFuelLitres: Double,
        val avgMileageKmPerL: Double,
        val totalFuelLogs: Int,
        val totalServiceRecords: Int,
        val totalRepairRecords: Int,
        val startMs: Long,
        val endMs: Long
    ) {
        val revenueDisplay: String get() = formatCurrency(revenue)
        val fuelCostDisplay: String get() = formatCurrency(fuelCost)
        val serviceCostDisplay: String get() = formatCurrency(serviceCost)
        val repairCostDisplay: String get() = formatCurrency(repairCost)
        val totalExpensesDisplay: String get() = formatCurrency(totalExpenses)
        val profitDisplay: String get() = formatCurrency(profit)
        val profitMarginDisplay: String get() = formatPercent(profitMargin)
        val distanceDisplay: String get() = formatKm(totalDistanceKm)
        val isProfitable: Boolean get() = profit >= 0
    }

    enum class CostCategory { FUEL, SERVICE, REPAIR }

    data class CostSegment(
        val label: String,
        val amount: Double,
        val percent: Double,
        val category: CostCategory
    ) {
        val amountDisplay: String get() = formatCurrency(amount)
        val percentDisplay: String get() = formatPercent(percent)
    }

    data class CostBreakdown(
        val fuelCost: Double,
        val serviceCost: Double,
        val repairCost: Double,
        val totalCost: Double,
        val fuelPercent: Double,
        val servicePercent: Double,
        val repairPercent: Double,
        val segments: List<CostSegment>
    ) {
        val totalDisplay: String get() = formatCurrency(totalCost)
        val hasData: Boolean get() = totalCost > 0
    }

    data class MonthlyDataPoint(
        val label: String,
        val monthStartMs: Long,
        val monthEndMs: Long,
        val revenue: Double,
        val fuelCost: Double,
        val serviceCost: Double,
        val repairCost: Double,
        val totalExpense: Double,
        val profit: Double,
        val tripCount: Int,
        val completedTrips: Int,
        val distanceKm: Double,
        val fuelLitres: Double
    )

    data class FuelTrendPoint(
        val label: String,
        val monthStartMs: Long,
        val totalCost: Double,
        val totalLitres: Double,
        val averageRate: Double,
        val logCount: Int
    ) {
        val costDisplay: String get() = formatCurrency(totalCost)
    }

    data class VehicleUtilization(
        val vehicleId: String,
        val vehicleNumber: String,
        val vehicleLabel: String,
        val status: VehicleStatus,
        val tripCount: Int,
        val activeMinutes: Long,
        val availableMinutes: Long,
        val utilization: Double,
        val totalDistanceKm: Double
    ) {
        val utilizationDisplay: String get() = formatPercent(utilization)
        val distanceDisplay: String get() = formatKm(totalDistanceKm)
    }

    data class DriverRanking(
        val rank: Int,
        val driverId: String,
        val driverName: String,
        val score: Int,
        val grade: String,
        val completedTrips: Int,
        val totalDistanceKm: Double,
        val violationsCount: Int,
        val avgMileage: Double,
        val trend: DriverSummary.ScoreTrend
    ) {
        val scoreProgress: Float get() = score / 100f
        val distanceDisplay: String get() = formatKm(totalDistanceKm)
        val mileageDisplay: String get() = String.format(Locale.US, "%.1f km/L", avgMileage)
    }

    data class FleetKPIs(
        val totalVehicles: Int,
        val availableVehicles: Int,
        val onTripVehicles: Int,
        val inServiceVehicles: Int,
        val inactiveVehicles: Int,
        val totalDrivers: Int,
        val activeDrivers: Int,
        val blockedDrivers: Int,
        val activeTrips: Int,
        val fleetUtilization: Double
    ) {
        val utilizationDisplay: String get() = formatPercent(fleetUtilization)
    }

    data class VehicleAnalytics(
        val vehicleId: String,
        val vehicleLabel: String,
        val vehicleNumber: String,
        val status: VehicleStatus,
        val tripCount: Int,
        val totalDistanceKm: Double,
        val totalDurationMinutes: Long,
        val fuelCost: Double,
        val serviceCost: Double,
        val repairCost: Double,
        val totalCost: Double,
        val totalFuelLitres: Double,
        val avgMileage: Double,
        val costPerKm: Double
    ) {
        val totalCostDisplay: String get() = formatCurrency(totalCost)
        val distanceDisplay: String get() = formatKm(totalDistanceKm)
        val costPerKmDisplay: String get() = String.format(Locale.US, "₹%.1f/km", costPerKm)
    }

    data class VehicleCostEntry(
        val vehicleId: String,
        val vehicleLabel: String,
        val vehicleNumber: String,
        val fuelCost: Double,
        val serviceCost: Double,
        val repairCost: Double,
        val totalCost: Double
    ) {
        val totalCostDisplay: String get() = formatCurrency(totalCost)
        val fuelCostDisplay: String get() = formatCurrency(fuelCost)
    }

    data class FullAnalyticsReport(
        val snapshot: FinancialSnapshot,
        val costBreakdown: CostBreakdown,
        val monthlyTrend: List<MonthlyDataPoint>,
        val fuelTrend: List<FuelTrendPoint>,
        val vehicleUtilization: List<VehicleUtilization>,
        val driverRankings: List<DriverRanking>,
        val fleetKPIs: FleetKPIs,
        val vehicleCosts: List<VehicleCostEntry>,
        val generatedAt: Long
    )
}
