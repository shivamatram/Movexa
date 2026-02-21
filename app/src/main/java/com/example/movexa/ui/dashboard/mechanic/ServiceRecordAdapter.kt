package com.example.movexa.ui.dashboard.mechanic

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.data.model.ServiceRecord
import com.example.movexa.databinding.ItemServiceRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for displaying service record history.
 * Shows service type, odometer, next due km, cost, and date.
 */
class ServiceRecordAdapter :
    ListAdapter<ServiceRecord, ServiceRecordAdapter.ServiceViewHolder>(ServiceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemServiceRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServiceViewHolder(
        private val binding: ItemServiceRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(service: ServiceRecord) {
            binding.tvServiceType.text = service.serviceType.displayName
            binding.tvOdometer.text = itemView.context.getString(
                R.string.maint_service_at, service.odometer
            )
            binding.tvNextDue.text = if (service.nextServiceKm > 0) {
                itemView.context.getString(R.string.maint_next_due, service.nextServiceKm)
            } else {
                "—"
            }
            binding.tvCost.text = if (service.cost > 0) {
                "₹%,.0f".format(service.cost)
            } else {
                "—"
            }
            binding.tvDate.text = if (service.date > 0) {
                dateFormat.format(Date(service.date))
            } else {
                "—"
            }

            // Completed status indicator
            val statusColor = if (service.completed) {
                ContextCompat.getColor(itemView.context, R.color.maint_ok)
            } else {
                ContextCompat.getColor(itemView.context, R.color.maint_due_soon)
            }
            binding.viewStatusIndicator.setBackgroundColor(statusColor)

            // Workshop info
            if (service.workshopName.isNotBlank()) {
                binding.tvWorkshop.text = service.workshopName
                binding.tvWorkshop.visibility = android.view.View.VISIBLE
            } else {
                binding.tvWorkshop.visibility = android.view.View.GONE
            }
        }
    }

    private class ServiceDiffCallback : DiffUtil.ItemCallback<ServiceRecord>() {
        override fun areItemsTheSame(oldItem: ServiceRecord, newItem: ServiceRecord): Boolean =
            oldItem.serviceId == newItem.serviceId

        override fun areContentsTheSame(oldItem: ServiceRecord, newItem: ServiceRecord): Boolean =
            oldItem == newItem
    }
}
