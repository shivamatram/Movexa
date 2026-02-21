package com.example.movexa.ui.dashboard.driver

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.databinding.ItemViolationCardBinding

/**
 * RecyclerView adapter for displaying violation breakdown items
 * on the Driver Performance screen.
 *
 * Each item shows the violation type icon, name, event count,
 * and total penalty points.
 */
class ViolationListAdapter :
    ListAdapter<DriverPerformanceViewModel.ViolationItem, ViolationListAdapter.ViolationViewHolder>(
        ViolationDiffCallback()
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViolationViewHolder {
        val binding = ItemViolationCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViolationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViolationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ═══════════════════════════════════════════════════════════
    //  ViewHolder
    // ═══════════════════════════════════════════════════════════

    inner class ViolationViewHolder(
        private val binding: ItemViolationCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DriverPerformanceViewModel.ViolationItem) {
            val context = binding.root.context

            // ── Icon ────────────────────────────────────────────
            val (iconRes, tintColor, bgColor) = getViolationVisuals(item.type)
            binding.ivViolationIcon.setImageResource(iconRes)
            binding.ivViolationIcon.setColorFilter(
                ContextCompat.getColor(context, tintColor)
            )
            binding.ivViolationIcon.background?.setTint(
                ContextCompat.getColor(context, bgColor)
            )

            // ── Text ────────────────────────────────────────────
            binding.tvViolationName.text = item.name
            binding.tvViolationCount.text = context.getString(
                R.string.perf_violation_count, item.count
            )

            // ── Penalty Badge ───────────────────────────────────
            binding.tvViolationPenalty.text = context.getString(
                R.string.perf_violation_total_penalty, item.totalPenalty
            )
        }

        /**
         * Resolve the icon drawable, tint color, and background
         * color for a violation type.
         */
        private fun getViolationVisuals(
            type: DriverPerformanceViewModel.ViolationType
        ): Triple<Int, Int, Int> {
            return when (type) {
                DriverPerformanceViewModel.ViolationType.OVERSPEED -> Triple(
                    R.drawable.ic_alert_overspeed,
                    R.color.alert_overspeed,
                    R.color.alert_critical_bg
                )
                DriverPerformanceViewModel.ViolationType.HARSH_BRAKING -> Triple(
                    R.drawable.ic_alert_harsh_braking,
                    R.color.alert_harsh_braking,
                    R.color.alert_high_bg
                )
                DriverPerformanceViewModel.ViolationType.HARSH_ACCELERATION -> Triple(
                    R.drawable.ic_alert_acceleration,
                    R.color.alert_harsh_acceleration,
                    R.color.alert_high_bg
                )
                DriverPerformanceViewModel.ViolationType.LONG_IDLE -> Triple(
                    R.drawable.ic_alert_long_idle,
                    R.color.alert_long_idle,
                    R.color.alert_medium_bg
                )
                DriverPerformanceViewModel.ViolationType.ROUTE_DEVIATION -> Triple(
                    R.drawable.ic_alert_route_deviation,
                    R.color.alert_route_deviation,
                    R.color.alert_medium_bg
                )
                DriverPerformanceViewModel.ViolationType.ACCIDENT -> Triple(
                    R.drawable.ic_alert_accident,
                    R.color.alert_accident,
                    R.color.alert_critical_bg
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DiffCallback
    // ═══════════════════════════════════════════════════════════

    class ViolationDiffCallback :
        DiffUtil.ItemCallback<DriverPerformanceViewModel.ViolationItem>() {

        override fun areItemsTheSame(
            oldItem: DriverPerformanceViewModel.ViolationItem,
            newItem: DriverPerformanceViewModel.ViolationItem
        ): Boolean = oldItem.type == newItem.type

        override fun areContentsTheSame(
            oldItem: DriverPerformanceViewModel.ViolationItem,
            newItem: DriverPerformanceViewModel.ViolationItem
        ): Boolean = oldItem == newItem
    }
}
