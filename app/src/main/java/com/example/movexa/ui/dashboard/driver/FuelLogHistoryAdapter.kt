package com.example.movexa.ui.dashboard.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.FuelLog
import com.example.movexa.databinding.ItemFuelLogBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══════════════════════════════════════════════════════════════
 *  FUEL LOG HISTORY ADAPTER
 * ═══════════════════════════════════════════════════════════════
 *
 * ListAdapter backed by DiffUtil for displaying recent fuel
 * log entries in a RecyclerView. Each card shows:
 *
 * ● Date & time of fueling
 * ● Station name (if available)
 * ● Mileage badge with quality-based colour
 * ● Quantity (litres), total cost, and odometer reading
 *
 * Colour-coding:
 *   - Green  → good/excellent mileage ( > 10 km/L)
 *   - Amber  → average mileage (5–10 km/L)
 *   - Red    → poor/suspicious mileage (< 5 km/L)
 *
 * ═══════════════════════════════════════════════════════════════
 */
class FuelLogHistoryAdapter :
    ListAdapter<FuelLog, FuelLogHistoryAdapter.FuelLogViewHolder>(FuelLogDiffCallback()) {

    // ── Click callback ──────────────────────────────────────────
    var onItemClick: ((FuelLog) -> Unit)? = null

    // ── Formatter instances ─────────────────────────────────────
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val numberFormatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    // ═══════════════════════════════════════════════════════════
    //  ADAPTER OVERRIDES
    // ═══════════════════════════════════════════════════════════

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FuelLogViewHolder {
        val binding = ItemFuelLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FuelLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FuelLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ═══════════════════════════════════════════════════════════
    //  VIEW HOLDER
    // ═══════════════════════════════════════════════════════════

    inner class FuelLogViewHolder(
        private val binding: ItemFuelLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(getItem(position))
                }
            }
        }

        fun bind(fuelLog: FuelLog) {
            val context = binding.root.context

            // ── Date ────────────────────────────────────────────
            binding.tvLogDate.text = dateFormatter.format(Date(fuelLog.timestamp))

            // ── Station Name ────────────────────────────────────
            if (fuelLog.stationName.isNotBlank()) {
                binding.tvLogStation.text = fuelLog.stationName
                binding.tvLogStation.visibility = View.VISIBLE
            } else {
                binding.tvLogStation.visibility = View.GONE
            }

            // ── Mileage Badge ───────────────────────────────────
            if (fuelLog.mileage > 0) {
                val mileageText = "%.1f km/L".format(fuelLog.mileage)
                binding.tvLogMileageBadge.text = mileageText
                binding.tvLogMileageBadge.visibility = View.VISIBLE

                // Colour based on mileage quality
                val (textColor, bgColor) = getMileageColors(fuelLog.mileage)
                binding.tvLogMileageBadge.setTextColor(
                    ContextCompat.getColor(context, textColor)
                )
                binding.tvLogMileageBadge.background?.setTint(
                    ContextCompat.getColor(context, bgColor)
                )
            } else {
                binding.tvLogMileageBadge.text = context.getString(R.string.fuel_log_no_mileage)
                binding.tvLogMileageBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.text_hint)
                )
                binding.tvLogMileageBadge.background?.setTint(
                    ContextCompat.getColor(context, R.color.surface_variant)
                )
                binding.tvLogMileageBadge.visibility = View.VISIBLE
            }

            // ── Quantity ────────────────────────────────────────
            binding.tvLogQuantity.text = "%.1f L".format(fuelLog.quantity)

            // ── Cost ────────────────────────────────────────────
            binding.tvLogCost.text = fuelLog.costDisplay

            // ── Odometer ────────────────────────────────────────
            binding.tvLogOdometer.text = "%s km".format(
                numberFormatter.format(fuelLog.odometer)
            )
        }

        /**
         * Returns (textColor, backgroundColor) resource IDs
         * based on mileage value quality.
         */
        private fun getMileageColors(mileage: Double): Pair<Int, Int> {
            return when {
                mileage >= 15.0 -> Pair(
                    R.color.fuel_log_mileage_good,
                    R.color.mileage_good_bg
                )
                mileage >= 8.0 -> Pair(
                    R.color.fuel_log_mileage_avg,
                    R.color.mileage_average_bg
                )
                mileage >= 5.0 -> Pair(
                    R.color.fuel_log_mileage_avg,
                    R.color.mileage_below_average_bg
                )
                else -> Pair(
                    R.color.fuel_log_mileage_poor,
                    R.color.mileage_poor_bg
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DIFF UTIL
    // ═══════════════════════════════════════════════════════════

    class FuelLogDiffCallback : DiffUtil.ItemCallback<FuelLog>() {
        override fun areItemsTheSame(oldItem: FuelLog, newItem: FuelLog): Boolean {
            return oldItem.fuelId == newItem.fuelId
        }

        override fun areContentsTheSame(oldItem: FuelLog, newItem: FuelLog): Boolean {
            return oldItem == newItem
        }
    }
}
