package com.example.movexa.ui.dashboard.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.databinding.ItemVehicleCostBinding
import com.example.movexa.service.AnalyticsEngine
import com.example.movexa.service.AnalyticsEngine.VehicleCostEntry

/**
 * VehicleCostAdapter — displays vehicle cost ranking with breakdown.
 *
 * ─── Features ────────────────────────────────────────────────────────────
 *  • Vehicle label and number in header row
 *  • Total cost displayed prominently
 *  • Fuel / service / repair breakdown in detail row
 *  • DiffUtil for efficient updates
 */
class VehicleCostAdapter :
    ListAdapter<VehicleCostEntry, VehicleCostAdapter.VehicleCostViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<VehicleCostEntry>() {
            override fun areItemsTheSame(old: VehicleCostEntry, new: VehicleCostEntry): Boolean =
                old.vehicleId == new.vehicleId

            override fun areContentsTheSame(old: VehicleCostEntry, new: VehicleCostEntry): Boolean =
                old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleCostViewHolder {
        val binding = ItemVehicleCostBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VehicleCostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VehicleCostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VehicleCostViewHolder(
        private val binding: ItemVehicleCostBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VehicleCostEntry) {
            val ctx = binding.root.context

            // Vehicle info
            binding.tvVehicleLabel.text = item.vehicleLabel
            binding.tvVehicleNumber.text = item.vehicleNumber

            // Total cost
            binding.tvTotalCost.text = item.totalCostDisplay

            // Cost breakdown
            binding.tvFuelCost.text = ctx.getString(
                R.string.analytics_cost_fuel,
                AnalyticsEngine.formatCurrency(item.fuelCost)
            )
            binding.tvServiceCost.text = ctx.getString(
                R.string.analytics_cost_service,
                AnalyticsEngine.formatCurrency(item.serviceCost)
            )
            binding.tvRepairCost.text = ctx.getString(
                R.string.analytics_cost_repair,
                AnalyticsEngine.formatCurrency(item.repairCost)
            )
        }
    }
}
