package com.example.movexa.ui.trips

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.TripEvent
import com.example.movexa.data.model.enums.TripEventType
import com.example.movexa.databinding.ItemTimelineEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for the trip event timeline.
 *
 * Renders a vertical timeline with connected dots and lines,
 * each representing a TripEvent in chronological order.
 *
 * Features:
 * - Timeline dot colors based on event type
 * - Top/bottom connector line visibility based on item position
 * - Active (colored) vs inactive (grey) dot styling
 * - Formatted timestamps
 * - Optional description text
 */
class TimelineEventAdapter :
    ListAdapter<TripEvent, TimelineEventAdapter.TimelineViewHolder>(TimelineDiffCallback()) {

    // ── ViewHolder ──────────────────────────────────────────────

    inner class TimelineViewHolder(
        private val binding: ItemTimelineEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: TripEvent, position: Int, totalCount: Int) {
            // ── Event Title ─────────────────────────────────────
            binding.tvEventTitle.text = event.type.displayName

            // ── Description ─────────────────────────────────────
            if (event.description.isNotBlank()) {
                binding.tvEventDescription.visibility = View.VISIBLE
                binding.tvEventDescription.text = event.description
            } else {
                binding.tvEventDescription.visibility = View.GONE
            }

            // ── Timestamp ───────────────────────────────────────
            binding.tvEventTimestamp.text = formatTimestamp(event.timestamp)

            // ── Timeline Connector Lines ────────────────────────
            // Hide top line for first item
            binding.lineTop.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE

            // Hide bottom line for last item
            binding.lineBottom.visibility = if (position == totalCount - 1)
                View.INVISIBLE else View.VISIBLE

            // ── Dot Color ───────────────────────────────────────
            val dotColor = getDotColor(event.type)
            binding.dotEvent.background.setTint(dotColor)

            // Active line colors for non-last items
            val lineColor = if (isActiveEventType(event.type)) {
                ContextCompat.getColor(binding.root.context, R.color.timeline_line_active)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.timeline_line)
            }
            binding.lineTop.setBackgroundColor(lineColor)
            if (position < totalCount - 1) {
                binding.lineBottom.setBackgroundColor(lineColor)
            }
        }

        /**
         * Get the appropriate dot color for the event type.
         * Major lifecycle events get colored dots; minor ones are grey.
         */
        private fun getDotColor(type: TripEventType): Int {
            val context = binding.root.context
            return when (type) {
                TripEventType.CREATED ->
                    ContextCompat.getColor(context, R.color.trip_created)
                TripEventType.DRIVER_ASSIGNED ->
                    ContextCompat.getColor(context, R.color.trip_assigned)
                TripEventType.STARTED ->
                    ContextCompat.getColor(context, R.color.trip_started)
                TripEventType.COMPLETED ->
                    ContextCompat.getColor(context, R.color.trip_completed)
                TripEventType.CANCELLED ->
                    ContextCompat.getColor(context, R.color.trip_cancelled)
                TripEventType.INCIDENT_REPORTED ->
                    ContextCompat.getColor(context, R.color.error)
                TripEventType.DEVIATION_DETECTED ->
                    ContextCompat.getColor(context, R.color.warning)
                TripEventType.CHECKPOINT_REACHED ->
                    ContextCompat.getColor(context, R.color.trip_accepted)
                else ->
                    ContextCompat.getColor(context, R.color.timeline_dot_inactive)
            }
        }

        /**
         * Whether the event type represents a major lifecycle event.
         */
        private fun isActiveEventType(type: TripEventType): Boolean {
            return type in listOf(
                TripEventType.CREATED,
                TripEventType.DRIVER_ASSIGNED,
                TripEventType.STARTED,
                TripEventType.COMPLETED,
                TripEventType.CANCELLED
            )
        }

        /**
         * Format a timestamp into a human-readable string.
         */
        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp <= 0) return "—"
            return try {
                val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                sdf.format(Date(timestamp))
            } catch (e: Exception) {
                "—"
            }
        }
    }

    // ── Adapter Methods ─────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val binding = ItemTimelineEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TimelineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    // ── DiffUtil ────────────────────────────────────────────────

    class TimelineDiffCallback : DiffUtil.ItemCallback<TripEvent>() {
        override fun areItemsTheSame(oldItem: TripEvent, newItem: TripEvent): Boolean {
            return oldItem.eventId == newItem.eventId
        }

        override fun areContentsTheSame(oldItem: TripEvent, newItem: TripEvent): Boolean {
            return oldItem == newItem
        }
    }
}
