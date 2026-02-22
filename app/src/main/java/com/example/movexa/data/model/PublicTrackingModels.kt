package com.example.movexa.data.model

import com.example.movexa.data.model.enums.TripEventType
import com.example.movexa.data.model.enums.TripStatus

/**
 * ═══════════════════════════════════════════════════════════════════
 *  PUBLIC CUSTOMER TRACKING — DATA MODELS
 * ═══════════════════════════════════════════════════════════════════
 *
 * Sanitised, security-scoped models exposed to unauthenticated
 * customers.  Only the absolute minimum data is surfaced:
 *
 *  ● Trip status, pickup/drop addresses, ETA
 *  ● Vehicle registration number (no internal IDs)
 *  ● Driver first-name + masked phone
 *  ● Ordered timeline events
 *  ● Live vehicle location (lat/lng only)
 *
 * No companyId, internal document IDs, or sensitive metadata
 * ever leave the repository layer.
 *
 * @since 2026-02-22
 */

// ═══════════════════════════════════════════════════════════════
//  PUBLIC TRIP INFO
// ═══════════════════════════════════════════════════════════════

/**
 * Sanitised snapshot of a Trip visible to a public customer.
 *
 * Fields intentionally omitted from the raw [Trip]:
 *  • companyId, tripId (internal)
 *  • assignedBy, notes, metadata
 *  • estimatedDistance / estimatedDuration (internal planning data)
 */
data class PublicTripInfo(
    /** The tracking ID the customer entered */
    val trackingId: String = "",

    /** Current trip lifecycle status */
    val status: TripStatus = TripStatus.CREATED,

    /** Human-readable addresses */
    val pickupAddress: String = "",
    val dropAddress: String = "",

    /** Geo-coordinates for map markers */
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val dropLat: Double = 0.0,
    val dropLng: Double = 0.0,

    /** Actual distance covered (km) — shown only after start */
    val distanceKm: Double = 0.0,

    /** Epoch timestamps — used for timeline building */
    val createdAt: Long = 0L,
    val startTime: Long = 0L,
    val endTime: Long = 0L,

    /** Internal references kept ONLY for repository lookups */
    internal val internalTripId: String = "",
    internal val internalCompanyId: String = "",
    internal val internalVehicleId: String = "",
    internal val internalDriverId: String = ""
) {
    // ─── Computed helpers ────────────────────────────────────
    val hasPickup: Boolean get() = pickupLat != 0.0 || pickupLng != 0.0
    val hasDrop: Boolean get() = dropLat != 0.0 || dropLng != 0.0
    val isActive: Boolean get() = !status.isTerminal && status != TripStatus.CREATED
    val isCompleted: Boolean get() = status == TripStatus.COMPLETED
    val isCancelled: Boolean get() = status == TripStatus.CANCELLED
    val isStarted: Boolean get() = status == TripStatus.STARTED
    val hasStarted: Boolean get() = startTime > 0L

    val statusDisplayName: String get() = when (status) {
        TripStatus.CREATED     -> "Order Placed"
        TripStatus.ASSIGNED    -> "Driver Assigned"
        TripStatus.ACCEPTED    -> "Driver Confirmed"
        TripStatus.REJECTED_BY_DRIVER -> "Reassigning Driver"
        TripStatus.STARTED     -> "In Transit"
        TripStatus.COMPLETED   -> "Delivered"
        TripStatus.CANCELLED   -> "Cancelled"
    }

    val distanceDisplay: String get() = when {
        distanceKm <= 0.0 -> "—"
        distanceKm < 1.0  -> "${(distanceKm * 1000).toInt()} m"
        else              -> "%.1f km".format(distanceKm)
    }

    /** Build a sorted timeline of events that have occurred so far. */
    fun buildTimeline(): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()

        // 1. Order placed
        if (createdAt > 0L) {
            events += TimelineEvent(
                type = TripEventType.CREATED,
                title = "Order Placed",
                description = "Your delivery request has been received",
                timestamp = createdAt,
                isCompleted = true
            )
        }

        // 2. Driver assigned
        if (status.ordinal >= TripStatus.ASSIGNED.ordinal && status != TripStatus.CANCELLED) {
            events += TimelineEvent(
                type = TripEventType.DRIVER_ASSIGNED,
                title = "Driver Assigned",
                description = "A driver has been assigned to your delivery",
                timestamp = createdAt + 1, // slightly after creation
                isCompleted = true
            )
        }

        // 3. Trip started
        if (status.ordinal >= TripStatus.STARTED.ordinal) {
            events += TimelineEvent(
                type = TripEventType.STARTED,
                title = "Pickup Completed",
                description = "Your package has been picked up and is on the way",
                timestamp = if (startTime > 0L) startTime else createdAt + 2,
                isCompleted = true
            )
        }

        // 4. Delivered
        if (status == TripStatus.COMPLETED) {
            events += TimelineEvent(
                type = TripEventType.COMPLETED,
                title = "Delivered",
                description = "Your package has been delivered successfully",
                timestamp = if (endTime > 0L) endTime else startTime + 1,
                isCompleted = true
            )
        }

        // 5. Cancelled
        if (status == TripStatus.CANCELLED) {
            events += TimelineEvent(
                type = TripEventType.CANCELLED,
                title = "Cancelled",
                description = "This delivery has been cancelled",
                timestamp = if (endTime > 0L) endTime else createdAt + 1,
                isCompleted = true
            )
        }

        // ── Future steps (not yet reached) ──────────────────
        if (!status.isTerminal) {
            val futureSteps = buildFutureSteps()
            events += futureSteps
        }

        return events
    }

    private fun buildFutureSteps(): List<TimelineEvent> {
        val future = mutableListOf<TimelineEvent>()

        if (status.ordinal < TripStatus.ASSIGNED.ordinal) {
            future += TimelineEvent(
                type = TripEventType.DRIVER_ASSIGNED,
                title = "Driver Assignment",
                description = "Waiting for driver assignment",
                timestamp = 0L,
                isCompleted = false
            )
        }

        if (status.ordinal < TripStatus.STARTED.ordinal) {
            future += TimelineEvent(
                type = TripEventType.STARTED,
                title = "Pickup",
                description = "Driver will pick up your package",
                timestamp = 0L,
                isCompleted = false
            )
        }

        if (status.ordinal < TripStatus.COMPLETED.ordinal) {
            future += TimelineEvent(
                type = TripEventType.COMPLETED,
                title = "Delivery",
                description = "Package will be delivered to destination",
                timestamp = 0L,
                isCompleted = false
            )
        }

        return future
    }

    companion object {
        /**
         * Convert a raw [Trip] to a sanitised [PublicTripInfo].
         * Only safe fields are transferred.
         */
        fun fromTrip(trip: Trip): PublicTripInfo = PublicTripInfo(
            trackingId = trip.trackingId,
            status = trip.status,
            pickupAddress = trip.pickupAddress,
            dropAddress = trip.dropAddress,
            pickupLat = trip.pickupLocation.latitude,
            pickupLng = trip.pickupLocation.longitude,
            dropLat = trip.dropLocation.latitude,
            dropLng = trip.dropLocation.longitude,
            distanceKm = trip.distance,
            createdAt = trip.createdAt,
            startTime = trip.startTime,
            endTime = trip.endTime,
            internalTripId = trip.tripId,
            internalCompanyId = trip.companyId,
            internalVehicleId = trip.vehicleId,
            internalDriverId = trip.driverId
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  TIMELINE EVENT
// ═══════════════════════════════════════════════════════════════

/**
 * A single event in the delivery timeline shown to the customer.
 */
data class TimelineEvent(
    val type: TripEventType,
    val title: String,
    val description: String,
    val timestamp: Long,
    val isCompleted: Boolean
)

// ═══════════════════════════════════════════════════════════════
//  PUBLIC DRIVER INFO
// ═══════════════════════════════════════════════════════════════

/**
 * Sanitised driver information for the public customer.
 * The phone number is ALWAYS masked for privacy.
 */
data class PublicDriverInfo(
    /** Driver's display name (first name + last initial) */
    val displayName: String = "",

    /** Masked phone — e.g. "XXXXX XX123" (last 3 digits) */
    val maskedPhone: String = "",

    /** Driver's star rating (0.0–5.0) */
    val rating: Float = 0f,

    /** Total completed deliveries */
    val completedTrips: Int = 0
) {
    val ratingDisplay: String get() = if (rating > 0f) "%.1f".format(rating) else "—"
    val hasPhone: Boolean get() = maskedPhone.isNotBlank() && maskedPhone != "XXXXX XXXXX"
    val tripsDisplay: String get() = when {
        completedTrips >= 1000 -> "${completedTrips / 1000}K+ deliveries"
        completedTrips > 0    -> "$completedTrips deliveries"
        else                  -> ""
    }

    companion object {
        /**
         * Build a [PublicDriverInfo] from raw User + Driver models.
         * Phone is always masked; only last 3 digits shown.
         */
        fun fromUserAndDriver(
            fullName: String,
            phone: String,
            rating: Float,
            totalTrips: Int
        ): PublicDriverInfo {
            return PublicDriverInfo(
                displayName = sanitiseName(fullName),
                maskedPhone = maskPhone(phone),
                rating = rating,
                completedTrips = totalTrips
            )
        }

        /**
         * Show first name + last initial.
         * "Rahul Sharma" → "Rahul S."
         * "Rahul"        → "Rahul"
         */
        private fun sanitiseName(fullName: String): String {
            val parts = fullName.trim().split("\\s+".toRegex())
            return when {
                parts.size >= 2 -> "${parts.first()} ${parts.last().first()}."
                parts.isNotEmpty() -> parts.first()
                else -> "Driver"
            }
        }

        /**
         * Mask a phone number, revealing only the last 3 digits.
         *
         * "9876543210" → "XXXXXXX210"
         * "+91 98765 43210" → "XXXXXXXXXX210"
         * ""             → "XXXXX XXXXX"
         */
        private fun maskPhone(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 4) return "XXXXX XXXXX"

            val visible = digits.takeLast(3)
            val masked = "X".repeat(digits.length - 3)

            // Format into groups for readability
            return when {
                digits.length == 10 -> {
                    // Indian mobile: XXXXXXX210
                    "${masked.take(7)}$visible"
                }
                digits.length > 10 -> {
                    // Country code included
                    val ccLen = digits.length - 10
                    val cc = "X".repeat(ccLen)
                    val rest = "X".repeat(7)
                    "+$cc $rest$visible"
                }
                else -> "$masked$visible"
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  PUBLIC VEHICLE INFO
// ═══════════════════════════════════════════════════════════════

/**
 * Minimal vehicle info shown to the public customer.
 */
data class PublicVehicleInfo(
    /** Registration number — e.g. "MH 12 AB 1234" */
    val number: String = "",

    /** Vehicle type label — e.g. "Truck", "Van" */
    val typeLabel: String = ""
) {
    val displayLabel: String get() = when {
        typeLabel.isNotBlank() && number.isNotBlank() -> "$typeLabel • $number"
        number.isNotBlank() -> number
        typeLabel.isNotBlank() -> typeLabel
        else -> "Vehicle"
    }

    companion object {
        fun fromVehicle(vehicle: Vehicle): PublicVehicleInfo = PublicVehicleInfo(
            number = vehicle.number,
            typeLabel = vehicle.type.name.lowercase()
                .replaceFirstChar { it.uppercase() }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  PUBLIC LIVE LOCATION
// ═══════════════════════════════════════════════════════════════

/**
 * Sanitised live location exposed to the customer.
 * Only lat/lng, speed, heading, and timestamps are included.
 */
data class PublicLiveLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speedKmh: Float = 0f,
    val heading: Float = 0f,
    val timestamp: Long = 0L,
    val isMoving: Boolean = false
) {
    val isValid: Boolean get() = lat != 0.0 || lng != 0.0

    companion object {
        fun fromTrackingLocation(tl: TrackingLocation): PublicLiveLocation =
            PublicLiveLocation(
                lat = tl.lat,
                lng = tl.lng,
                speedKmh = tl.speedKmh,
                heading = tl.heading,
                timestamp = tl.timestamp,
                isMoving = tl.isMoving
            )
    }
}

// ═══════════════════════════════════════════════════════════════
//  RECENT SEARCH
// ═══════════════════════════════════════════════════════════════

/**
 * A stored recent tracking-ID search.
 */
data class RecentSearch(
    val trackingId: String,
    val statusLabel: String,
    val searchedAt: Long = System.currentTimeMillis()
) {
    val timeAgoDisplay: String get() {
        val elapsed = System.currentTimeMillis() - searchedAt
        val minutes = elapsed / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1  -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24   -> "${hours}h ago"
            days < 7     -> "${days}d ago"
            else         -> "${days / 7}w ago"
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TRACKING SCREEN STATE
// ═══════════════════════════════════════════════════════════════

/**
 * Sealed class representing the overall state of the public
 * tracking screens — drives UI visibility logic in fragments.
 */
sealed class PublicTrackingState {
    data object Idle : PublicTrackingState()
    data object Searching : PublicTrackingState()
    data class Found(val tripInfo: PublicTripInfo) : PublicTrackingState()
    data class TrackingIdInvalid(val message: String) : PublicTrackingState()
    data class TrackingExpired(val message: String) : PublicTrackingState()
    data class NetworkError(val message: String) : PublicTrackingState()
}

/**
 * Live tracking connection state.
 */
enum class LiveConnectionState {
    CONNECTING,
    CONNECTED,
    VEHICLE_OFFLINE,
    DISCONNECTED,
    ERROR
}
