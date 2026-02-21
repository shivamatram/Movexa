package com.example.movexa.core

import android.content.Context
import android.location.Location
import android.os.Bundle
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized manager for Google Maps and location operations.
 * Handles map initialization, location tracking, and geofencing.
 *
 * Usage:
 * - Initialize with context when needed
 * - Use location flows to observe position changes
 * - Map operations are exposed for fragment-level map views
 */
class MapManager(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var googleMap: GoogleMap? = null

    // ─── Location State ─────────────────────────────────────────

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _isTrackingLocation = MutableStateFlow(false)
    val isTrackingLocation: StateFlow<Boolean> = _isTrackingLocation.asStateFlow()

    // ─── Location Callback ──────────────────────────────────────

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                _currentLocation.value = location
            }
        }
    }

    // ─── Map Setup ──────────────────────────────────────────────

    /**
     * Set the GoogleMap instance when a MapFragment/MapView is ready.
     */
    fun setMap(map: GoogleMap) {
        googleMap = map
        configureMap()
    }

    /**
     * Configure default map settings.
     */
    private fun configureMap() {
        googleMap?.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            uiSettings.isMyLocationButtonEnabled = true
            uiSettings.isMapToolbarEnabled = true
            mapType = GoogleMap.MAP_TYPE_NORMAL
        }
    }

    /**
     * Release the map reference when the map view is destroyed.
     */
    fun releaseMap() {
        googleMap = null
    }

    // ─── Map Operations ─────────────────────────────────────────

    /**
     * Move the camera to a specific location.
     */
    fun moveCamera(latLng: LatLng, zoom: Float = DEFAULT_ZOOM) {
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, zoom)
        )
    }

    /**
     * Add a marker on the map.
     */
    fun addMarker(latLng: LatLng, title: String, snippet: String? = null) {
        googleMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippet)
        )
    }

    /**
     * Clear all markers and overlays from the map.
     */
    fun clearMap() {
        googleMap?.clear()
    }

    // ─── Location Tracking ──────────────────────────────────────

    /**
     * Request a single location update.
     */
    @RequiresPermission(anyOf = [
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION"
    ])
    fun getLastKnownLocation(callback: (Location?) -> Unit) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location -> callback(location) }
            .addOnFailureListener { callback(null) }
    }

    /**
     * Start continuous location tracking.
     */
    @RequiresPermission(anyOf = [
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION"
    ])
    fun startLocationTracking(
        intervalMs: Long = DEFAULT_LOCATION_INTERVAL,
        fastestIntervalMs: Long = FASTEST_LOCATION_INTERVAL,
        priority: Int = Priority.PRIORITY_HIGH_ACCURACY
    ) {
        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(fastestIntervalMs)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
        _isTrackingLocation.value = true
    }

    /**
     * Stop continuous location tracking.
     */
    fun stopLocationTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTrackingLocation.value = false
    }

    // ─── Utility ────────────────────────────────────────────────

    /**
     * Calculate distance between two LatLng points in meters.
     */
    fun calculateDistance(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude,
            results
        )
        return results[0]
    }

    /**
     * Convert Location to LatLng.
     */
    fun locationToLatLng(location: Location): LatLng {
        return LatLng(location.latitude, location.longitude)
    }

    companion object {
        const val DEFAULT_ZOOM = 15f
        const val CITY_ZOOM = 12f
        const val STREET_ZOOM = 17f
        const val DEFAULT_LOCATION_INTERVAL = 10_000L  // 10 seconds
        const val FASTEST_LOCATION_INTERVAL = 5_000L   // 5 seconds
    }
}
