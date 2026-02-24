package com.example.movexa.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.example.movexa.R
import com.example.movexa.data.model.Vehicle
import com.example.movexa.data.model.enums.VehicleStatus
import com.example.movexa.databinding.ItemVehicleCardBinding

/**
 * Custom view component for rendering a vehicle card in the fleet list.
 *
 * Responsibilities:
 * - Binds Vehicle model data to layout
 * - Applies status-specific colors to badge
 * - Shows/hides action buttons based on vehicle state
 * - Exposes click callbacks for edit, delete, status change, driver assignment
 */
class VehicleCardView(context: Context) : LinearLayout(context) {

    private val binding: ItemVehicleCardBinding

    // ── Click Callbacks ─────────────────────────────────────────
    var onEditClick: ((Vehicle) -> Unit)? = null
    var onDeleteClick: ((Vehicle) -> Unit)? = null
    var onStatusChangeClick: ((Vehicle) -> Unit)? = null
    var onAssignDriverClick: ((Vehicle) -> Unit)? = null
    var onToggleDocsClick: ((Vehicle) -> Unit)? = null    // new callback for marking docs valid/invalid
    var onCardClick: ((Vehicle) -> Unit)? = null

    private var currentVehicle: Vehicle? = null

    init {
        orientation = VERTICAL
        binding = ItemVehicleCardBinding.inflate(LayoutInflater.from(context), this, true)
    }

    /**
     * Bind a Vehicle model to this card view.
     * Updates all visual elements: number, type, status badge, capacity, driver, docs.
     *
     * @param vehicle The Vehicle to display
     * @param isAdmin Whether the viewer is admin (shows edit/delete actions)
     */
    fun bind(vehicle: Vehicle, isAdmin: Boolean = true) {
        currentVehicle = vehicle

        // ── Header Information ──────────────────────────────────
        binding.tvVehicleNumber.text = vehicle.number
        binding.tvVehicleDetails.text = buildDetailsText(vehicle)

        // ── Status Badge ────────────────────────────────────────
        applyStatusBadge(vehicle.status)

        // ── Info Row ────────────────────────────────────────────
        binding.tvCapacity.text = vehicle.capacity.toString()

        // Driver assignment
        if (!vehicle.assignedDriverId.isNullOrBlank()) {
            binding.tvDriver.text = context.getString(R.string.status_available)
            binding.tvDriver.setTextColor(ContextCompat.getColor(context, R.color.status_approved))
            binding.btnAssignDriver.text = context.getString(R.string.assignment_unassign)
        } else {
            binding.tvDriver.text = context.getString(R.string.vehicle_unassigned)
            binding.tvDriver.setTextColor(ContextCompat.getColor(context, R.color.text_hint))
            binding.btnAssignDriver.text = context.getString(R.string.vehicle_assign_driver)
        }

        // Document validity
        if (vehicle.documentsValid) {
            binding.tvDocs.text = context.getString(R.string.vehicle_docs_valid)
            binding.tvDocs.setTextColor(ContextCompat.getColor(context, R.color.success))
        } else {
            binding.tvDocs.text = context.getString(R.string.vehicle_docs_invalid)
            binding.tvDocs.setTextColor(ContextCompat.getColor(context, R.color.error))
        }

        // ── Action Buttons ──────────────────────────────────────
        if (isAdmin) {
            binding.layoutActions.visibility = View.VISIBLE
            binding.btnEditVehicle.visibility = View.VISIBLE
            binding.btnAssignDriver.visibility = View.VISIBLE
        } else {
            binding.layoutActions.visibility = View.VISIBLE
            binding.btnEditVehicle.visibility = View.GONE
            binding.btnAssignDriver.visibility = View.GONE
        }

        // ── Click Listeners ─────────────────────────────────────
        setupClickListeners(vehicle, isAdmin)
    }

    /**
     * Build the secondary details text: "Type · Make Model Year"
     */
    private fun buildDetailsText(vehicle: Vehicle): String {
        val parts = mutableListOf(vehicle.type.displayName)
        val makeModel = buildString {
            if (vehicle.make.isNotBlank()) append(vehicle.make)
            if (vehicle.model.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(vehicle.model)
            }
            if (vehicle.year > 0) {
                if (isNotEmpty()) append(" ")
                append(vehicle.year)
            }
        }
        if (makeModel.isNotBlank()) parts.add(makeModel)
        return parts.joinToString(" · ")
    }

    /**
     * Apply status-specific colors and text to the status badge.
     */
    private fun applyStatusBadge(status: VehicleStatus) {
        val (textColorRes, bgColorRes, labelRes) = when (status) {
            VehicleStatus.AVAILABLE -> Triple(
                R.color.status_available, R.color.status_available_bg, R.string.status_available
            )
            VehicleStatus.ON_TRIP -> Triple(
                R.color.status_on_trip, R.color.status_on_trip_bg, R.string.status_on_trip
            )
            VehicleStatus.SERVICE -> Triple(
                R.color.status_service, R.color.status_service_bg, R.string.status_in_service
            )
            VehicleStatus.INACTIVE -> Triple(
                R.color.status_inactive, R.color.status_inactive_bg, R.string.status_inactive
            )
        }

        binding.tvStatusBadge.text = context.getString(labelRes)
        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, textColorRes))
        binding.tvStatusBadge.background.setTint(ContextCompat.getColor(context, bgColorRes))
    }

    /**
     * Set up click listeners for all interactive elements.
     */
    private fun setupClickListeners(vehicle: Vehicle, isAdmin: Boolean) {
        binding.cardVehicle.setOnClickListener {
            onCardClick?.invoke(vehicle)
        }

        binding.btnEditVehicle.setOnClickListener {
            onEditClick?.invoke(vehicle)
        }

        binding.btnAssignDriver.setOnClickListener {
            onAssignDriverClick?.invoke(vehicle)
        }

        // Popup menu on the overflow icon
        binding.ivMenu.setOnClickListener { anchor ->
            showPopupMenu(anchor, vehicle, isAdmin)
        }
    }

    /**
     * Show the overflow popup menu with contextual options.
     */
    private fun showPopupMenu(anchor: View, vehicle: Vehicle, isAdmin: Boolean) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
            if (isAdmin) {
                add(0, MENU_EDIT, 0, R.string.action_edit)
                add(0, MENU_CHANGE_STATUS, 1, R.string.action_change_status)
                add(0, MENU_TOGGLE_DOCS, 2,
                    if (vehicle.documentsValid) R.string.action_mark_docs_invalid
                    else R.string.action_mark_docs_valid)
                add(0, MENU_DELETE, 3, R.string.action_delete)
            } else {
                add(0, MENU_CHANGE_STATUS, 0, R.string.action_change_status)
            }
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT -> {
                    onEditClick?.invoke(vehicle)
                    true
                }
                MENU_CHANGE_STATUS -> {
                    onStatusChangeClick?.invoke(vehicle)
                    true
                }
                MENU_TOGGLE_DOCS -> {
                    onToggleDocsClick?.invoke(vehicle)
                    true
                }
                MENU_DELETE -> {
                    onDeleteClick?.invoke(vehicle)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_CHANGE_STATUS = 2
        private const val MENU_TOGGLE_DOCS = 3 // new menu id
        private const val MENU_DELETE = 4
    }
}
