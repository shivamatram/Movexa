package com.example.movexa.ui.dashboard.driver

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.example.movexa.R
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.databinding.FragmentDriverNavigationBinding
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

/**
 * Driver's GPS navigation / tracking screen.
 *
 * Features:
 *  ● Toggle foreground tracking service on / off
 *  ● Show current location on Google Map with custom marker
 *  ● Display real-time speed, heading, accuracy, and duration
 *  ● GPS signal quality indicator
 *  ● Runtime location permission handling (fine + background)
 *  ● Active trip display when one is assigned
 */
class DriverNavigationFragment : BaseFragment<FragmentDriverNavigationBinding>(
    FragmentDriverNavigationBinding::inflate
), OnMapReadyCallback {

    // ─── ViewModel ──────────────────────────────────────────────
    private val viewModel: DriverNavigationViewModel by viewModels()

    // ─── Map ────────────────────────────────────────────────────
    private var googleMap: GoogleMap? = null
    private var myMarker: Marker? = null
    private var myMarkerIcon: BitmapDescriptor? = null
    private var isMapReady = false
    private var hasCenteredOnce = false

    // ─── Permission Launcher ────────────────────────────────────
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            // Fine location granted — hide permission overlay
            binding.cardPermission.visibility = View.GONE
            binding.bottomPanel.visibility = View.VISIBLE
            binding.cardTopStatus.visibility = View.VISIBLE

            // Request background location separately if needed (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationIfNeeded()
            }
        } else {
            // Permission denied — show permission overlay
            binding.cardPermission.visibility = View.VISIBLE
            binding.bottomPanel.visibility = View.GONE
            showError(getString(R.string.permission_denied_message))
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showInfo(getString(R.string.background_location_rationale))
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        myMarker = null
        googleMap = null
        isMapReady = false
        super.onDestroyView()
    }

    // ═══════════════════════════════════════════════════════════
    //  BaseFragment Overrides
    // ═══════════════════════════════════════════════════════════

    override fun initViews() {
        // Prepare marker icon
        myMarkerIcon = createMarkerIcon()

        // Initialize map
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Check permission state
        if (hasLocationPermission()) {
            binding.cardPermission.visibility = View.GONE
            binding.bottomPanel.visibility = View.VISIBLE
            binding.cardTopStatus.visibility = View.VISIBLE
        } else {
            binding.cardPermission.visibility = View.VISIBLE
            binding.bottomPanel.visibility = View.GONE
            binding.cardTopStatus.visibility = View.GONE
        }

        // Initialize ViewModel
        viewModel.initialize()
    }

    override fun setupListeners() {
        // ── Tracking Toggle Button ──────────────────────────────
        binding.btnTrackingToggle.setOnClickListener {
            if (!hasLocationPermission()) {
                requestLocationPermissions()
                return@setOnClickListener
            }
            viewModel.toggleTracking()
        }

        // ── Grant Permission Button ─────────────────────────────
        binding.btnGrantPermission.setOnClickListener {
            requestLocationPermissions()
        }

        // ── My Location FAB ─────────────────────────────────────
        binding.fabMyLocation.setOnClickListener {
            centerOnMyLocation()
        }
    }

    override fun observeData() {
        // ── Tracking Active State ───────────────────────────────
        collectLatestFlow(viewModel.isTracking) { isTracking ->
            updateTrackingButton(isTracking)
        }

        // ── Last Location → update map + stats ──────────────────
        collectLatestFlow(viewModel.lastLocation) { location ->
            if (location != null && isMapReady) {
                updateMyMarker(location)
                updateStats(location)
            }
        }

        // ── GPS Status ──────────────────────────────────────────
        collectLatestFlow(viewModel.gpsStatus) { status ->
            updateGpsIndicator(status)
        }

        // ── Tracking Duration ───────────────────────────────────
        collectLatestFlow(viewModel.trackingDuration) { seconds ->
            binding.tvDuration.text = viewModel.formatDuration(seconds)
        }

        // ── Active Trip ─────────────────────────────────────────
        collectLatestFlow(viewModel.activeTrip) { trip ->
            if (trip != null) {
                binding.layoutTripInfo.visibility = View.VISIBLE
                binding.tvActiveTripId.text = getString(
                    R.string.active_trip_label, trip.tripId.takeLast(8)
                )
            } else {
                binding.layoutTripInfo.visibility = View.GONE
            }
        }

        // ── Error Events ────────────────────────────────────────
        collectFlow(viewModel.errorEvent) { message ->
            showError(message)
        }

        // ── Permission Request Events ───────────────────────────
        collectFlow(viewModel.requestPermissions) {
            requestLocationPermissions()
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

        // If location already available, show it
        viewModel.lastLocation.value?.let { location ->
            updateMyMarker(location)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Map Marker
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the driver's location marker on the map.
     */
    private fun updateMyMarker(location: TrackingLocation) {
        val map = googleMap ?: return
        val position = LatLng(location.lat, location.lng)

        if (myMarker == null) {
            myMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(myMarkerIcon)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .rotation(location.heading)
                    .title(getString(R.string.my_location_marker_title))
            )
        } else {
            myMarker?.position = position
            myMarker?.rotation = location.heading
        }

        // Center camera on first valid fix
        if (!hasCenteredOnce && location.isValid) {
            hasCenteredOnce = true
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(position, 16f)
            )
        }
    }

    /**
     * Center camera on current position.
     */
    private fun centerOnMyLocation() {
        val map = googleMap ?: return
        val marker = myMarker ?: return
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(marker.position, 16f)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  UI Updates
    // ═══════════════════════════════════════════════════════════

    /**
     * Update the tracking toggle button appearance.
     */
    private fun updateTrackingButton(isTracking: Boolean) {
        if (isTracking) {
            binding.btnTrackingToggle.text = getString(R.string.stop_tracking)
            binding.btnTrackingToggle.setIconResource(R.drawable.ic_stop)
            binding.btnTrackingToggle.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.tracking_stopped)
            )
        } else {
            binding.btnTrackingToggle.text = getString(R.string.start_tracking)
            binding.btnTrackingToggle.setIconResource(R.drawable.ic_play)
            binding.btnTrackingToggle.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.tracking_active)
            )
        }
    }

    /**
     * Update speed, heading, and accuracy displays.
     */
    private fun updateStats(location: TrackingLocation) {
        binding.tvCurrentSpeed.text = String.format("%.0f", location.speedKmh)
        binding.tvHeading.text = viewModel.formatHeading(location.heading)
        binding.tvDetailAccuracy.text = getString(
            R.string.accuracy_value, String.format("%.0f", location.accuracy)
        )
        binding.tvAccuracy.text = getString(
            R.string.accuracy_value, String.format("%.0f", location.accuracy)
        )
    }

    /**
     * Update the GPS signal indicator.
     */
    private fun updateGpsIndicator(status: DriverNavigationViewModel.GpsStatus) {
        val (color, text) = when (status) {
            DriverNavigationViewModel.GpsStatus.ACTIVE -> {
                ContextCompat.getColor(requireContext(), R.color.tracking_moving) to
                        getString(R.string.gps_status_active)
            }
            DriverNavigationViewModel.GpsStatus.ACQUIRING -> {
                ContextCompat.getColor(requireContext(), R.color.tracking_idle) to
                        getString(R.string.gps_status_acquiring)
            }
            DriverNavigationViewModel.GpsStatus.POOR_SIGNAL -> {
                ContextCompat.getColor(requireContext(), R.color.tracking_alert) to
                        getString(R.string.gps_status_poor)
            }
            DriverNavigationViewModel.GpsStatus.INACTIVE -> {
                ContextCompat.getColor(requireContext(), R.color.tracking_offline) to
                        getString(R.string.gps_status_inactive)
            }
        }
        binding.viewGpsStatus.background?.setTint(color)
        binding.tvGpsStatus.text = text
    }

    // ═══════════════════════════════════════════════════════════
    //  Permissions
    // ═══════════════════════════════════════════════════════════

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Marker Icon
    // ═══════════════════════════════════════════════════════════

    private fun createMarkerIcon(): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_my_location)
            ?: return null
        val color = ContextCompat.getColor(requireContext(), R.color.tracking_route_primary)
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
