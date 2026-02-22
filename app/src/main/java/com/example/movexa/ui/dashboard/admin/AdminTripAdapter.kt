package com.example.movexa.ui.dashboard.admin

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.Trip
import com.example.movexa.data.model.enums.TripStatus
import com.example.movexa.databinding.ItemAdminTripCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * RecyclerView adapter for admin trip list with DiffUtil.
 *
 * Features:
 * - Animated card insertion/removal
 * - Color-coded status badges
 * - Revenue display for completed trips
 * - Relative timestamp formatting
 * - Long-press for admin actions
 * - Name resolution via callbacks
 */
class AdminTripAdapter(
    private val onTripClick: (Trip) -> Unit,
    private val onTripLongClick: (Trip) -> Unit,
    private val driverNameResolver: (String) -> String,
    private val vehicleNumberResolver: (String) -> String
) : ListAdapter<Trip, AdminTripAdapter.TripViewHolder>(TripDiffCallback()) {

    // Track last animated position for staggered entrance
    private var lastAnimatedPosition = -1
    private var animationsEnabled = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemAdminTripCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val trip = getItem(position)
        holder.bind(trip)

        // Staggered entrance animation
        if (animationsEnabled && position > lastAnimatedPosition) {
            val animation = AnimationUtils.loadAnimation(
                holder.itemView.context, R.anim.scale_up_fade_in
            )
            animation.startOffset = (position % 5) * 50L
            holder.itemView.startAnimation(animation)
            lastAnimatedPosition = position
        }
    }

    override fun onViewDetachedFromWindow(holder: TripViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.clearAnimation()
    }

    fun resetAnimations() {
        lastAnimatedPosition = -1
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
    }

    // ── ViewHolder ──────────────────────────────────────────────

    inner class TripViewHolder(
        private val binding: ItemAdminTripCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.cardTrip.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTripClick(getItem(pos))
                }
            }
            binding.cardTrip.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTripLongClick(getItem(pos))
                    true
                } else false
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(trip: Trip) {
            val context = binding.root.context

            // Status badge
            binding.tvStatusBadge.text = trip.status.displayName
            val (textColor, bgTint) = getStatusColors(trip.status)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, textColor))
            binding.tvStatusBadge.background.setTint(ContextCompat.getColor(context, bgTint))

            // Audit flag
            val isFlagged = trip.metadata["auditFlagged"] == true
            binding.tvAuditFlag.visibility = if (isFlagged) View.VISIBLE else View.GONE

            // Tracking ID
            binding.tvTrackingId.text = trip.trackingId.ifBlank {
                trip.tripId.take(12).uppercase()
            }

            // Route addresses
            binding.tvPickupAddress.text = trip.pickupAddress.ifBlank {
                context.getString(R.string.admin_trips_no_address)
            }
            binding.tvDropAddress.text = trip.dropAddress.ifBlank {
                context.getString(R.string.admin_trips_no_address)
            }

            // Driver name
            val driverName = if (trip.driverId.isNotBlank()) {
                driverNameResolver(trip.driverId)
            } else {
                context.getString(R.string.admin_trips_unassigned)
            }
            binding.tvDriverName.text = driverName

            // Vehicle number
            val vehicleNumber = if (trip.vehicleId.isNotBlank()) {
                vehicleNumberResolver(trip.vehicleId)
            } else {
                context.getString(R.string.admin_trips_no_vehicle)
            }
            binding.tvVehicleNumber.text = vehicleNumber

            // Distance
            if (trip.distance > 0) {
                binding.tvDistance.text = trip.distanceDisplay
            } else if (trip.estimatedDistance > 0) {
                binding.tvDistance.text = "~${"%.1f km".format(trip.estimatedDistance)}"
            } else {
                binding.tvDistance.text = "--"
            }

            // Duration
            val durationMs = when {
                trip.duration > 0 -> trip.duration
                trip.endTime > trip.startTime -> trip.endTime - trip.startTime
                trip.startTime > 0 -> System.currentTimeMillis() - trip.startTime
                else -> 0L
            }
            if (durationMs > 0) {
                binding.tvDuration.text = formatDuration(durationMs)
                binding.layoutDuration.visibility = View.VISIBLE
                binding.dividerDuration.visibility = View.VISIBLE
            } else {
                binding.layoutDuration.visibility = View.GONE
                binding.dividerDuration.visibility = View.GONE
            }

            // Revenue (only for completed trips with metadata)
            val revenue = (trip.metadata["revenue"] as? Number)?.toDouble()
            if (trip.status == TripStatus.COMPLETED && revenue != null && revenue > 0) {
                binding.layoutRevenue.visibility = View.VISIBLE
                binding.dividerRevenue.visibility = View.VISIBLE
                binding.tvRevenue.text = "₹%,.0f".format(revenue)
            } else {
                binding.layoutRevenue.visibility = View.GONE
                binding.dividerRevenue.visibility = View.GONE
            }

            // Timestamp
            val timestamp = when {
                trip.endTime > 0 -> trip.endTime
                trip.startTime > 0 -> trip.startTime
                trip.createdAt > 0 -> trip.createdAt
                else -> 0L
            }
            binding.tvTimestamp.text = if (timestamp > 0) {
                formatRelativeTime(timestamp)
            } else {
                ""
            }
        }

        private fun getStatusColors(status: TripStatus): Pair<Int, Int> {
            return when (status) {
                TripStatus.CREATED -> R.color.trip_created to R.color.trip_created_bg
                TripStatus.ASSIGNED -> R.color.trip_assigned to R.color.trip_assigned_bg
                TripStatus.ACCEPTED -> R.color.trip_accepted to R.color.trip_accepted_bg
                TripStatus.REJECTED_BY_DRIVER -> R.color.trip_rejected to R.color.trip_rejected_bg
                TripStatus.STARTED -> R.color.trip_started to R.color.trip_started_bg
                TripStatus.COMPLETED -> R.color.trip_completed to R.color.trip_completed_bg
                TripStatus.CANCELLED -> R.color.trip_cancelled to R.color.trip_cancelled_bg
            }
        }
    }

    // ── Formatting Helpers ──────────────────────────────────────

    companion object {
        private val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        fun formatDuration(millis: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }

        fun formatRelativeTime(timestampMs: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestampMs

            return when {
                diff < 0 -> dateFormat.format(Date(timestampMs))
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> {
                    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                    "${mins}m ago"
                }
                diff < TimeUnit.DAYS.toMillis(1) -> {
                    val hrs = TimeUnit.MILLISECONDS.toHours(diff)
                    "${hrs}h ago"
                }
                diff < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    "${days}d ago"
                }
                else -> dateFormat.format(Date(timestampMs))
            }
        }
    }

    // ── DiffUtil ────────────────────────────────────────────────

    class TripDiffCallback : DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip): Boolean {
            return oldItem.tripId == newItem.tripId
        }

        override fun areContentsTheSame(oldItem: Trip, newItem: Trip): Boolean {
            return oldItem == newItem
        }
    }
}
