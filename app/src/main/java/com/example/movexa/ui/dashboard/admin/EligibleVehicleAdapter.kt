package com.example.movexa.ui.dashboard.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.databinding.ItemEligibleVehicleBinding

/**
 * Adapter for eligible vehicle selection in the reassign driver bottom sheet.
 */
class EligibleVehicleAdapter(
    private val onVehicleSelected: (AdminTripsViewModel.EligibleVehicle) -> Unit
) : ListAdapter<AdminTripsViewModel.EligibleVehicle, EligibleVehicleAdapter.VehicleViewHolder>(DiffCallback()) {

    private var selectedPosition = RecyclerView.NO_POSITION

    val selectedVehicle: AdminTripsViewModel.EligibleVehicle?
        get() = if (selectedPosition in 0 until itemCount) getItem(selectedPosition) else null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val binding = ItemEligibleVehicleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VehicleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    fun clearSelection() {
        val prev = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (prev in 0 until itemCount) notifyItemChanged(prev)
    }

    inner class VehicleViewHolder(
        private val binding: ItemEligibleVehicleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.cardVehicle.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val prev = selectedPosition
                    selectedPosition = pos
                    if (prev in 0 until itemCount) notifyItemChanged(prev)
                    notifyItemChanged(pos)
                    onVehicleSelected(getItem(pos))
                }
            }
        }

        fun bind(vehicle: AdminTripsViewModel.EligibleVehicle, isSelected: Boolean) {
            binding.tvVehicleNumber.text = vehicle.vehicleNumber
            binding.tvVehicleType.text = vehicle.vehicleType
            binding.tvDriverName.text = vehicle.driverName.ifBlank { "No driver" }
            binding.rbSelect.isChecked = isSelected

            val strokeColor = if (isSelected) R.color.primary else R.color.outline_variant
            binding.cardVehicle.strokeColor = ContextCompat.getColor(
                binding.root.context, strokeColor
            )
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AdminTripsViewModel.EligibleVehicle>() {
        override fun areItemsTheSame(
            oldItem: AdminTripsViewModel.EligibleVehicle,
            newItem: AdminTripsViewModel.EligibleVehicle
        ): Boolean = oldItem.vehicleId == newItem.vehicleId

        override fun areContentsTheSame(
            oldItem: AdminTripsViewModel.EligibleVehicle,
            newItem: AdminTripsViewModel.EligibleVehicle
        ): Boolean = oldItem == newItem
    }
}
