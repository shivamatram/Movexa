package com.example.movexa.data.model

/**
 * Dashboard summary data model for admin overview cards.
 *
 * Firestore document: dashboard_summary/{companyId}
 *
 * Fields are aggregated server-side (Cloud Functions or batch jobs)
 * and consumed in real-time by the admin dashboard.
 */
data class DashboardSummary(
    val companyId: String = "",
    val totalVehicles: Int = 0,
    val activeVehicles: Int = 0,
    val inactiveVehicles: Int = 0,
    val inMaintenanceVehicles: Int = 0,
    val totalDrivers: Int = 0,
    val availableDrivers: Int = 0,
    val onTripDrivers: Int = 0,
    val offDutyDrivers: Int = 0,
    val activeTrips: Int = 0,
    val completedTripsToday: Int = 0,
    val pendingTrips: Int = 0,
    val cancelledTripsToday: Int = 0,
    val todayRevenue: Double = 0.0,
    val weekRevenue: Double = 0.0,
    val monthRevenue: Double = 0.0,
    val activeAlerts: Int = 0,
    val criticalAlerts: Int = 0,
    val pendingServiceCount: Int = 0,
    val overdueServiceCount: Int = 0,
    val fuelCostToday: Double = 0.0,
    val fuelCostMonth: Double = 0.0,
    val averageTripDistance: Double = 0.0,
    val fleetUtilizationPercent: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
) {

    /**
     * Percentage of fleet currently active.
     */
    val fleetActivePercent: Double
        get() = if (totalVehicles > 0) (activeVehicles.toDouble() / totalVehicles) * 100 else 0.0

    /**
     * Percentage of drivers currently available.
     */
    val driverAvailabilityPercent: Double
        get() = if (totalDrivers > 0) (availableDrivers.toDouble() / totalDrivers) * 100 else 0.0

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "companyId" to companyId,
            "totalVehicles" to totalVehicles,
            "activeVehicles" to activeVehicles,
            "inactiveVehicles" to inactiveVehicles,
            "inMaintenanceVehicles" to inMaintenanceVehicles,
            "totalDrivers" to totalDrivers,
            "availableDrivers" to availableDrivers,
            "onTripDrivers" to onTripDrivers,
            "offDutyDrivers" to offDutyDrivers,
            "activeTrips" to activeTrips,
            "completedTripsToday" to completedTripsToday,
            "pendingTrips" to pendingTrips,
            "cancelledTripsToday" to cancelledTripsToday,
            "todayRevenue" to todayRevenue,
            "weekRevenue" to weekRevenue,
            "monthRevenue" to monthRevenue,
            "activeAlerts" to activeAlerts,
            "criticalAlerts" to criticalAlerts,
            "pendingServiceCount" to pendingServiceCount,
            "overdueServiceCount" to overdueServiceCount,
            "fuelCostToday" to fuelCostToday,
            "fuelCostMonth" to fuelCostMonth,
            "averageTripDistance" to averageTripDistance,
            "fleetUtilizationPercent" to fleetUtilizationPercent,
            "lastUpdated" to lastUpdated
        )
    }

    companion object {
        const val COLLECTION_NAME = "dashboard_summary"

        fun fromMap(map: Map<String, Any?>): DashboardSummary {
            return DashboardSummary(
                companyId = map["companyId"] as? String ?: "",
                totalVehicles = (map["totalVehicles"] as? Number)?.toInt() ?: 0,
                activeVehicles = (map["activeVehicles"] as? Number)?.toInt() ?: 0,
                inactiveVehicles = (map["inactiveVehicles"] as? Number)?.toInt() ?: 0,
                inMaintenanceVehicles = (map["inMaintenanceVehicles"] as? Number)?.toInt() ?: 0,
                totalDrivers = (map["totalDrivers"] as? Number)?.toInt() ?: 0,
                availableDrivers = (map["availableDrivers"] as? Number)?.toInt() ?: 0,
                onTripDrivers = (map["onTripDrivers"] as? Number)?.toInt() ?: 0,
                offDutyDrivers = (map["offDutyDrivers"] as? Number)?.toInt() ?: 0,
                activeTrips = (map["activeTrips"] as? Number)?.toInt() ?: 0,
                completedTripsToday = (map["completedTripsToday"] as? Number)?.toInt() ?: 0,
                pendingTrips = (map["pendingTrips"] as? Number)?.toInt() ?: 0,
                cancelledTripsToday = (map["cancelledTripsToday"] as? Number)?.toInt() ?: 0,
                todayRevenue = (map["todayRevenue"] as? Number)?.toDouble() ?: 0.0,
                weekRevenue = (map["weekRevenue"] as? Number)?.toDouble() ?: 0.0,
                monthRevenue = (map["monthRevenue"] as? Number)?.toDouble() ?: 0.0,
                activeAlerts = (map["activeAlerts"] as? Number)?.toInt() ?: 0,
                criticalAlerts = (map["criticalAlerts"] as? Number)?.toInt() ?: 0,
                pendingServiceCount = (map["pendingServiceCount"] as? Number)?.toInt() ?: 0,
                overdueServiceCount = (map["overdueServiceCount"] as? Number)?.toInt() ?: 0,
                fuelCostToday = (map["fuelCostToday"] as? Number)?.toDouble() ?: 0.0,
                fuelCostMonth = (map["fuelCostMonth"] as? Number)?.toDouble() ?: 0.0,
                averageTripDistance = (map["averageTripDistance"] as? Number)?.toDouble() ?: 0.0,
                fleetUtilizationPercent = (map["fleetUtilizationPercent"] as? Number)?.toDouble() ?: 0.0,
                lastUpdated = (map["lastUpdated"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
