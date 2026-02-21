package com.example.movexa.ui.dashboard.manager

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.databinding.FragmentManagerTrackingBinding
import com.example.movexa.ui.base.BaseFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manager's real-time fleet tracking screen.
 *
 * Shows all company vehicles on a Google Map with live position updates.
 *  ● Markers color-coded: green = moving, orange = idle
 *  ● Smooth marker animation between position updates
 *  ● Filter chips: All / Moving / Idle
 *  ● Bottom panel shows detail card when a vehicle marker is tapped
 *  ● Summary bar with total / moving / idle counts
 *  ● Map type toggle (normal ↔ satellite)
 *  ● Center-all button fits all markers in view
 */
class ManagerTrackingFragment : BaseFragment<FragmentManagerTrackingBinding>(
    FragmentManagerTrackingBinding::inflate
), OnMapReadyCallback {

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: ManagerTrackingViewModel by viewModels()

    // ─── Map ────────────────────────────────────────────────────
    private var googleMap: GoogleMap? = null
    private val vehicleMarkers = mutableMapOf<String, Marker>()
    private val markerAnimators = mutableMapOf<String, ValueAnimator>()

    // ─── Icons ──────────────────────────────────────────────────
    private var movingIcon: BitmapDescriptor? = null
    private var idleIcon: BitmapDescriptor? = null
    private var isMapReady = false

    // ─── Date Formatter ─────────────────────────────────────────
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // ═══════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        // Cancel all marker animations to prevent leaks
        markerAnimators.values.forEach { it.cancel() }
        markerAnimators.clear()
        vehicleMarkers.clear()
        googleMap = null
        isMapReady = false
        super.onDestroyView()
    }

    // ═══════════════════════════════════════════════════════════
    //  BaseFragment Overrides
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        // Prepare marker icons
        movingIcon = createMarkerIcon(R.color.tracking_moving)
        idleIcon = createMarkerIcon(R.color.tracking_idle)

        // Initialize map fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Set initial chip selection
        binding.chipAll.isChecked = true

        // Start loading data (will render once map is ready)
        viewModel.loadTracking()
    }

    override fun setupListeners() {
        // ── Filter Chips ────────────────────────────────────────
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(R.id.chipMoving) -> ManagerTrackingViewModel.FilterMode.MOVING
                checkedIds.contains(R.id.chipIdle) -> ManagerTrackingViewModel.FilterMode.IDLE
                else -> ManagerTrackingViewModel.FilterMode.ALL
            }
            viewModel.setFilter(filter)
        }

        // ── Map Type Toggle ─────────────────────────────────────
        binding.btnMapType.setOnClickListener {
            googleMap?.let { map ->
                map.mapType = if (map.mapType == GoogleMap.MAP_TYPE_NORMAL) {
                    GoogleMap.MAP_TYPE_SATELLITE
                } else {
                    GoogleMap.MAP_TYPE_NORMAL
                }
            }
        }

        // ── Center All Vehicles ─────────────────────────────────
        binding.btnCenterAll.setOnClickListener {
            centerAllVehicles()
        }

        // ── Close Vehicle Detail Card ───────────────────────────
        binding.btnCloseDetail.setOnClickListener {
            viewModel.deselectVehicle()
        }
    }

    override fun observeData() {
        // ── Filtered Locations → update markers ─────────────────
        collectLatestFlow(viewModel.filteredLocations) { locations ->
            if (isMapReady) {
                updateMapMarkers(locations)
            }
        }

        // ── All Locations state → empty state / error ───────────
        collectLatestFlow(viewModel.allLocations) { result ->
            when (result) {
                is ResultState.Loading -> {
                    binding.cardEmptyState.visibility = View.GONE
                }
                is ResultState.Error -> {
                    showError(result.message)
                    if (vehicleMarkers.isEmpty()) {
                        binding.cardEmptyState.visibility = View.VISIBLE
                    }
                }
                is ResultState.Success -> {
                    binding.cardEmptyState.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is ResultState.Idle -> { /* ignore */ }
            }
        }

        // ── Summary Counts ──────────────────────────────────────
        collectLatestFlow(viewModel.totalCount) { count ->
            binding.tvTotalCount.text = getString(R.string.total_vehicles_count, count)
        }
        collectLatestFlow(viewModel.movingCount) { count ->
            binding.tvMovingCount.text = getString(R.string.moving_count, count)
        }
        collectLatestFlow(viewModel.idleCount) { count ->
            binding.tvIdleCount.text = getString(R.string.idle_count, count)
        }

        // ── Selected Vehicle Detail ─────────────────────────────
        collectLatestFlow(viewModel.selectedVehicleDetail) { detail ->
            if (detail != null) {
                showVehicleDetail(detail)
            } else {
                binding.cardVehicleDetail.visibility = View.GONE
            }
        }

        // ── Error Events ────────────────────────────────────────
        collectFlow(viewModel.errorEvent) { message ->
            showError(message)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Map Ready
    // ═══════════════════════════════════════════════════════════

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true

        // Configure map
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = true
            isMapToolbarEnabled = false
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = false
        }

        // Default camera position (India center)
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 5f)
        )

        // Marker click → select vehicle
        map.setOnMarkerClickListener { marker ->
            val vehicleId = marker.tag as? String
            if (vehicleId != null) {
                viewModel.selectVehicle(vehicleId)
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(marker.position, 15f)
                )
            }
            true
        }

        // Map click → deselect
        map.setOnMapClickListener {
            viewModel.deselectVehicle()
        }

        // If data already loaded, render markers
        val currentLocations = viewModel.filteredLocations.value
        if (currentLocations.isNotEmpty()) {
            updateMapMarkers(currentLocations)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Marker Management
    // ═══════════════════════════════════════════════════════════

    /**
     * Sync map markers with the latest locations.
     * - Add new markers for new vehicles
     * - Animate existing markers to new positions
     * - Remove markers for vehicles that went offline
     */
    private fun updateMapMarkers(locations: List<TrackingLocation>) {
        val map = googleMap ?: return
        val currentVehicleIds = locations.map { it.vehicleId }.toSet()

        // Remove markers for vehicles no longer in the list
        val removedIds = vehicleMarkers.keys - currentVehicleIds
        removedIds.forEach { id ->
            markerAnimators[id]?.cancel()
            markerAnimators.remove(id)
            vehicleMarkers[id]?.remove()
            vehicleMarkers.remove(id)
        }

        // Add or update markers
        for (location in locations) {
            val newPosition = LatLng(location.lat, location.lng)
            val icon = if (location.isMoving) movingIcon else idleIcon
            val vehicleNumber = viewModel.getVehicleNumber(location.vehicleId)

            val existingMarker = vehicleMarkers[location.vehicleId]
            if (existingMarker != null) {
                // Animate marker to new position
                animateMarker(existingMarker, newPosition, location.vehicleId)
                existingMarker.setIcon(icon)
                existingMarker.rotation = location.heading
                existingMarker.title = vehicleNumber
                existingMarker.snippet = if (location.isMoving) {
                    "${String.format("%.0f", location.speedKmh)} km/h"
                } else {
                    getString(R.string.filter_idle)
                }
            } else {
                // Create new marker
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(newPosition)
                        .icon(icon)
                        .title(vehicleNumber)
                        .snippet(
                            if (location.isMoving) "${String.format("%.0f", location.speedKmh)} km/h"
                            else getString(R.string.filter_idle)
                        )
                        .rotation(location.heading)
                        .anchor(0.5f, 0.5f)
                        .flat(true)
                )
                marker?.tag = location.vehicleId
                if (marker != null) {
                    vehicleMarkers[location.vehicleId] = marker
                }
            }
        }
    }

    /**
     * Smoothly animate a marker from its current position to [toPosition].
     * Uses linear interpolation over 1 second.
     */
    private fun animateMarker(marker: Marker, toPosition: LatLng, vehicleId: String) {
        // Cancel any running animation for this marker
        markerAnimators[vehicleId]?.cancel()

        val startPosition = marker.position
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val lat = startPosition.latitude +
                        (toPosition.latitude - startPosition.latitude) * fraction
                val lng = startPosition.longitude +
                        (toPosition.longitude - startPosition.longitude) * fraction
                marker.position = LatLng(lat, lng)
            }
        }

        markerAnimators[vehicleId] = animator
        animator.start()
    }

    // ═══════════════════════════════════════════════════════════
    //  Vehicle Detail Card
    // ═══════════════════════════════════════════════════════════

    private fun showVehicleDetail(detail: ManagerTrackingViewModel.VehicleDetail) {
        binding.cardVehicleDetail.visibility = View.VISIBLE

        // Vehicle info
        binding.tvVehicleNumber.text = detail.vehicleNumber
        binding.tvDriverName.text = detail.driverName

        // Status dot color
        val statusColor = if (detail.isMoving) {
            ContextCompat.getColor(requireContext(), R.color.tracking_moving)
        } else {
            ContextCompat.getColor(requireContext(), R.color.tracking_idle)
        }
        binding.viewStatusDot.background.setTint(statusColor)

        // Stats
        binding.tvSpeed.text = getString(
            R.string.speed_value, String.format("%.0f", detail.speed * 3.6f)
        )
        binding.tvHeading.text = formatHeading(detail.heading)
        binding.tvLastUpdate.text = timeFormatter.format(Date(detail.lastUpdate))

        // Trip info
        if (detail.tripId.isNotBlank()) {
            binding.tvTripInfo.visibility = View.VISIBLE
            binding.tvTripInfo.text = getString(R.string.trip_info_active, detail.tripId.takeLast(8))
        } else {
            binding.tvTripInfo.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Map Utilities
    // ═══════════════════════════════════════════════════════════

    /**
     * Fit all vehicle markers into the visible map area.
     */
    private fun centerAllVehicles() {
        val map = googleMap ?: return
        if (vehicleMarkers.isEmpty()) return

        val boundsBuilder = LatLngBounds.builder()
        vehicleMarkers.values.forEach { marker ->
            boundsBuilder.include(marker.position)
        }

        try {
            val bounds = boundsBuilder.build()
            val padding = 120 // px from edge
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        } catch (e: Exception) {
            // If only one marker, just zoom to it
            vehicleMarkers.values.firstOrNull()?.let { marker ->
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(marker.position, 15f)
                )
            }
        }
    }

    /**
     * Create a BitmapDescriptor from a vector drawable with a tint color.
     * Used for vehicle marker icons.
     */
    private fun createMarkerIcon(colorResId: Int): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_vehicle_marker)
            ?: return null
        val color = ContextCompat.getColor(requireContext(), colorResId)
        drawable.setTint(color)

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /**
     * Format heading in degrees to compass direction string.
     */
    private fun formatHeading(degrees: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((degrees + 22.5f) / 45f).toInt() % 8
        return "${degrees.toInt()}° ${directions[index]}"
    }
}
