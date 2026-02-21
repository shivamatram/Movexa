package com.example.movexa.ui.dashboard.mechanic

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.PartHistory
import com.example.movexa.databinding.ItemPartRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for displaying part replacement history.
 * Shows part name, odometer, expected life, usage progress, and status.
 */
class PartRecordAdapter(
    private val currentOdometer: Long = 0L
) : ListAdapter<PartHistory, PartRecordAdapter.PartViewHolder>(PartDiffCallback()) {

    private var _currentOdometer: Long = currentOdometer

    fun updateCurrentOdometer(odometer: Long) {
        _currentOdometer = odometer
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartViewHolder {
        val binding = ItemPartRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PartViewHolder(
        private val binding: ItemPartRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(part: PartHistory) {
            val ctx = itemView.context

            binding.tvPartName.text = part.partName
            binding.tvInstalledAt.text = ctx.getString(
                R.string.maint_service_at, part.changedAtKm
            )
            binding.tvExpectedLife.text = ctx.getString(
                R.string.maint_part_life_display, part.expectedLifeKm
            )
            binding.tvDate.text = if (part.date > 0) {
                dateFormat.format(Date(part.date))
            } else {
                "—"
            }

            // Cost
            binding.tvCost.text = if (part.cost > 0) {
                "₹%,.0f".format(part.cost)
            } else {
                "—"
            }

            // Usage progress
            if (_currentOdometer > 0 && part.expectedLifeKm > 0) {
                val usagePercent = part.usagePercent(_currentOdometer)
                val remainingKm = part.remainingLifeKm(_currentOdometer)

                binding.progressUsage.progress = usagePercent.toInt().coerceIn(0, 100)
                binding.tvUsagePercent.text = ctx.getString(
                    R.string.maint_part_usage, usagePercent
                )

                if (remainingKm > 0) {
                    binding.tvRemaining.text = ctx.getString(
                        R.string.maint_part_remaining, remainingKm
                    )
                } else {
                    binding.tvRemaining.text = ctx.getString(R.string.maint_part_expired)
                }

                // Color based on usage
                val progressColor = when {
                    usagePercent >= 100f -> ContextCompat.getColor(ctx, R.color.maint_progress_critical)
                    usagePercent >= 75f -> ContextCompat.getColor(ctx, R.color.maint_progress_warn)
                    else -> ContextCompat.getColor(ctx, R.color.maint_progress_safe)
                }
                binding.progressUsage.setIndicatorColor(progressColor)

                binding.progressUsage.visibility = android.view.View.VISIBLE
                binding.tvUsagePercent.visibility = android.view.View.VISIBLE
                binding.tvRemaining.visibility = android.view.View.VISIBLE
            } else {
                binding.progressUsage.visibility = android.view.View.GONE
                binding.tvUsagePercent.visibility = android.view.View.GONE
                binding.tvRemaining.visibility = android.view.View.GONE
            }

            // Brand info
            if (part.brand.isNotBlank()) {
                binding.tvBrand.text = part.brand
                binding.tvBrand.visibility = android.view.View.VISIBLE
            } else {
                binding.tvBrand.visibility = android.view.View.GONE
            }
        }
    }

    private class PartDiffCallback : DiffUtil.ItemCallback<PartHistory>() {
        override fun areItemsTheSame(oldItem: PartHistory, newItem: PartHistory): Boolean =
            oldItem.partId == newItem.partId

        override fun areContentsTheSame(oldItem: PartHistory, newItem: PartHistory): Boolean =
            oldItem == newItem
    }
}
