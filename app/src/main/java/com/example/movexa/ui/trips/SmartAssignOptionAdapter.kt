package com.example.movexa.ui.trips

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.R
import com.example.movexa.databinding.ItemSmartAssignOptionBinding

/**
 * RecyclerView adapter for the Smart Assignment bottom sheet.
 *
 * Displays a list of eligible vehicle+driver options with radio-button
 * single selection. Each item shows vehicle number, type/capacity,
 * assigned driver name, trip count, and proximity info (placeholder).
 */
class SmartAssignOptionAdapter :
    ListAdapter<SmartAssignOptionAdapter.EligibleOption, SmartAssignOptionAdapter.OptionViewHolder>(
        OptionDiffCallback()
    ) {

    /**
     * Data class representing an eligible vehicle+driver pair.
     */
    data class EligibleOption(
        val vehicleId: String,
        val driverId: String,
        val vehicleNumber: String,
        val vehicleTypeCapacity: String,
        val driverName: String,
        val tripCount: Int = 0,
        val proximity: String = ""
    )

    // ── Selection State ─────────────────────────────────────────

    private var selectedPosition: Int = -1

    /** Callback invoked when a selection changes. */
    var onSelectionChanged: ((EligibleOption?) -> Unit)? = null

    /** Get the currently selected option, or null. */
    fun getSelectedOption(): EligibleOption? {
        return if (selectedPosition in 0 until itemCount) getItem(selectedPosition) else null
    }

    // ── ViewHolder ──────────────────────────────────────────────

    inner class OptionViewHolder(
        private val binding: ItemSmartAssignOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(option: EligibleOption, position: Int) {
            binding.tvVehicleNumber.text = option.vehicleNumber
            binding.tvVehicleType.text = option.vehicleTypeCapacity
            binding.tvDriverName.text = option.driverName

            // Proximity / trip count info
            binding.tvProximity.text = if (option.proximity.isNotBlank())
                option.proximity else "—"
            binding.tvTripCount.text = binding.root.context.getString(
                R.string.smart_assign_trips_done, option.tripCount
            )

            // Radio button state
            val isSelected = position == selectedPosition
            binding.rbSelect.isChecked = isSelected

            // Highlight selected card
            val strokeColor = if (isSelected) R.color.primary else R.color.outline_variant
            (binding.root as? com.google.android.material.card.MaterialCardView)?.apply {
                this.strokeColor = androidx.core.content.ContextCompat.getColor(
                    context, strokeColor
                )
                this.strokeWidth = if (isSelected) 2 else 1
            }

            // Click handler — select this option
            binding.root.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = position

                if (previousPosition >= 0) notifyItemChanged(previousPosition)
                notifyItemChanged(position)

                onSelectionChanged?.invoke(option)
            }
        }
    }

    // ── Adapter Methods ─────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val binding = ItemSmartAssignOptionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    /** Reset selection state (e.g., when list changes). */
    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = -1
        if (previous >= 0) notifyItemChanged(previous)
        onSelectionChanged?.invoke(null)
    }

    // ── DiffUtil ────────────────────────────────────────────────

    class OptionDiffCallback : DiffUtil.ItemCallback<EligibleOption>() {
        override fun areItemsTheSame(oldItem: EligibleOption, newItem: EligibleOption): Boolean {
            return oldItem.vehicleId == newItem.vehicleId
        }

        override fun areContentsTheSame(oldItem: EligibleOption, newItem: EligibleOption): Boolean {
            return oldItem == newItem
        }
    }
}
