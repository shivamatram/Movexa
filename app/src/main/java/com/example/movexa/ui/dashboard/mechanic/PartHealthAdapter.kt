package com.example.movexa.ui.dashboard.mechanic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.service.MaintenanceScheduler
import com.example.movexa.service.MaintenanceScheduler.StatusLabel
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Adapter for displaying part health status items with usage progress bars
 * in the parts overview section.
 */
class PartHealthAdapter : ListAdapter<MaintenanceScheduler.PartStatus,
        PartHealthAdapter.PartHealthViewHolder>(PartStatusDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartHealthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_part_health, parent, false)
        return PartHealthViewHolder(view)
    }

    override fun onBindViewHolder(holder: PartHealthViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PartHealthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPartName: TextView = itemView.findViewById(R.id.tvHealthPartName)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvHealthBadge)
        private val progressBar: LinearProgressIndicator =
            itemView.findViewById(R.id.progressHealth)
        private val tvUsage: TextView = itemView.findViewById(R.id.tvHealthUsage)
        private val tvRemaining: TextView = itemView.findViewById(R.id.tvHealthRemaining)

        fun bind(status: MaintenanceScheduler.PartStatus) {
            val ctx = itemView.context

            tvPartName.text = status.partName

            // Badge
            tvBadge.text = status.statusLabel.label
            val badgeBg = when (status.statusLabel) {
                StatusLabel.OVERDUE -> R.drawable.bg_badge_overdue
                StatusLabel.DUE_SOON -> R.drawable.bg_badge_due_soon
                StatusLabel.UPCOMING -> R.drawable.bg_badge_upcoming
                StatusLabel.OK -> R.drawable.bg_badge_ok
                StatusLabel.NO_RECORD -> R.drawable.bg_badge_ok
            }
            tvBadge.setBackgroundResource(badgeBg)

            val badgeTextColor = when (status.statusLabel) {
                StatusLabel.OVERDUE -> R.color.maint_overdue
                StatusLabel.DUE_SOON -> R.color.maint_due_soon
                StatusLabel.UPCOMING -> R.color.maint_upcoming
                StatusLabel.OK -> R.color.maint_ok
                StatusLabel.NO_RECORD -> R.color.maint_no_record
            }
            tvBadge.setTextColor(ctx.getColor(badgeTextColor))

            // Progress
            val clampedPercent = status.usagePercent.coerceIn(0f, 100f).toInt()
            progressBar.progress = clampedPercent
            val progressColor = when {
                clampedPercent >= 90 -> R.color.maint_progress_critical
                clampedPercent >= 70 -> R.color.maint_progress_warn
                else -> R.color.maint_progress_safe
            }
            progressBar.setIndicatorColor(ctx.getColor(progressColor))

            // Usage text
            tvUsage.text = ctx.getString(R.string.maint_part_usage, status.usagePercent)

            // Remaining
            tvRemaining.text = if (status.remainingKm > 0) {
                ctx.getString(R.string.maint_part_remaining, status.remainingKm)
            } else {
                ctx.getString(R.string.maint_part_expired)
            }
        }
    }

    private class PartStatusDiffCallback :
        DiffUtil.ItemCallback<MaintenanceScheduler.PartStatus>() {
        override fun areItemsTheSame(
            oldItem: MaintenanceScheduler.PartStatus,
            newItem: MaintenanceScheduler.PartStatus
        ): Boolean = oldItem.partId == newItem.partId

        override fun areContentsTheSame(
            oldItem: MaintenanceScheduler.PartStatus,
            newItem: MaintenanceScheduler.PartStatus
        ): Boolean = oldItem == newItem
    }
}
