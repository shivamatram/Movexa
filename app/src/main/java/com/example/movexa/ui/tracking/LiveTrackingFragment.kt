package com.example.movexa.ui.tracking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.movexa.R
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.databinding.FragmentLiveTrackingBinding
import com.example.movexa.ui.base.BaseFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

/**
 * Live vehicle tracking screen for customers / managers viewing a single vehicle.
 *
 * Features:
 *  ● Real-time vehicle marker with smooth position updates
 *  ● Route polyline showing vehicle's traveled path
 *  ● ETA badge with estimated arrival time and distance remaining
 *  ● Trip info (pickup / drop-off addresses)
 *  ● Driver and vehicle details
 *  ● Waiting state while vehicle hasn't started tracking
 *  ● Auto-center on vehicle button
 *
 * Arguments (passed via nav args or fragment arguments):
 *  ● companyId: String
 *  ● vehicleId: String
 *  ● tripId: String (optional)
 */
class LiveTrackingFragment : BaseFragment<FragmentLiveTrackingBinding>(
    FragmentLiveTrackingBinding::inflate
), OnMapReadyCallback {

    companion object {
        const val ARG_COMPANY_ID = "arg_company_id"
        const val ARG_VEHICLE_ID = "arg_vehicle_id"
        const val ARG_TRIP_ID = "arg_trip_id"
    }

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: LiveTrackingViewModel by viewModels()

    // ─── Map ────────────────────────────────────────────────────
    private var googleMap: GoogleMap? = null
    private var vehicleMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private var vehicleIcon: BitmapDescriptor? = null
    private var isMapReady = false
    private var isAutoCenter = true

    // ═══════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        viewModel.stopTracking()
        vehicleMarker = null
        routePolyline = null
        googleMap = null
        isMapReady = false
        super.onDestroyView()
    }

    // ═══════════════════════════════════════════════════════════
    //  BaseFragment Overrides
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        // Prepare marker icon
        vehicleIcon = createVehicleIcon()

        // Initialize map
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Show waiting state initially
        binding.cardWaiting.visibility = View.VISIBLE
        binding.cardEta.visibility = View.GONE
        binding.cardBottomInfo.visibility = View.GONE

        // Extract arguments and start tracking
        val companyId = arguments?.getString(ARG_COMPANY_ID) ?: ""
        val vehicleId = arguments?.getString(ARG_VEHICLE_ID) ?: ""
        val tripId = arguments?.getString(ARG_TRIP_ID) ?: ""

        if (companyId.isNotBlank() && vehicleId.isNotBlank()) {
            viewModel.startTracking(companyId, vehicleId, tripId)
        } else {
            showError("Missing tracking parameters")
        }
    }

    override fun setupListeners() {
        // ── Back Button ─────────────────────────────────────────
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // ── Center Vehicle Button ───────────────────────────────
        binding.btnCenterVehicle.setOnClickListener {
            isAutoCenter = true
            centerOnVehicle()
        }
    }

    override fun observeData() {
        // ── Vehicle Location ────────────────────────────────────
        collectLatestFlow(viewModel.vehicleLocation) { result ->
            when (result) {
                is ResultState.Success -> {
                    val location = result.data
                    if (location != null && location.isValid && isMapReady) {
                        updateVehicleMarker(location)
                        binding.cardWaiting.visibility = View.GONE
                    }
                }
                is ResultState.Loading -> {
                    binding.cardWaiting.visibility = View.VISIBLE
                }
                is ResultState.Error -> {
                    showError(result.message)
                }
                is ResultState.Idle -> { /* ignore */ }
            }
        }

        // ── Tracking Status ─────────────────────────────────────
        collectLatestFlow(viewModel.trackingStatus) { status ->
            when (status) {
                LiveTrackingViewModel.TrackingStatus.ACTIVE -> {
                    binding.tvTrackingStatus.text = getString(R.string.status_active)
                    binding.tvTrackingStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.tracking_moving)
                    )
                    binding.cardBottomInfo.visibility = View.VISIBLE
                    binding.cardWaiting.visibility = View.GONE
                }
                LiveTrackingViewModel.TrackingStatus.WAITING -> {
                    binding.tvTrackingStatus.text = getString(R.string.status_waiting)
                    binding.tvTrackingStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.tracking_idle)
                    )
                    binding.cardWaiting.visibility = View.VISIBLE
                }
                LiveTrackingViewModel.TrackingStatus.OFFLINE -> {
                    binding.tvTrackingStatus.text = getString(R.string.status_offline)
                    binding.tvTrackingStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.tracking_offline)
                    )
                    binding.cardBottomInfo.visibility = View.VISIBLE
                    binding.cardWaiting.visibility = View.GONE
                }
            }
        }

        // ── Status Message ──────────────────────────────────────
        collectLatestFlow(viewModel.statusMessage) { message ->
            binding.tvStatusMessage.text = message
        }

        // ── ETA ─────────────────────────────────────────────────
        collectLatestFlow(viewModel.etaMinutes) { eta ->
            if (eta != null) {
                binding.cardEta.visibility = View.VISIBLE
                binding.tvEta.text = viewModel.formatEta(eta)
            } else {
                binding.cardEta.visibility = View.GONE
            }
        }

        // ── Distance Remaining ──────────────────────────────────
        collectLatestFlow(viewModel.distanceRemainingKm) { distance ->
            if (distance != null) {
                binding.tvDistanceRemaining.text = getString(
                    R.string.distance_remaining_value, viewModel.formatDistance(distance)
                )
            }
        }

        // ── Route Polyline ──────────────────────────────────────
        collectLatestFlow(viewModel.routeHistory) { points ->
            if (isMapReady && points.size >= 2) {
                updateRoutePolyline(points)
            }
        }

        // ── Trip Details ────────────────────────────────────────
        collectLatestFlow(viewModel.tripDetails) { trip ->
            if (trip != null) {
                binding.tvPickupAddress.text = trip.pickupAddress.ifBlank {
                    "${trip.pickupLocation.latitude}, ${trip.pickupLocation.longitude}"
                }
                binding.tvDropAddress.text = trip.dropAddress.ifBlank {
                    "${trip.dropLocation.latitude}, ${trip.dropLocation.longitude}"
                }
            }
        }

        // ── Vehicle Details ─────────────────────────────────────
        collectLatestFlow(viewModel.vehicleDetails) { vehicle ->
            if (vehicle != null) {
                binding.tvLiveVehicleNumber.text = vehicle.number
                binding.tvTitle.text = getString(R.string.tracking_vehicle_title, vehicle.number)
            }
        }

        // ── Driver Details ──────────────────────────────────────
        collectLatestFlow(viewModel.driverDetails) { driver ->
            if (driver != null) {
                binding.tvLiveDriverName.text = driver.metadata["fullName"] as? String
                    ?: "Driver ${driver.driverId.takeLast(6)}"
            }
        }

        // ── Live Speed ──────────────────────────────────────────
        collectLatestFlow(viewModel.vehicleLocation) { result ->
            if (result is ResultState.Success && result.data != null) {
                binding.tvLiveSpeed.text = getString(
                    R.string.speed_value,
                    String.format("%.0f", result.data.speedKmh)
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Map Ready
    // ═══════════════════════════════════════════════════════════

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true

        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = true
            isMapToolbarEnabled = false
            isMyLocationButtonEnabled = false
            isRotateGesturesEnabled = true
        }

        // Default camera (India center)
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 5f)
        )

        // Disable auto-center when user manually moves map
        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                isAutoCenter = false
            }
        }

        // If location already available, show it
        val currentResult = viewModel.vehicleLocation.value
        if (currentResult is ResultState.Success && currentResult.data != null) {
            updateVehicleMarker(currentResult.data)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Vehicle Marker
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the vehicle marker position on the map.
     */
    private fun updateVehicleMarker(location: TrackingLocation) {
        val map = googleMap ?: return
        val position = LatLng(location.lat, location.lng)

        if (vehicleMarker == null) {
            vehicleMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(vehicleIcon)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .rotation(location.heading)
                    .title(getString(R.string.vehicle_marker_title))
            )

            // Center on first appearance
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(position, 15f)
            )
        } else {
            vehicleMarker?.position = position
            vehicleMarker?.rotation = location.heading
        }

        // Auto-center if enabled
        if (isAutoCenter) {
            map.animateCamera(CameraUpdateFactory.newLatLng(position))
        }
    }

    /**
     * Center camera on the vehicle.
     */
    private fun centerOnVehicle() {
        val map = googleMap ?: return
        val marker = vehicleMarker ?: return
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(marker.position, 15f)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  Route Polyline
    // ═══════════════════════════════════════════════════════════

    /**
     * Draw or update the route polyline from location history.
     */
    private fun updateRoutePolyline(points: List<LiveTrackingViewModel.LatLng>) {
        val map = googleMap ?: return
        val mapPoints = points.map { LatLng(it.lat, it.lng) }

        if (routePolyline == null) {
            val routeColor = ContextCompat.getColor(requireContext(), R.color.tracking_route_primary)
            routePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(mapPoints)
                    .width(8f)
                    .color(routeColor)
                    .geodesic(true)
            )
        } else {
            routePolyline?.points = mapPoints
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Marker Icon
    // ═══════════════════════════════════════════════════════════

    private fun createVehicleIcon(): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_vehicle_marker)
            ?: return null
        val color = ContextCompat.getColor(requireContext(), R.color.tracking_moving)
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
}
