package com.example.movexa.data.repository.contracts

import com.example.movexa.data.model.PublicDriverInfo
import com.example.movexa.data.model.PublicLiveLocation
import com.example.movexa.data.model.PublicTripInfo
import com.example.movexa.data.model.PublicVehicleInfo
import com.example.movexa.data.model.ResultState
import kotlinx.coroutines.flow.Flow

/**
 * ═══════════════════════════════════════════════════════════════════
 *  PUBLIC TRACKING REPOSITORY — CONTRACT
 * ═══════════════════════════════════════════════════════════════════
 *
 * Read-only repository for **unauthenticated** public customers.
 *
 * Security contract:
 *  ● NO writes — customers can never modify data
 *  ● NO access to companyId, internal IDs, or metadata
 *  ● Phone numbers are ALWAYS masked before returning
 *  ● Only trip status, location, ETA, and minimal driver/vehicle
 *    info are exposed
 *
 * Data sources:
 *  ● Firestore `trips` collection — queried by `trackingId` field
 *  ● Firestore `vehicles` / `drivers` / `users` — point reads
 *  ● Realtime Database `tracking_locations/{companyId}/{vehicleId}`
 *    — live observation
 *
 * @since 2026-02-22
 */
interface PublicTrackingRepository {

    // ═══════════════════════════════════════════════════════════
    //  TRIP LOOKUP
    // ═══════════════════════════════════════════════════════════

    /**
     * Look up a trip by its customer-facing tracking ID.
     *
     * Returns [PublicTripInfo] if exactly one matching trip is found,
     * `null` if no trip matches.
     *
     * The implementation queries Firestore where `trackingId == id`.
     */
    suspend fun findTripByTrackingId(trackingId: String): ResultState<PublicTripInfo?>

    /**
     * Observe a trip document in real-time for status changes.
     * The flow emits a new [PublicTripInfo] whenever the Firestore
     * document is updated (e.g. status transitions).
     */
    fun observeTripByTrackingId(trackingId: String): Flow<ResultState<PublicTripInfo?>>

    // ═══════════════════════════════════════════════════════════
    //  LIVE LOCATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Observe the live GPS location of the vehicle.
     *
     * Requires [companyId] and [vehicleId] (obtained from
     * [PublicTripInfo.internalCompanyId] / [internalVehicleId]).
     * These internal IDs are kept opaque to the customer.
     *
     * Emits sanitised [PublicLiveLocation] updates.
     */
    fun observeVehicleLocation(
        companyId: String,
        vehicleId: String
    ): Flow<ResultState<PublicLiveLocation?>>

    // ═══════════════════════════════════════════════════════════
    //  DRIVER & VEHICLE INFO
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch sanitised driver info — name masked, phone masked.
     */
    suspend fun getDriverInfo(driverId: String): ResultState<PublicDriverInfo?>

    /**
     * Fetch minimal vehicle info — only number + type.
     */
    suspend fun getVehicleInfo(vehicleId: String): ResultState<PublicVehicleInfo?>
}
