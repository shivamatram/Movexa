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

/**
 * Adapter for displaying maintenance status items (overdue, due-soon, upcoming,
 * OK, no-record) in the service overview section.
 */
class MaintenanceStatusAdapter : ListAdapter<MaintenanceScheduler.MaintenanceStatus,
        MaintenanceStatusAdapter.StatusViewHolder>(StatusDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_maintenance_status, parent, false)
        return StatusViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvServiceType: TextView = itemView.findViewById(R.id.tvStatusServiceType)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        private val tvDetail: TextView = itemView.findViewById(R.id.tvStatusDetail)

        fun bind(status: MaintenanceScheduler.MaintenanceStatus) {
            tvServiceType.text = status.serviceType.displayName

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
            tvBadge.setTextColor(itemView.context.getColor(badgeTextColor))

            // Detail line
            val ctx = itemView.context
            tvDetail.text = when {
                status.statusLabel == StatusLabel.NO_RECORD ->
                    "No service record found"
                status.statusLabel == StatusLabel.OVERDUE ->
                    ctx.getString(R.string.maint_overdue_km, -status.remainingKm)
                else ->
                    ctx.getString(R.string.maint_remaining_km, status.remainingKm)
            }
        }
    }

    private class StatusDiffCallback :
        DiffUtil.ItemCallback<MaintenanceScheduler.MaintenanceStatus>() {
        override fun areItemsTheSame(
            oldItem: MaintenanceScheduler.MaintenanceStatus,
            newItem: MaintenanceScheduler.MaintenanceStatus
        ): Boolean =
            oldItem.vehicleId == newItem.vehicleId &&
                    oldItem.serviceType == newItem.serviceType

        override fun areContentsTheSame(
            oldItem: MaintenanceScheduler.MaintenanceStatus,
            newItem: MaintenanceScheduler.MaintenanceStatus
        ): Boolean = oldItem == newItem
    }
}
