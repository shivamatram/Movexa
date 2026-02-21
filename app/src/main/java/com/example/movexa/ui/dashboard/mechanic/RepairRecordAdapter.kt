package com.example.movexa.ui.dashboard.mechanic

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.Repair
import com.example.movexa.databinding.ItemRepairRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for displaying repair record history.
 * Shows issue, repair action, cost, odometer, and parts replaced.
 */
class RepairRecordAdapter :
    ListAdapter<Repair, RepairRecordAdapter.RepairViewHolder>(RepairDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepairViewHolder {
        val binding = ItemRepairRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RepairViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RepairViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RepairViewHolder(
        private val binding: ItemRepairRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(repair: Repair) {
            binding.tvIssue.text = repair.issue
            binding.tvRepairDone.text = repair.repairDone
            binding.tvCost.text = if (repair.cost > 0) {
                "₹%,.0f".format(repair.cost)
            } else {
                "—"
            }
            binding.tvOdometer.text = if (repair.odometer > 0) {
                itemView.context.getString(R.string.maint_service_at, repair.odometer)
            } else {
                "—"
            }
            binding.tvDate.text = if (repair.date > 0) {
                dateFormat.format(Date(repair.date))
            } else {
                "—"
            }

            // Parts replaced
            if (repair.partsReplaced.isNotEmpty()) {
                binding.tvParts.text = repair.partsReplaced.joinToString(", ")
                binding.tvParts.visibility = android.view.View.VISIBLE
                binding.tvPartsLabel.visibility = android.view.View.VISIBLE
            } else {
                binding.tvParts.visibility = android.view.View.GONE
                binding.tvPartsLabel.visibility = android.view.View.GONE
            }

            // Workshop
            if (repair.workshopName.isNotBlank()) {
                binding.tvWorkshop.text = repair.workshopName
                binding.tvWorkshop.visibility = android.view.View.VISIBLE
            } else {
                binding.tvWorkshop.visibility = android.view.View.GONE
            }

            // Warranty indicator
            if (repair.isUnderWarranty()) {
                binding.tvWarranty.text = "Under Warranty"
                binding.tvWarranty.visibility = android.view.View.VISIBLE
            } else {
                binding.tvWarranty.visibility = android.view.View.GONE
            }
        }
    }

    private class RepairDiffCallback : DiffUtil.ItemCallback<Repair>() {
        override fun areItemsTheSame(oldItem: Repair, newItem: Repair): Boolean =
            oldItem.repairId == newItem.repairId

        override fun areContentsTheSame(oldItem: Repair, newItem: Repair): Boolean =
            oldItem == newItem
    }
}
