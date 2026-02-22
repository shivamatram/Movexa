package com.example.movexa.ui.public_tracking

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.movexa.R
import com.example.movexa.data.model.LiveConnectionState
import com.example.movexa.data.model.PublicLiveLocation
import com.example.movexa.data.model.PublicTripInfo
import com.example.movexa.databinding.FragmentPublicLiveTrackingBinding
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
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  PUBLIC LIVE TRACKING FRAGMENT
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Real-time vehicle tracking map for public (unauthenticated) customers.
 *
 * ┌──────────────────────────────────────────────────┐
 * │  ← Live Tracking   TRK-ABCD       ⊕             │
 * │─────────────────────────────────────────────────  │
 * │                                        ┌─────┐   │
 * │               ╱──╲                     │ ETA │   │
 * │     📍────────╱🚛╲                    │12min│   │
 * │              ╲──╱                      │8.5km│   │
 * │                                        └─────┘   │
 * │                                     📍           │
 * │──────────────────────────────────────────────────│
 * │  🚛 Vehicle is moving • 45 km/h                 │
 * │  ─────────────────────────────────────           │
 * │  PICKUP: 123 MG Road → DROP: 456 FC Road        │
 * │  ─────────────────────────────────────           │
 * │  [ View Delivery Details ]          45 km/h      │
 * └──────────────────────────────────────────────────┘
 *
 * ─── Features ─────────────────────────────────────────────────────
 *
 *  ● Full-screen Google Map with vehicle, pickup, and drop markers
 *  ● Real-time vehicle position updates from RTDB
 *  ● Route polyline showing travelled path
 *  ● ETA badge with estimated arrival time and remaining distance
 *  ● Connection state indicator (Connected/Offline/Error)
 *  ● Auto-center on vehicle (disables on manual pan)
 *  ● Camera bounds fitting all markers on first load
 *  ● Smooth marker position updates
 *  ● Bottom info panel with status, addresses, and details button
 *  ● Waiting overlay for initial connection
 *  ● Auto-updates trip status (completed/cancelled)
 *  ● Speed display in bottom panel
 *
 * ─── Architecture ─────────────────────────────────────────────────
 *
 *  ● Shared [PublicTrackingViewModel] for data
 *  ● OnMapReadyCallback for Google Maps
 *  ● Observes: liveLocation, connectionState, statusMessage,
 *    etaMinutes, distanceRemainingKm, routeHistory, tripInfo
 *
 * ─── Security ─────────────────────────────────────────────────────
 *
 *  ● No auth required — all data is sanitised via PublicTripInfo
 *  ● No internal IDs exposed on the map or UI
 *  ● Vehicle location comes from read-only RTDB observation
 *
 * @since 2026-02-22
 */
class PublicLiveTrackingFragment : BaseFragment<FragmentPublicLiveTrackingBinding>(
    FragmentPublicLiveTrackingBinding::inflate
), OnMapReadyCallback {

    // ═══════════════════════════════════════════════════════════════
    //  PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Shared ViewModel */
    private val viewModel: PublicTrackingViewModel by activityViewModels()

    // ─── Map state ──────────────────────────────────────────────
    private var googleMap: GoogleMap? = null
    private var vehicleMarker: Marker? = null
    private var pickupMarker: Marker? = null
    private var dropMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private var vehicleIcon: BitmapDescriptor? = null
    private var isMapReady = false
    private var isAutoCenter = true
    private var hasFittedBounds = false

    // ─── Animation flags ────────────────────────────────────────
    private var hasShownBottomPanel = false

    // ═══════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    companion object {
        /** Default zoom level for the map */
        private const val DEFAULT_ZOOM = 15f

        /** Padding for fitBounds in pixels */
        private const val BOUNDS_PADDING_PX = 120

        /** Animation durations */
        private const val ANIM_PANEL_DURATION = 400L
        private const val ANIM_ETA_DURATION = 300L

        /** India center coordinates (fallback) */
        private const val INDIA_LAT = 20.5937
        private const val INDIA_LNG = 78.9629
        private const val INDIA_ZOOM = 5f

        /** Route polyline width */
        private const val ROUTE_WIDTH = 8f
    }

    // ═══════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onDestroyView() {
        viewModel.stopLiveTracking()
        vehicleMarker = null
        pickupMarker = null
        dropMarker = null
        routePolyline = null
        googleMap = null
        isMapReady = false
        hasFittedBounds = false
        hasShownBottomPanel = false
        super.onDestroyView()
    }

    // ═══════════════════════════════════════════════════════════════
    //  INIT VIEWS
    // ═══════════════════════════════════════════════════════════════

    override fun initViews() {
        // ── Create vehicle marker icon ──────────────────────────
        vehicleIcon = createVehicleIcon()

        // ── Initialize Google Map ───────────────────────────────
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // ── Configure initial UI state ──────────────────────────
        binding.cardEta.visibility = View.GONE
        binding.cardBottomInfo.visibility = View.GONE
        binding.cardWaiting.visibility = View.VISIBLE
        binding.tvSpeed.visibility = View.GONE

        // ── Set tracking ID badge ───────────────────────────────
        val tripInfo = viewModel.tripInfo.value
        if (tripInfo != null) {
            binding.tvTrackingIdBadge.text = tripInfo.trackingId
            binding.tvPickupAddress.text = tripInfo.pickupAddress.ifBlank { "—" }
            binding.tvDropAddress.text = tripInfo.dropAddress.ifBlank { "—" }
        }

        // ── Start live tracking observation ─────────────────────
        viewModel.startLiveTracking()
    }

    // ═══════════════════════════════════════════════════════════════
    //  SETUP LISTENERS
    // ═══════════════════════════════════════════════════════════════

    override fun setupListeners() {
        // ── Back button ─────────────────────────────────────────
        binding.btnBack.setOnClickListener {
            viewModel.stopLiveTracking()
            findNavController().navigateUp()
        }

        // ── Center vehicle button ───────────────────────────────
        binding.btnCenterVehicle.setOnClickListener {
            isAutoCenter = true
            centerOnVehicle()
        }

        // ── View Delivery Details button ────────────────────────
        binding.btnViewDetails.setOnClickListener {
            try {
                findNavController().navigate(
                    R.id.action_liveTracking_to_deliveryDetails
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  OBSERVE DATA
    // ═══════════════════════════════════════════════════════════════

    override fun observeData() {
        // ── Live location updates ───────────────────────────────
        collectLatestFlow(viewModel.liveLocation) { location ->
            if (location != null && location.isValid && isMapReady) {
                updateVehicleMarker(location)
                updateSpeedDisplay(location)

                // Hide waiting overlay on first valid location
                if (binding.cardWaiting.visibility == View.VISIBLE) {
                    hideWaitingOverlay()
                }
            }
        }

        // ── Connection state ────────────────────────────────────
        collectLatestFlow(viewModel.connectionState) { state ->
            updateConnectionIndicator(state)
        }

        // ── Status message ──────────────────────────────────────
        collectLatestFlow(viewModel.statusMessage) { message ->
            if (message.isNotBlank()) {
                binding.tvStatusMessage.text = message
            }
        }

        // ── ETA ─────────────────────────────────────────────────
        collectLatestFlow(viewModel.etaMinutes) { eta ->
            if (eta != null) {
                showEtaBadge(eta)
            } else {
                binding.cardEta.visibility = View.GONE
            }
        }

        // ── Distance remaining ──────────────────────────────────
        collectLatestFlow(viewModel.distanceRemainingKm) { distance ->
            if (distance != null) {
                binding.tvDistanceRemaining.text = viewModel.formatDistance(distance) + " away"
            }
        }

        // ── Route polyline ──────────────────────────────────────
        collectLatestFlow(viewModel.routeHistory) { points ->
            if (isMapReady && points.size >= 2) {
                updateRoutePolyline(points)
            }
        }

        // ── Trip info (status updates) ──────────────────────────
        collectLatestFlow(viewModel.tripInfo) { tripInfo ->
            if (tripInfo != null) {
                binding.tvTrackingIdBadge.text = tripInfo.trackingId
                binding.tvPickupAddress.text = tripInfo.pickupAddress.ifBlank { "—" }
                binding.tvDropAddress.text = tripInfo.dropAddress.ifBlank { "—" }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MAP READY
    // ═══════════════════════════════════════════════════════════════

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true

        // ── Configure map UI settings ───────────────────────────
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = true
            isMapToolbarEnabled = false
            isMyLocationButtonEnabled = false
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = false
        }

        // ── Default camera (India center) ───────────────────────
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(INDIA_LAT, INDIA_LNG), INDIA_ZOOM
            )
        )

        // ── Disable auto-center on user gesture ─────────────────
        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                isAutoCenter = false
            }
        }

        // ── Add pickup and drop markers ─────────────────────────
        addPickupDropMarkers()

        // ── If location already available, show it ──────────────
        val currentLocation = viewModel.liveLocation.value
        if (currentLocation != null && currentLocation.isValid) {
            updateVehicleMarker(currentLocation)
            hideWaitingOverlay()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PICKUP & DROP MARKERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Add pickup (green) and drop-off (red) markers to the map.
     * Also fits camera bounds to include both markers.
     */
    private fun addPickupDropMarkers() {
        val map = googleMap ?: return
        val tripInfo = viewModel.tripInfo.value ?: return

        val boundsBuilder = LatLngBounds.Builder()
        var hasMarkers = false

        // ── Pickup marker ───────────────────────────────────────
        if (tripInfo.hasPickup) {
            val pickupPos = LatLng(tripInfo.pickupLat, tripInfo.pickupLng)
            pickupMarker = map.addMarker(
                MarkerOptions()
                    .position(pickupPos)
                    .title(getString(R.string.public_map_pickup_marker))
                    .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN
                    ))
            )
            boundsBuilder.include(pickupPos)
            hasMarkers = true
        }

        // ── Drop-off marker ─────────────────────────────────────
        if (tripInfo.hasDrop) {
            val dropPos = LatLng(tripInfo.dropLat, tripInfo.dropLng)
            dropMarker = map.addMarker(
                MarkerOptions()
                    .position(dropPos)
                    .title(getString(R.string.public_map_drop_marker))
                    .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED
                    ))
            )
            boundsBuilder.include(dropPos)
            hasMarkers = true
        }

        // ── Fit bounds to show all markers ──────────────────────
        if (hasMarkers && !hasFittedBounds) {
            try {
                val bounds = boundsBuilder.build()
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX)
                )
                hasFittedBounds = true
            } catch (e: Exception) {
                // Not enough markers for bounds — ignore
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  VEHICLE MARKER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update the vehicle marker position on the map.
     * Creates the marker on first call, then updates position/rotation.
     */
    private fun updateVehicleMarker(location: PublicLiveLocation) {
        val map = googleMap ?: return
        val position = LatLng(location.lat, location.lng)

        if (vehicleMarker == null) {
            // ── Create new marker ───────────────────────────────
            vehicleMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(vehicleIcon)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .rotation(location.heading)
                    .title(getString(R.string.public_map_vehicle_marker))
            )

            // ── Show bottom info panel ──────────────────────────
            if (!hasShownBottomPanel) {
                showBottomInfoPanel()
                hasShownBottomPanel = true
            }

            // ── Fit all markers including vehicle ───────────────
            fitAllMarkersInBounds()
        } else {
            // ── Update existing marker ──────────────────────────
            vehicleMarker?.position = position
            vehicleMarker?.rotation = location.heading
        }

        // ── Auto-center ─────────────────────────────────────────
        if (isAutoCenter) {
            map.animateCamera(CameraUpdateFactory.newLatLng(position))
        }
    }

    /**
     * Fit camera bounds to include vehicle, pickup, and drop markers.
     */
    private fun fitAllMarkersInBounds() {
        val map = googleMap ?: return
        val boundsBuilder = LatLngBounds.Builder()
        var count = 0

        vehicleMarker?.position?.let { boundsBuilder.include(it); count++ }
        pickupMarker?.position?.let { boundsBuilder.include(it); count++ }
        dropMarker?.position?.let { boundsBuilder.include(it); count++ }

        if (count >= 2) {
            try {
                val bounds = boundsBuilder.build()
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX)
                )
            } catch (_: Exception) { }
        } else if (count == 1) {
            vehicleMarker?.position?.let {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM))
            }
        }
    }

    /**
     * Center camera on the vehicle marker.
     */
    private fun centerOnVehicle() {
        val map = googleMap ?: return
        val marker = vehicleMarker ?: return
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(marker.position, DEFAULT_ZOOM)
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  ROUTE POLYLINE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Draw or update the route polyline from location history.
     */
    private fun updateRoutePolyline(points: List<PublicTrackingViewModel.LatLng>) {
        val map = googleMap ?: return
        val mapPoints = points.map { LatLng(it.lat, it.lng) }

        if (routePolyline == null) {
            val routeColor = ContextCompat.getColor(
                requireContext(), R.color.public_map_route
            )
            routePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(mapPoints)
                    .width(ROUTE_WIDTH)
                    .color(routeColor)
                    .geodesic(true)
            )
        } else {
            routePolyline?.points = mapPoints
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONNECTION STATE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update the connection indicator dot and text based on state.
     */
    private fun updateConnectionIndicator(state: LiveConnectionState) {
        val context = context ?: return

        val (dotColorRes, statusText) = when (state) {
            LiveConnectionState.CONNECTING -> {
                R.color.public_conn_connecting to getString(R.string.public_conn_connecting)
            }
            LiveConnectionState.CONNECTED -> {
                R.color.public_conn_connected to getString(R.string.public_conn_connected)
            }
            LiveConnectionState.VEHICLE_OFFLINE -> {
                R.color.public_conn_offline to getString(R.string.public_conn_vehicle_offline)
            }
            LiveConnectionState.DISCONNECTED -> {
                R.color.public_conn_disconnected to getString(R.string.public_conn_disconnected)
            }
            LiveConnectionState.ERROR -> {
                R.color.public_conn_error to getString(R.string.public_conn_error)
            }
        }

        binding.tvConnectionStatus.text = statusText
        binding.tvConnectionStatus.setTextColor(
            ContextCompat.getColor(context, dotColorRes)
        )

        // Update waiting overlay for connection states
        when (state) {
            LiveConnectionState.CONNECTING -> {
                if (vehicleMarker == null) {
                    binding.cardWaiting.visibility = View.VISIBLE
                    binding.tvWaitingTitle.text = getString(R.string.public_conn_connecting)
                    binding.tvWaitingMessage.text = getString(R.string.public_status_waiting_gps)
                }
            }
            LiveConnectionState.VEHICLE_OFFLINE -> {
                if (vehicleMarker == null) {
                    binding.cardWaiting.visibility = View.VISIBLE
                    binding.tvWaitingTitle.text = getString(R.string.public_conn_vehicle_offline)
                    binding.tvWaitingMessage.text = getString(R.string.public_status_waiting)
                }
            }
            LiveConnectionState.ERROR -> {
                if (vehicleMarker == null) {
                    binding.cardWaiting.visibility = View.VISIBLE
                    binding.tvWaitingTitle.text = getString(R.string.public_conn_error)
                    binding.tvWaitingMessage.text = getString(R.string.public_error_network_message)
                }
            }
            else -> { /* handled elsewhere */ }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ETA BADGE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Show or update the ETA badge with animation.
     */
    private fun showEtaBadge(etaMinutes: Int) {
        binding.tvEta.text = viewModel.formatEta(etaMinutes)

        if (binding.cardEta.visibility != View.VISIBLE) {
            binding.cardEta.visibility = View.VISIBLE
            binding.cardEta.alpha = 0f
            binding.cardEta.scaleX = 0.8f
            binding.cardEta.scaleY = 0.8f
            binding.cardEta.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ANIM_ETA_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SPEED DISPLAY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update the speed text in the bottom info panel.
     */
    private fun updateSpeedDisplay(location: PublicLiveLocation) {
        if (location.isMoving && location.speedKmh > 0f) {
            binding.tvSpeed.visibility = View.VISIBLE
            binding.tvSpeed.text = getString(
                R.string.public_live_speed_value,
                location.speedKmh
            )
        } else {
            binding.tvSpeed.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI ANIMATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Hide the waiting overlay with a fade animation.
     */
    private fun hideWaitingOverlay() {
        binding.cardWaiting.animate()
            .alpha(0f)
            .setDuration(300L)
            .withEndAction {
                binding.cardWaiting.visibility = View.GONE
            }
            .start()
    }

    /**
     * Show the bottom info panel with a slide-up animation.
     */
    private fun showBottomInfoPanel() {
        binding.cardBottomInfo.visibility = View.VISIBLE
        binding.cardBottomInfo.translationY = 200f
        binding.cardBottomInfo.alpha = 0f
        binding.cardBottomInfo.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(ANIM_PANEL_DURATION)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // ═══════════════════════════════════════════════════════════════
    //  VEHICLE ICON
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a BitmapDescriptor for the vehicle marker from a vector drawable.
     * Uses the public_map_vehicle color.
     */
    private fun createVehicleIcon(): BitmapDescriptor? {
        val context = context ?: return null
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_vehicle_marker)
            ?: return null
        val color = ContextCompat.getColor(context, R.color.public_map_vehicle)
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
