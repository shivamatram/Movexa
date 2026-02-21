package com.example.movexa.ui.dashboard.manager

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.Alert
import com.example.movexa.data.model.enums.AlertPriority
import com.example.movexa.data.model.enums.AlertStatus
import com.example.movexa.data.model.enums.AlertType
import com.example.movexa.databinding.ItemAlertCardBinding

/**
 * RecyclerView adapter for alert cards in the Manager Alerts screen.
 *
 * Features:
 * ─────────────────────────────────────────────────────────────
 * - DiffUtil for efficient list updates with animations
 * - Priority-based color coding (strip, badge, type icon)
 * - Alert type icon mapping (overspeed, braking, idle, etc.)
 * - Dynamic vehicle/driver name resolution via callbacks
 * - Relative time formatting ("5 min ago", "2 hr ago")
 * - Expandable action buttons on card click
 * - Status badge with color-coded background
 * - Action required / auto-generated indicators
 * - Resolved info display for resolved tab
 *
 * Callbacks:
 * ─────────────────────────────────────────────────────────────
 * - onResolve: resolve an alert
 * - onAcknowledge: acknowledge an alert
 * - onDismiss: dismiss an alert
 * - onCardClick: expand/collapse action buttons
 *
 * @see ItemAlertCardBinding for the layout
 */
class AlertListAdapter : ListAdapter<Alert, AlertListAdapter.AlertViewHolder>(AlertDiffCallback()) {

    // ── Callbacks ───────────────────────────────────────────────

    /** Resolve button clicked. */
    var onResolve: ((Alert) -> Unit)? = null

    /** Acknowledge button clicked. */
    var onAcknowledge: ((Alert) -> Unit)? = null

    /** Dismiss button clicked. */
    var onDismiss: ((Alert) -> Unit)? = null

    /** Card body clicked (expand/collapse). */
    var onCardClick: ((Alert, Int) -> Unit)? = null

    /** Resolve a vehicle number from vehicleId. */
    var vehicleNameResolver: ((String) -> String?)? = null

    /** Resolve a driver name from driverId. */
    var driverNameResolver: ((String) -> String?)? = null

    // ── Expand State ────────────────────────────────────────────

    /** Track which alertIds have expanded action buttons. */
    private val expandedItems = mutableSetOf<String>()

    // ═══════════════════════════════════════════════════════════
    //  Adapter Overrides
    // ═══════════════════════════════════════════════════════════

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = getItem(position)
        holder.bind(alert)
    }

    // ═══════════════════════════════════════════════════════════
    //  Public Methods
    // ═══════════════════════════════════════════════════════════

    /**
     * Toggle the expanded state of an alert card's action buttons.
     *
     * @param alertId The alert ID to toggle.
     * @return True if now expanded, false if collapsed.
     */
    fun toggleExpanded(alertId: String): Boolean {
        val isExpanded = if (expandedItems.contains(alertId)) {
            expandedItems.remove(alertId)
            false
        } else {
            expandedItems.add(alertId)
            true
        }
        return isExpanded
    }

    /**
     * Collapse all expanded items.
     */
    fun collapseAll() {
        expandedItems.clear()
    }

    /**
     * Get alert at a specific adapter position (for swipe handlers).
     */
    fun getAlertAt(position: Int): Alert? {
        return if (position in 0 until itemCount) getItem(position) else null
    }

    // ═══════════════════════════════════════════════════════════
    //  ViewHolder
    // ═══════════════════════════════════════════════════════════

    inner class AlertViewHolder(
        private val binding: ItemAlertCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: Alert) {
            val context = binding.root.context

            // ── Priority Strip Color ────────────────────────────
            binding.viewPriorityStrip.setBackgroundColor(
                ContextCompat.getColor(context, getPriorityColor(alert.priority))
            )

            // ── Alert Type Icon ─────────────────────────────────
            binding.ivAlertTypeIcon.setImageResource(getTypeIcon(alert.type))
            binding.ivAlertTypeIcon.setColorFilter(
                ContextCompat.getColor(context, getTypeColor(alert.type))
            )

            // ── Tint the icon background ────────────────────────
            val iconBg = binding.layoutTypeIcon.background
            if (iconBg is GradientDrawable) {
                iconBg.mutate()
                // Light tint of the type color using surface variant
                iconBg.setColor(ContextCompat.getColor(context, R.color.surface_variant))
            }

            // ── Title ───────────────────────────────────────────
            binding.tvAlertTitle.text = alert.title

            // ── Alert Type Label ────────────────────────────────
            binding.tvAlertType.text = alert.type.displayName.uppercase()

            // ── Priority Badge ──────────────────────────────────
            binding.tvPriorityBadge.text = alert.priority.displayName.uppercase()
            setPriorityBadgeColors(binding.tvPriorityBadge, alert.priority)

            // ── Message ─────────────────────────────────────────
            binding.tvAlertMessage.text = alert.message

            // ── Vehicle Info ────────────────────────────────────
            val vehicleId = alert.vehicleId
            if (!vehicleId.isNullOrBlank()) {
                val vehicleNumber = vehicleNameResolver?.invoke(vehicleId)
                binding.layoutVehicleInfo.visibility = View.VISIBLE
                binding.tvVehicleNumber.text = vehicleNumber ?: vehicleId.take(8)
                binding.viewMetaDot1.visibility = View.VISIBLE
            } else {
                binding.layoutVehicleInfo.visibility = View.GONE
                binding.viewMetaDot1.visibility = View.GONE
            }

            // ── Driver Info ─────────────────────────────────────
            val driverId = alert.driverId
            if (!driverId.isNullOrBlank()) {
                val driverName = driverNameResolver?.invoke(driverId)
                binding.layoutDriverInfo.visibility = View.VISIBLE
                binding.tvDriverName.text = driverName ?: driverId.take(8)
            } else {
                binding.layoutDriverInfo.visibility = View.GONE
                if (vehicleId.isNullOrBlank()) {
                    binding.viewMetaDot1.visibility = View.GONE
                }
            }

            // ── Time ────────────────────────────────────────────
            binding.tvAlertTime.text = formatRelativeTime(alert.timestamp)

            // ── Status Badge ────────────────────────────────────
            binding.tvStatusBadge.text = alert.status.displayName.uppercase()
            setStatusBadgeColors(binding.tvStatusBadge, alert.status)

            // ── Action Required ─────────────────────────────────
            binding.tvActionRequired.visibility =
                if (alert.actionRequired && alert.status.isOpen()) View.VISIBLE else View.GONE

            // ── Auto Generated ──────────────────────────────────
            binding.tvAutoGenerated.visibility =
                if (alert.autoGenerated) View.VISIBLE else View.GONE

            // ── Resolved Info ───────────────────────────────────
            if (alert.status == AlertStatus.RESOLVED && alert.resolvedAt > 0) {
                binding.tvResolvedInfo.visibility = View.VISIBLE
                binding.tvResolvedInfo.text = context.getString(
                    R.string.alert_time_resolved_ago,
                    formatRelativeTime(alert.resolvedAt)
                )
            } else {
                binding.tvResolvedInfo.visibility = View.GONE
            }

            // ── Action Buttons (Expandable) ─────────────────────
            val isExpanded = expandedItems.contains(alert.alertId)
            binding.layoutActions.visibility = if (isExpanded && alert.status.isOpen()) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // Show/hide specific action buttons based on status
            if (alert.status == AlertStatus.ACTIVE) {
                binding.btnAcknowledge.visibility = View.VISIBLE
                binding.btnResolve.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.VISIBLE
            } else if (alert.status == AlertStatus.ACKNOWLEDGED) {
                binding.btnAcknowledge.visibility = View.GONE
                binding.btnResolve.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.VISIBLE
            } else {
                binding.btnAcknowledge.visibility = View.GONE
                binding.btnResolve.visibility = View.GONE
                binding.btnDismiss.visibility = View.GONE
            }

            // ── Click Listeners ─────────────────────────────────

            binding.cardAlert.setOnClickListener {
                onCardClick?.invoke(alert, bindingAdapterPosition)
            }

            binding.btnResolve.setOnClickListener {
                onResolve?.invoke(alert)
            }

            binding.btnAcknowledge.setOnClickListener {
                onAcknowledge?.invoke(alert)
            }

            binding.btnDismiss.setOnClickListener {
                onDismiss?.invoke(alert)
            }
        }

        // ── Priority Badge Color Helper ─────────────────────────

        private fun setPriorityBadgeColors(view: android.widget.TextView, priority: AlertPriority) {
            val context = view.context
            val bg = view.background
            if (bg is GradientDrawable) {
                bg.mutate()
                bg.setColor(ContextCompat.getColor(context, getPriorityColor(priority)))
            } else {
                // If bg_priority_badge is not a GradientDrawable, use BackgroundTint
                view.backgroundTintList = ContextCompat.getColorStateList(
                    context, getPriorityColor(priority)
                )
            }
            view.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }

        // ── Status Badge Color Helper ───────────────────────────

        private fun setStatusBadgeColors(view: android.widget.TextView, status: AlertStatus) {
            val context = view.context
            val (textColor, bgColor) = getStatusColors(status)
            view.setTextColor(ContextCompat.getColor(context, textColor))
            val bg = view.background
            if (bg is GradientDrawable) {
                bg.mutate()
                bg.setColor(ContextCompat.getColor(context, bgColor))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Color / Icon Mapping
    // ═══════════════════════════════════════════════════════════

    companion object {

        /**
         * Map alert priority to its primary color resource.
         */
        fun getPriorityColor(priority: AlertPriority): Int {
            return when (priority) {
                AlertPriority.CRITICAL -> R.color.alert_critical
                AlertPriority.HIGH -> R.color.alert_high
                AlertPriority.MEDIUM -> R.color.alert_medium
                AlertPriority.LOW -> R.color.alert_low
            }
        }

        /**
         * Map alert type to its icon drawable resource.
         */
        fun getTypeIcon(type: AlertType): Int {
            return when (type) {
                AlertType.OVER_SPEED -> R.drawable.ic_alert_overspeed
                AlertType.HARSH_BRAKING -> R.drawable.ic_alert_harsh_braking
                AlertType.HARSH_ACCELERATION -> R.drawable.ic_alert_acceleration
                AlertType.LONG_IDLE -> R.drawable.ic_alert_long_idle
                AlertType.ROUTE_DEVIATION -> R.drawable.ic_alert_route_deviation
                AlertType.ACCIDENT_SUSPECTED -> R.drawable.ic_alert_accident
                else -> R.drawable.ic_nav_alerts // Fallback for non-behavioral alerts
            }
        }

        /**
         * Map alert type to its icon tint color resource.
         */
        fun getTypeColor(type: AlertType): Int {
            return when (type) {
                AlertType.OVER_SPEED -> R.color.alert_overspeed
                AlertType.HARSH_BRAKING -> R.color.alert_harsh_braking
                AlertType.HARSH_ACCELERATION -> R.color.alert_harsh_acceleration
                AlertType.LONG_IDLE -> R.color.alert_long_idle
                AlertType.ROUTE_DEVIATION -> R.color.alert_route_deviation
                AlertType.ACCIDENT_SUSPECTED -> R.color.alert_accident
                else -> R.color.text_secondary // Fallback
            }
        }

        /**
         * Map alert status to its text color and background color pair.
         */
        fun getStatusColors(status: AlertStatus): Pair<Int, Int> {
            return when (status) {
                AlertStatus.ACTIVE -> R.color.alert_status_active to R.color.alert_status_active_bg
                AlertStatus.ACKNOWLEDGED -> R.color.alert_status_acknowledged to R.color.alert_status_acknowledged_bg
                AlertStatus.RESOLVED -> R.color.alert_status_resolved to R.color.alert_status_resolved_bg
                AlertStatus.DISMISSED -> R.color.alert_status_dismissed to R.color.alert_status_dismissed_bg
            }
        }

        /**
         * Format a timestamp to a human-readable relative time string.
         *
         * @param timestamp Epoch milliseconds.
         * @return Formatted string like "Just now", "5 min ago", "2 hr ago", "Yesterday".
         */
        fun formatRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diffMs = now - timestamp

            if (diffMs < 0) return "Just now"

            val diffSeconds = diffMs / 1_000
            val diffMinutes = diffMs / 60_000
            val diffHours = diffMs / 3_600_000
            val diffDays = diffMs / 86_400_000

            return when {
                diffSeconds < 60 -> "Just now"
                diffMinutes < 60 -> "${diffMinutes}m ago"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays == 1L -> "Yesterday"
                diffDays < 7 -> "${diffDays}d ago"
                diffDays < 30 -> "${diffDays / 7}w ago"
                else -> "${diffDays / 30}mo ago"
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DiffUtil
    // ═══════════════════════════════════════════════════════════

    class AlertDiffCallback : DiffUtil.ItemCallback<Alert>() {
        override fun areItemsTheSame(oldItem: Alert, newItem: Alert): Boolean {
            return oldItem.alertId == newItem.alertId
        }

        override fun areContentsTheSame(oldItem: Alert, newItem: Alert): Boolean {
            return oldItem == newItem
        }
    }
}
