package com.example.movexa.ui.public_tracking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.TimelineEvent
import com.example.movexa.databinding.ItemPublicTimelineEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══════════════════════════════════════════════════════════════════
 *  PUBLIC TIMELINE ADAPTER
 * ═══════════════════════════════════════════════════════════════════
 *
 * Displays the ordered delivery timeline in DeliveryDetailsFragment.
 * Each item shows:
 *  ● A dot (completed = green, current = pulsing blue, pending = grey)
 *  ● Connector lines (hidden for first/last items)
 *  ● Title, description, and formatted timestamp
 *
 * The adapter determines the "current" step as the last completed
 * event in the list.
 *
 * @since 2026-02-22
 */
class PublicTimelineAdapter : ListAdapter<TimelineEvent, PublicTimelineAdapter.TimelineViewHolder>(
    DIFF_CALLBACK
) {

    // ═══════════════════════════════════════════════════════════
    //  VIEW HOLDER
    // ═══════════════════════════════════════════════════════════

    inner class TimelineViewHolder(
        private val binding: ItemPublicTimelineEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())

        fun bind(item: TimelineEvent, position: Int, totalCount: Int) {
            val context = binding.root.context

            // ── Title ───────────────────────────────────────────
            binding.tvEventTitle.text = item.title

            // ── Description ─────────────────────────────────────
            if (item.description.isNotBlank()) {
                binding.tvEventDescription.text = item.description
                binding.tvEventDescription.visibility = View.VISIBLE
            } else {
                binding.tvEventDescription.visibility = View.GONE
            }

            // ── Timestamp ───────────────────────────────────────
            if (item.timestamp > 0L) {
                binding.tvEventTimestamp.text = dateFormat.format(Date(item.timestamp))
                binding.tvEventTimestamp.visibility = View.VISIBLE
            } else {
                binding.tvEventTimestamp.visibility = View.GONE
            }

            // ── Dot state ───────────────────────────────────────
            val isCurrentStep = item.isCompleted && !isNextCompleted(position)

            val dotDrawableRes = when {
                isCurrentStep -> R.drawable.bg_timeline_dot_current
                item.isCompleted -> R.drawable.bg_timeline_dot_completed
                else -> R.drawable.bg_timeline_dot_pending
            }
            binding.dotEvent.setBackgroundResource(dotDrawableRes)

            // ── Text styling based on completion ────────────────
            val titleColor = if (item.isCompleted) {
                ContextCompat.getColor(context, R.color.public_text_primary)
            } else {
                ContextCompat.getColor(context, R.color.public_text_hint)
            }
            binding.tvEventTitle.setTextColor(titleColor)

            val descColor = if (item.isCompleted) {
                ContextCompat.getColor(context, R.color.public_text_secondary)
            } else {
                ContextCompat.getColor(context, R.color.public_text_hint)
            }
            binding.tvEventDescription.setTextColor(descColor)

            // ── Connector lines ─────────────────────────────────
            binding.lineTop.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
            binding.lineBottom.visibility = if (position == totalCount - 1) {
                View.INVISIBLE
            } else {
                View.VISIBLE
            }

            // ── Line color (completed = primary, pending = grey) ─
            val lineColor = if (item.isCompleted) {
                ContextCompat.getColor(context, R.color.public_primary)
            } else {
                ContextCompat.getColor(context, R.color.public_timeline_line)
            }
            binding.lineTop.setBackgroundColor(
                if (position > 0 && getItem(position - 1).isCompleted) {
                    ContextCompat.getColor(context, R.color.public_primary)
                } else {
                    ContextCompat.getColor(context, R.color.public_timeline_line)
                }
            )
            binding.lineBottom.setBackgroundColor(lineColor)
        }

        /**
         * Check if the event after the current position is also completed.
         * Used to determine if this is the "current" (last completed) step.
         */
        private fun isNextCompleted(position: Int): Boolean {
            return if (position < itemCount - 1) {
                getItem(position + 1).isCompleted
            } else {
                false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ADAPTER OVERRIDES
    // ═══════════════════════════════════════════════════════════

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val binding = ItemPublicTimelineEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TimelineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    // ═══════════════════════════════════════════════════════════
    //  DIFF CALLBACK
    // ═══════════════════════════════════════════════════════════

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TimelineEvent>() {
            override fun areItemsTheSame(
                oldItem: TimelineEvent,
                newItem: TimelineEvent
            ): Boolean = oldItem.type == newItem.type

            override fun areContentsTheSame(
                oldItem: TimelineEvent,
                newItem: TimelineEvent
            ): Boolean = oldItem == newItem
        }
    }
}
