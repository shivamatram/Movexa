package com.example.movexa.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.example.movexa.R
import com.example.movexa.data.model.Driver
import com.example.movexa.data.model.enums.VerificationStatus
import com.example.movexa.databinding.ItemDriverCardBinding

/**
 * Custom view component for rendering a driver card in the fleet list.
 *
 * Responsibilities:
 * - Binds Driver model data to layout
 * - Applies verification status colors (badge + dot)
 * - Shows/hides action buttons based on driver state and viewer role
 * - Exposes click callbacks for verify, block, assign vehicle, etc.
 */
class DriverCardView(context: Context) : LinearLayout(context) {

    private val binding: ItemDriverCardBinding

    // ── Click Callbacks ─────────────────────────────────────────
    var onVerifyClick: ((Driver) -> Unit)? = null
    var onRejectClick: ((Driver) -> Unit)? = null
    var onBlockClick: ((Driver) -> Unit)? = null
    var onUnblockClick: ((Driver) -> Unit)? = null
    var onAssignVehicleClick: ((Driver) -> Unit)? = null
    var onCardClick: ((Driver) -> Unit)? = null

    private var currentDriver: Driver? = null

    init {
        orientation = VERTICAL
        binding = ItemDriverCardBinding.inflate(LayoutInflater.from(context), this, true)
    }

    /**
     * Bind a Driver model to this card view.
     *
     * @param driver The Driver to display
     * @param driverName Display name (fetched from User model or fallback)
     * @param isAdmin Whether the viewer is admin (controls action visibility)
     */
    fun bind(driver: Driver, driverName: String = "Driver", isAdmin: Boolean = true) {
        currentDriver = driver

        // ── Header Information ──────────────────────────────────
        binding.tvDriverName.text = driverName

        // License + Blood Group info line
        val infoText = buildString {
            append(driver.licenseNumber)
            if (driver.bloodGroup.isNotBlank()) {
                append(" · ")
                append(driver.bloodGroup)
            }
        }
        binding.tvDriverInfo.text = infoText

        // ── Verification Badge ──────────────────────────────────
        applyVerificationStatus(driver)

        // ── Status Dot Color ────────────────────────────────────
        applyStatusDot(driver)

        // ── Verified Icon ───────────────────────────────────────
        binding.ivVerifiedBadge.visibility =
            if (driver.verificationStatus.isApproved() && !driver.blocked) View.VISIBLE else View.GONE

        // ── Stats Row ───────────────────────────────────────────
        binding.tvRating.text = String.format("%.1f", driver.rating)
        binding.tvTrips.text = driver.totalTrips.toString()

        if (!driver.assignedVehicleId.isNullOrBlank()) {
            binding.tvVehicle.text = driver.assignedVehicleId
            binding.tvVehicle.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        } else {
            binding.tvVehicle.text = context.getString(R.string.vehicle_unassigned)
            binding.tvVehicle.setTextColor(ContextCompat.getColor(context, R.color.text_hint))
        }

        // ── Action Buttons ──────────────────────────────────────
        setupActionButtons(driver, isAdmin)

        // ── Click Listeners ─────────────────────────────────────
        setupClickListeners(driver, isAdmin)
    }

    /**
     * Apply verification status to the badge.
     */
    private fun applyVerificationStatus(driver: Driver) {
        if (driver.blocked) {
            // Blocked overrides verification status display
            binding.tvStatusBadge.text = context.getString(R.string.filter_blocked)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_blocked))
            binding.tvStatusBadge.background?.setTint(ContextCompat.getColor(context, R.color.status_blocked_bg))
            return
        }

        val (textColorRes, bgColorRes, label) = when (driver.verificationStatus) {
            VerificationStatus.PENDING -> Triple(
                R.color.status_pending, R.color.status_pending_bg,
                context.getString(R.string.filter_pending)
            )
            VerificationStatus.UNDER_REVIEW -> Triple(
                R.color.status_under_review, R.color.status_under_review_bg,
                context.getString(R.string.filter_pending)
            )
            VerificationStatus.APPROVED -> Triple(
                R.color.status_approved, R.color.status_approved_bg,
                context.getString(R.string.filter_verified)
            )
            VerificationStatus.REJECTED -> Triple(
                R.color.status_rejected, R.color.status_rejected_bg,
                context.getString(R.string.fleet_reject_driver)
            )
            VerificationStatus.EXPIRED -> Triple(
                R.color.status_expired, R.color.status_expired_bg,
                "Expired"
            )
        }

        binding.tvStatusBadge.text = label
        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, textColorRes))
        binding.tvStatusBadge.background?.setTint(ContextCompat.getColor(context, bgColorRes))
    }

    /**
     * Apply color to the status dot indicator.
     */
    private fun applyStatusDot(driver: Driver) {
        val dotColor = when {
            driver.blocked -> R.color.status_blocked
            driver.verificationStatus.isApproved() -> R.color.status_approved
            driver.verificationStatus.needsAction() -> R.color.status_pending
            else -> R.color.status_inactive
        }

        val drawable = binding.viewStatusDot.background
        if (drawable is GradientDrawable) {
            drawable.setColor(ContextCompat.getColor(context, dotColor))
        } else {
            binding.viewStatusDot.background.setTint(ContextCompat.getColor(context, dotColor))
        }
    }

    /**
     * Show/hide action buttons based on driver state and viewer role.
     */
    private fun setupActionButtons(driver: Driver, isAdmin: Boolean) {
        binding.layoutActions.visibility = if (isAdmin) View.VISIBLE else View.GONE

        if (!isAdmin) return

        // Verify button: visible only if pending/under_review
        binding.btnVerify.visibility =
            if (driver.verificationStatus.needsAction() && !driver.blocked)
                View.VISIBLE
            else
                View.GONE

        // Block/Unblock button
        if (driver.blocked) {
            binding.btnBlock.text = context.getString(R.string.driver_action_unblock)
            binding.btnBlock.setIconResource(R.drawable.ic_check_circle)
            binding.btnBlock.visibility = View.VISIBLE
        } else if (driver.verificationStatus.isApproved()) {
            binding.btnBlock.text = context.getString(R.string.driver_action_block)
            binding.btnBlock.setIconResource(R.drawable.ic_block)
            binding.btnBlock.visibility = View.VISIBLE
        } else {
            binding.btnBlock.visibility = View.GONE
        }

        // Assign vehicle: visible only if approved & not blocked & no vehicle
        binding.btnAssignVehicle.visibility =
            if (driver.verificationStatus.isApproved() && !driver.blocked)
                View.VISIBLE
            else
                View.GONE
    }

    /**
     * Set up click listeners for the card and action buttons.
     */
    private fun setupClickListeners(driver: Driver, isAdmin: Boolean) {
        binding.cardDriver.setOnClickListener {
            onCardClick?.invoke(driver)
        }

        binding.btnVerify.setOnClickListener {
            onVerifyClick?.invoke(driver)
        }

        binding.btnBlock.setOnClickListener {
            if (driver.blocked) {
                onUnblockClick?.invoke(driver)
            } else {
                onBlockClick?.invoke(driver)
            }
        }

        binding.btnAssignVehicle.setOnClickListener {
            onAssignVehicleClick?.invoke(driver)
        }

        // Popup menu on overflow icon
        binding.ivMenu.setOnClickListener { anchor ->
            showPopupMenu(anchor, driver, isAdmin)
        }
    }

    /**
     * Show overflow popup menu with contextual actions.
     */
    private fun showPopupMenu(anchor: View, driver: Driver, isAdmin: Boolean) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
            if (isAdmin) {
                if (driver.verificationStatus.needsAction() && !driver.blocked) {
                    add(0, MENU_VERIFY, 0, R.string.fleet_verify_driver)
                    add(0, MENU_REJECT, 1, R.string.fleet_reject_driver)
                }
                if (driver.blocked) {
                    add(0, MENU_UNBLOCK, 2, R.string.fleet_unblock_driver)
                } else if (driver.verificationStatus.isApproved()) {
                    add(0, MENU_BLOCK, 3, R.string.fleet_block_driver)
                }
                if (driver.verificationStatus.isApproved() && !driver.blocked) {
                    add(0, MENU_ASSIGN_VEHICLE, 4, R.string.fleet_assign_vehicle)
                }
                if (!driver.assignedVehicleId.isNullOrBlank()) {
                    add(0, MENU_REMOVE_ASSIGNMENT, 5, R.string.fleet_remove_assignment)
                }
            }
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_VERIFY -> {
                    onVerifyClick?.invoke(driver)
                    true
                }
                MENU_REJECT -> {
                    onRejectClick?.invoke(driver)
                    true
                }
                MENU_BLOCK -> {
                    onBlockClick?.invoke(driver)
                    true
                }
                MENU_UNBLOCK -> {
                    onUnblockClick?.invoke(driver)
                    true
                }
                MENU_ASSIGN_VEHICLE -> {
                    onAssignVehicleClick?.invoke(driver)
                    true
                }
                MENU_REMOVE_ASSIGNMENT -> {
                    // Treat as unassign request via assign callback
                    onAssignVehicleClick?.invoke(driver)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    companion object {
        private const val MENU_VERIFY = 1
        private const val MENU_REJECT = 2
        private const val MENU_BLOCK = 3
        private const val MENU_UNBLOCK = 4
        private const val MENU_ASSIGN_VEHICLE = 5
        private const val MENU_REMOVE_ASSIGNMENT = 6
    }
}
