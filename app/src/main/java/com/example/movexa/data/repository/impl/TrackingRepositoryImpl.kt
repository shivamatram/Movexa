package com.example.movexa.data.repository.impl

import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.data.repository.BaseRealtimeRepository
import com.example.movexa.data.repository.contracts.TrackingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Realtime Database implementation of [TrackingRepository].
 *
 * Manages live vehicle tracking at path: tracking_locations/{companyId}/{vehicleId}
 * Optimized for low-latency, high-frequency location updates.
 */
class TrackingRepositoryImpl : BaseRealtimeRepository<TrackingLocation>(), TrackingRepository {

    override val rootPath: String = TrackingLocation.PATH_TRACKING_LOCATIONS

    override fun fromMap(map: Map<String, Any?>): TrackingLocation =
        TrackingLocation.fromMap(map)

    override fun toMap(model: TrackingLocation): Map<String, Any?> = model.toMap()

    // ═══════════════════════════════════════════════════════════
    // WRITE
    // ═══════════════════════════════════════════════════════════

    override suspend fun updateLocation(
        companyId: String,
        vehicleId: String,
        location: TrackingLocation
    ): ResultState<Unit> = setValue("$companyId/$vehicleId", location)

    override suspend fun removeLocation(
        companyId: String,
        vehicleId: String
    ): ResultState<Unit> = remove("$companyId/$vehicleId")

    override suspend fun removeAllCompanyLocations(companyId: String): ResultState<Unit> =
        remove(companyId)

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════

    override suspend fun getLocation(
        companyId: String,
        vehicleId: String
    ): ResultState<TrackingLocation?> = getValue("$companyId/$vehicleId")

    override suspend fun getAllCompanyLocations(
        companyId: String
    ): ResultState<List<TrackingLocation>> = getChildrenAt(companyId)

    // ═══════════════════════════════════════════════════════════
    // REAL-TIME
    // ═══════════════════════════════════════════════════════════

    override fun observeVehicleLocation(
        companyId: String,
        vehicleId: String
    ): Flow<ResultState<TrackingLocation?>> = observeValue("$companyId/$vehicleId")

    override fun observeAllCompanyLocations(
        companyId: String
    ): Flow<ResultState<List<TrackingLocation>>> = observeChildrenAt(companyId)

    override fun observeMovingVehicles(
        companyId: String
    ): Flow<ResultState<List<TrackingLocation>>> =
        observeChildrenAt(companyId).map { result ->
            when (result) {
                is ResultState.Success -> {
                    ResultState.Success(result.data.filter { it.isMoving })
                }
                is ResultState.Error -> result
                is ResultState.Loading -> result
                is ResultState.Idle -> result
            }
        }
}
