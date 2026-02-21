package com.example.movexa.ui.dashboard.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.databinding.ItemSuggestionCardBinding
import com.example.movexa.service.DriverScoringEngine

/**
 * RecyclerView adapter for displaying driving suggestions
 * on the Driver Performance screen.
 *
 * Each item shows a suggestion icon, title, detailed message,
 * severity indicator, and optional penalty impact badge.
 */
class SuggestionListAdapter :
    ListAdapter<DriverScoringEngine.DrivingSuggestion, SuggestionListAdapter.SuggestionViewHolder>(
        SuggestionDiffCallback()
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSuggestionCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ═══════════════════════════════════════════════════════════
    //  ViewHolder
    // ═══════════════════════════════════════════════════════════

    inner class SuggestionViewHolder(
        private val binding: ItemSuggestionCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DriverScoringEngine.DrivingSuggestion) {
            val context = binding.root.context

            // ── Icon ────────────────────────────────────────────
            val (iconRes, iconTint, iconBg) = getSuggestionVisuals(item.icon, item.severity)
            binding.ivSuggestionIcon.setImageResource(iconRes)
            binding.ivSuggestionIcon.setColorFilter(
                ContextCompat.getColor(context, iconTint)
            )
            binding.ivSuggestionIcon.background?.setTint(
                ContextCompat.getColor(context, iconBg)
            )

            // ── Text ────────────────────────────────────────────
            binding.tvSuggestionTitle.text = item.title
            binding.tvSuggestionMessage.text = item.message

            // ── Severity Dot ────────────────────────────────────
            val severityColor = getSeverityColor(item.severity)
            binding.viewSeverityDot.background?.setTint(
                ContextCompat.getColor(context, severityColor)
            )

            // ── Impact Badge ────────────────────────────────────
            if (item.penaltyImpact > 0) {
                binding.tvSuggestionImpact.visibility = View.VISIBLE
                binding.tvSuggestionImpact.text = context.getString(
                    R.string.perf_suggestion_impact, item.penaltyImpact
                )
            } else {
                binding.tvSuggestionImpact.visibility = View.GONE
            }

            // ── Card border tint for severity ───────────────────
            val cardView = binding.cardSuggestion
            val borderColor = getSeverityColor(item.severity)
            cardView.strokeColor = ContextCompat.getColor(context, borderColor)
            cardView.strokeWidth = if (item.severity == DriverScoringEngine.DrivingSuggestion.Severity.HIGH) {
                2
            } else {
                1
            }
        }

        /**
         * Get icon drawable and colors based on suggestion icon type.
         */
        private fun getSuggestionVisuals(
            icon: DriverScoringEngine.SuggestionIcon,
            severity: DriverScoringEngine.DrivingSuggestion.Severity
        ): Triple<Int, Int, Int> {
            val baseIconRes: Int
            val baseTint: Int
            when (icon) {
                DriverScoringEngine.SuggestionIcon.SPEED -> {
                    baseIconRes = R.drawable.ic_alert_overspeed
                    baseTint = R.color.alert_overspeed
                }
                DriverScoringEngine.SuggestionIcon.BRAKING -> {
                    baseIconRes = R.drawable.ic_alert_harsh_braking
                    baseTint = R.color.alert_harsh_braking
                }
                DriverScoringEngine.SuggestionIcon.ACCELERATION -> {
                    baseIconRes = R.drawable.ic_alert_acceleration
                    baseTint = R.color.alert_harsh_acceleration
                }
                DriverScoringEngine.SuggestionIcon.IDLE -> {
                    baseIconRes = R.drawable.ic_alert_long_idle
                    baseTint = R.color.alert_long_idle
                }
                DriverScoringEngine.SuggestionIcon.ROUTE -> {
                    baseIconRes = R.drawable.ic_alert_route_deviation
                    baseTint = R.color.alert_route_deviation
                }
                DriverScoringEngine.SuggestionIcon.ACCIDENT -> {
                    baseIconRes = R.drawable.ic_alert_accident
                    baseTint = R.color.alert_accident
                }
                DriverScoringEngine.SuggestionIcon.FUEL -> {
                    baseIconRes = R.drawable.ic_nav_fuel
                    baseTint = R.color.perf_stat_mileage
                }
                DriverScoringEngine.SuggestionIcon.TROPHY -> {
                    baseIconRes = R.drawable.ic_nav_performance
                    baseTint = R.color.suggestion_positive
                }
            }

            val bgColor = when (severity) {
                DriverScoringEngine.DrivingSuggestion.Severity.HIGH ->
                    R.color.suggestion_high_bg
                DriverScoringEngine.DrivingSuggestion.Severity.MEDIUM ->
                    R.color.suggestion_medium_bg
                DriverScoringEngine.DrivingSuggestion.Severity.LOW ->
                    R.color.suggestion_low_bg
                DriverScoringEngine.DrivingSuggestion.Severity.POSITIVE ->
                    R.color.suggestion_positive_bg
            }

            return Triple(baseIconRes, baseTint, bgColor)
        }

        /**
         * Get the color for a suggestion severity level.
         */
        private fun getSeverityColor(
            severity: DriverScoringEngine.DrivingSuggestion.Severity
        ): Int {
            return when (severity) {
                DriverScoringEngine.DrivingSuggestion.Severity.HIGH ->
                    R.color.suggestion_high
                DriverScoringEngine.DrivingSuggestion.Severity.MEDIUM ->
                    R.color.suggestion_medium
                DriverScoringEngine.DrivingSuggestion.Severity.LOW ->
                    R.color.suggestion_low
                DriverScoringEngine.DrivingSuggestion.Severity.POSITIVE ->
                    R.color.suggestion_positive
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DiffCallback
    // ═══════════════════════════════════════════════════════════

    class SuggestionDiffCallback :
        DiffUtil.ItemCallback<DriverScoringEngine.DrivingSuggestion>() {

        override fun areItemsTheSame(
            oldItem: DriverScoringEngine.DrivingSuggestion,
            newItem: DriverScoringEngine.DrivingSuggestion
        ): Boolean = oldItem.title == newItem.title

        override fun areContentsTheSame(
            oldItem: DriverScoringEngine.DrivingSuggestion,
            newItem: DriverScoringEngine.DrivingSuggestion
        ): Boolean = oldItem == newItem
    }
}
