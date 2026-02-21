package com.example.movexa.ui.trips

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movexa.data.model.Trip

/**
 * RecyclerView adapter that wraps [TripCardView] for each trip item.
 *
 * Supports:
 * - DiffUtil for efficient list updates
 * - Manager vs. driver action modes
 * - Dynamic vehicle/driver name resolution via callbacks
 * - Click callbacks for primary, secondary, view-details, menu actions
 *
 * @param isManager True to show manager actions; false for driver actions.
 */
class TripListAdapter(
    private val isManager: Boolean
) : ListAdapter<Trip, TripListAdapter.TripViewHolder>(TripDiffCallback()) {

    // ── Callbacks ───────────────────────────────────────────────

    /** Resolve a vehicle number from vehicleId. */
    var vehicleNameResolver: ((String) -> String?)? = null

    /** Resolve a driver name from driverId. */
    var driverNameResolver: ((String) -> String?)? = null

    /** Primary action button clicked (e.g., Assign, Accept, Start, Complete). */
    var onPrimaryClick: ((Trip) -> Unit)? = null

    /** Secondary action button clicked (e.g., Cancel, Reject). */
    var onSecondaryClick: ((Trip) -> Unit)? = null

    /** View details button clicked. */
    var onViewDetailsClick: ((Trip) -> Unit)? = null

    /** Card body clicked. */
    var onCardClick: ((Trip) -> Unit)? = null

    /** Menu button clicked. */
    var onMenuClick: ((Trip) -> Unit)? = null

    // ── Adapter Overrides ───────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val cardView = TripCardView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return TripViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val trip = getItem(position)
        val cardView = holder.cardView

        // Resolve names
        val vehicleNumber = if (trip.vehicleId.isNotBlank())
            vehicleNameResolver?.invoke(trip.vehicleId) else null
        val driverName = if (trip.driverId.isNotBlank())
            driverNameResolver?.invoke(trip.driverId) else null

        // Bind data
        cardView.bind(trip, isManager, vehicleNumber, driverName)

        // Wire callbacks
        cardView.onPrimaryClick = { onPrimaryClick?.invoke(it) }
        cardView.onSecondaryClick = { onSecondaryClick?.invoke(it) }
        cardView.onViewDetailsClick = { onViewDetailsClick?.invoke(it) }
        cardView.onCardClick = { onCardClick?.invoke(it) }
        cardView.onMenuClick = { onMenuClick?.invoke(it) }
    }

    // ── ViewHolder ──────────────────────────────────────────────

    class TripViewHolder(val cardView: TripCardView) : RecyclerView.ViewHolder(cardView)

    // ── DiffUtil ────────────────────────────────────────────────

    class TripDiffCallback : DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip): Boolean {
            return oldItem.tripId == newItem.tripId
        }

        override fun areContentsTheSame(oldItem: Trip, newItem: Trip): Boolean {
            return oldItem == newItem
        }
    }
}
