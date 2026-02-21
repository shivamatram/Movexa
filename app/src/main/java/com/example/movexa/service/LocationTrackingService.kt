package com.example.movexa.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.movexa.MainActivity
import com.example.movexa.MoveXaApp
import com.example.movexa.R
import com.example.movexa.data.model.TrackingLocation
import com.example.movexa.data.model.ResultState
import com.example.movexa.data.repository.impl.TrackingRepositoryImpl
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service for continuous GPS location tracking.
 *
 * Lifecycle:
 *   startService(ACTION_START) → creates FusedLocationProvider → requests updates
 *     → every [INTERVAL_MS] uploads to Realtime DB at tracking_locations/{companyId}/{vehicleId}
 *   stopService(ACTION_STOP) → removes updates → stops foreground → destroys
 *
 * Required permissions:
 *   - ACCESS_FINE_LOCATION
 *   - ACCESS_COARSE_LOCATION
 *   - FOREGROUND_SERVICE
 *   - FOREGROUND_SERVICE_LOCATION
 *   - ACCESS_BACKGROUND_LOCATION (for background tracking)
 *
 * Intent extras:
 *   - EXTRA_COMPANY_ID: Company identifier
 *   - EXTRA_VEHICLE_ID: Vehicle identifier
 *   - EXTRA_DRIVER_ID: Driver identifier
 *   - EXTRA_TRIP_ID: (optional) Active trip ID
 */
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"

        // Actions
        const val ACTION_START = "com.example.movexa.action.START_TRACKING"
        const val ACTION_STOP = "com.example.movexa.action.STOP_TRACKING"

        // Intent Extras
        const val EXTRA_COMPANY_ID = "extra_company_id"
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        const val EXTRA_DRIVER_ID = "extra_driver_id"
        const val EXTRA_TRIP_ID = "extra_trip_id"

        // Configuration
        private const val INTERVAL_MS = 5_000L          // 5 seconds
        private const val FASTEST_INTERVAL_MS = 3_000L   // 3 seconds minimum
        private const val NOTIFICATION_ID = 9001

        // Static state for UI observation
        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _lastLocation = MutableStateFlow<TrackingLocation?>(null)
        val lastLocation: StateFlow<TrackingLocation?> = _lastLocation.asStateFlow()

        private val _trackingStartTime = MutableStateFlow(0L)
        val trackingStartTime: StateFlow<Long> = _trackingStartTime.asStateFlow()

        /**
         * Start the tracking service with required parameters.
         */
        fun start(
            context: Context,
            companyId: String,
            vehicleId: String,
            driverId: String,
            tripId: String = ""
        ) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_COMPANY_ID, companyId)
                putExtra(EXTRA_VEHICLE_ID, vehicleId)
                putExtra(EXTRA_DRIVER_ID, driverId)
                putExtra(EXTRA_TRIP_ID, tripId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Stop the tracking service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    // ─── Dependencies ───────────────────────────────────────────
    private val trackingRepository = TrackingRepositoryImpl()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── State ──────────────────────────────────────────────────
    private var companyId: String = ""
    private var vehicleId: String = ""
    private var driverId: String = ""
    private var tripId: String = ""
    private var uploadFailCount = 0
    private val maxRetryFailures = 10

    // ─── Location Callback ──────────────────────────────────────
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val trackingLocation = TrackingLocation(
                    lat = location.latitude,
                    lng = location.longitude,
                    speed = location.speed,
                    heading = location.bearing,
                    accuracy = location.accuracy,
                    timestamp = System.currentTimeMillis(),
                    tripId = tripId,
                    driverId = driverId,
                    vehicleId = vehicleId,
                    isMoving = location.speed > 0.5f // moving if speed > 0.5 m/s (~1.8 km/h)
                )

                // Update static state for UI
                _lastLocation.value = trackingLocation

                // Upload to Realtime Database
                uploadLocation(trackingLocation)
            }
        }
    }

    // ─── Service Lifecycle ──────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                companyId = intent.getStringExtra(EXTRA_COMPANY_ID) ?: ""
                vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID) ?: ""
                driverId = intent.getStringExtra(EXTRA_DRIVER_ID) ?: ""
                tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: ""

                if (companyId.isBlank() || vehicleId.isBlank()) {
                    Log.e(TAG, "Missing companyId or vehicleId — cannot start tracking")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundTracking()
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─── Core Tracking Logic ────────────────────────────────────

    /**
     * Start the foreground service and begin location updates.
     */
    private fun startForegroundTracking() {
        // Check location permission
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted — stopping")
            stopSelf()
            return
        }

        // Start as foreground service
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Build location request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        // Start location updates
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            _isTracking.value = true
            _trackingStartTime.value = System.currentTimeMillis()
            uploadFailCount = 0

            Log.i(TAG, "Tracking started: company=$companyId vehicle=$vehicleId driver=$driverId trip=$tripId")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException — location permission revoked", e)
            stopSelf()
        }
    }

    /**
     * Stop location updates and clean up.
     */
    private fun stopTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing location updates", e)
        }

        // Remove location from Realtime Database (vehicle goes offline)
        if (companyId.isNotBlank() && vehicleId.isNotBlank()) {
            serviceScope.launch {
                try {
                    trackingRepository.removeLocation(companyId, vehicleId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove tracking location on stop", e)
                }
            }
        }

        _isTracking.value = false
        _lastLocation.value = null
        _trackingStartTime.value = 0L

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i(TAG, "Tracking stopped")
    }

    /**
     * Upload a single location update to Realtime Database.
     * Handles transient failures with retry count tracking.
     */
    private fun uploadLocation(location: TrackingLocation) {
        serviceScope.launch {
            try {
                val result = trackingRepository.updateLocation(companyId, vehicleId, location)
                when (result) {
                    is ResultState.Success -> {
                        uploadFailCount = 0 // Reset on success
                    }
                    is ResultState.Error -> {
                        uploadFailCount++
                        Log.w(TAG, "Upload failed ($uploadFailCount/$maxRetryFailures): ${result.message}")
                        if (uploadFailCount >= maxRetryFailures) {
                            Log.e(TAG, "Too many upload failures — stopping tracking")
                            stopTracking()
                        }
                    }
                    else -> { /* Loading/Idle — ignore */ }
                }
            } catch (e: Exception) {
                uploadFailCount++
                Log.e(TAG, "Upload exception ($uploadFailCount/$maxRetryFailures)", e)
                if (uploadFailCount >= maxRetryFailures) {
                    stopTracking()
                }
            }
        }
    }

    // ─── Notification ───────────────────────────────────────────

    /**
     * Create the foreground service notification.
     * Shows persistent notification while GPS tracking is active.
     */
    private fun createNotification(): Notification {
        // Tapping the notification opens the app
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop tracking action button
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MoveXaApp.CHANNEL_TRACKING)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(R.drawable.ic_nav_tracking)
            .setContentIntent(tapPendingIntent)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.tracking_notification_stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
