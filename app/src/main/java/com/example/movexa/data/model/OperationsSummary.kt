package com.example.movexa.data.model

/**
 * Operations summary data model for manager dashboard.
 *
 * Firestore document: operations_summary/{companyId}
 *
 * Contains operational metrics relevant to fleet managers:
 * team performance, pending approvals, and real-time fleet status.
 */
data class OperationsSummary(
    val companyId: String = "",
    val managerId: String = "",
    val assignedVehicles: Int = 0,
    val activeVehicles: Int = 0,
    val idleVehicles: Int = 0,
    val inMaintenanceVehicles: Int = 0,
    val assignedDrivers: Int = 0,
    val availableDrivers: Int = 0,
    val onTripDrivers: Int = 0,
    val activeTrips: Int = 0,
    val pendingTrips: Int = 0,
    val completedTripsToday: Int = 0,
    val delayedTrips: Int = 0,
    val pendingApprovals: Int = 0,
    val pendingFuelApprovals: Int = 0,
    val pendingLeaveRequests: Int = 0,
    val pendingMaintenanceRequests: Int = 0,
    val openAlerts: Int = 0,
    val criticalAlerts: Int = 0,
    val todayDistance: Double = 0.0,
    val todayFuelCost: Double = 0.0,
    val teamEfficiencyPercent: Double = 0.0,
    val onTimeDeliveryPercent: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
) {

    /**
     * Total pending items requiring manager action.
     */
    val totalPendingActions: Int
        get() = pendingApprovals + pendingFuelApprovals +
                pendingLeaveRequests + pendingMaintenanceRequests

    /**
     * Fleet utilization rate for assigned vehicles.
     */
    val fleetUtilizationPercent: Double
        get() = if (assignedVehicles > 0) (activeVehicles.toDouble() / assignedVehicles) * 100 else 0.0

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "companyId" to companyId,
            "managerId" to managerId,
            "assignedVehicles" to assignedVehicles,
            "activeVehicles" to activeVehicles,
            "idleVehicles" to idleVehicles,
            "inMaintenanceVehicles" to inMaintenanceVehicles,
            "assignedDrivers" to assignedDrivers,
            "availableDrivers" to availableDrivers,
            "onTripDrivers" to onTripDrivers,
            "activeTrips" to activeTrips,
            "pendingTrips" to pendingTrips,
            "completedTripsToday" to completedTripsToday,
            "delayedTrips" to delayedTrips,
            "pendingApprovals" to pendingApprovals,
            "pendingFuelApprovals" to pendingFuelApprovals,
            "pendingLeaveRequests" to pendingLeaveRequests,
            "pendingMaintenanceRequests" to pendingMaintenanceRequests,
            "openAlerts" to openAlerts,
            "criticalAlerts" to criticalAlerts,
            "todayDistance" to todayDistance,
            "todayFuelCost" to todayFuelCost,
            "teamEfficiencyPercent" to teamEfficiencyPercent,
            "onTimeDeliveryPercent" to onTimeDeliveryPercent,
            "lastUpdated" to lastUpdated
        )
    }

    companion object {
        const val COLLECTION_NAME = "operations_summary"

        fun fromMap(map: Map<String, Any?>): OperationsSummary {
            return OperationsSummary(
                companyId = map["companyId"] as? String ?: "",
                managerId = map["managerId"] as? String ?: "",
                assignedVehicles = (map["assignedVehicles"] as? Number)?.toInt() ?: 0,
                activeVehicles = (map["activeVehicles"] as? Number)?.toInt() ?: 0,
                idleVehicles = (map["idleVehicles"] as? Number)?.toInt() ?: 0,
                inMaintenanceVehicles = (map["inMaintenanceVehicles"] as? Number)?.toInt() ?: 0,
                assignedDrivers = (map["assignedDrivers"] as? Number)?.toInt() ?: 0,
                availableDrivers = (map["availableDrivers"] as? Number)?.toInt() ?: 0,
                onTripDrivers = (map["onTripDrivers"] as? Number)?.toInt() ?: 0,
                activeTrips = (map["activeTrips"] as? Number)?.toInt() ?: 0,
                pendingTrips = (map["pendingTrips"] as? Number)?.toInt() ?: 0,
                completedTripsToday = (map["completedTripsToday"] as? Number)?.toInt() ?: 0,
                delayedTrips = (map["delayedTrips"] as? Number)?.toInt() ?: 0,
                pendingApprovals = (map["pendingApprovals"] as? Number)?.toInt() ?: 0,
                pendingFuelApprovals = (map["pendingFuelApprovals"] as? Number)?.toInt() ?: 0,
                pendingLeaveRequests = (map["pendingLeaveRequests"] as? Number)?.toInt() ?: 0,
                pendingMaintenanceRequests = (map["pendingMaintenanceRequests"] as? Number)?.toInt() ?: 0,
                openAlerts = (map["openAlerts"] as? Number)?.toInt() ?: 0,
                criticalAlerts = (map["criticalAlerts"] as? Number)?.toInt() ?: 0,
                todayDistance = (map["todayDistance"] as? Number)?.toDouble() ?: 0.0,
                todayFuelCost = (map["todayFuelCost"] as? Number)?.toDouble() ?: 0.0,
                teamEfficiencyPercent = (map["teamEfficiencyPercent"] as? Number)?.toDouble() ?: 0.0,
                onTimeDeliveryPercent = (map["onTimeDeliveryPercent"] as? Number)?.toDouble() ?: 0.0,
                lastUpdated = (map["lastUpdated"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
