package com.compass_gpt.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast // Keep for accuracy warnings
import androidx.core.app.ActivityCompat
import com.compass_gpt.R
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {

    // --- Constants ---
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    // Filter coefficient for ROTATION_VECTOR (often needs less filtering)
    // Adjust between 0.1 (smoother) and 0.5 (more responsive) maybe
    private val ALPHA = 0.25f // Start value for Rotation Vector

    // --- UI ---
    private lateinit var compassView: CompassView

    // --- Sensors ---
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null // Using Rotation Vector sensor

    // Reusable arrays for sensor processing
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // --- State ---
    private var filteredAzimuthDeg = 0f
    private var bezelRotationDeg = 0f

    // --- Location ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var declinationDeg = 0f
    private var currentSpeedKmh = 0f
    private var currentAltitudeM = 0f
    private var lastKnownLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        // Initialize CompassView
        val container = findViewById<FrameLayout>(R.id.compassContainer)
        compassView = CompassView(this)
        container.addView(
            compassView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Initialize Sensor Manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // Get the Rotation Vector Sensor
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor == null) {
            Toast.makeText(this, "Rotation Vector Sensor not available!", Toast.LENGTH_LONG).show()
            // Handle lack of essential sensor (compass may not work)
        }

        // Initialize Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()

        // Start requesting location updates
        startLocationUpdates()

        // Initial data update for time display etc.
        updateCompassViewData()
    }

    override fun onResume() {
        super.onResume()
        // Register the Rotation Vector sensor listener
        rotationVectorSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        // Request location updates if permissions are granted
        if (checkLocationPermission()) {
            requestLocationUpdatesInternal()
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister listeners
        sensorManager.unregisterListener(this) // Unregisters all listeners for this context
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // --- Location Handling ---

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermission()) {
            requestLocationPermission()
        } else {
            requestLocationUpdatesInternal()
        }
    }

    private fun requestLocationUpdatesInternal() {
        if (!checkLocationPermission()) return
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // 10 secs
                .setMinUpdateIntervalMillis(5000L) // 5 secs fastest
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (secEx: SecurityException) {
            Toast.makeText(this, "Location permission error.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastKnownLocation = location
                    updateCompassViewData() // Update UI data when location changes
                }
            }
        }
    }

    // Updates the data displayed in the CompassView (Speed, Alt, Decl, Time)
    // Called initially and when GPS location updates.
    private fun updateCompassViewData() {
        // Calculate GPS-derived values if location is available
        lastKnownLocation?.let { location ->
            currentSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            currentAltitudeM = if (location.hasAltitude()) location.altitude.toFloat() else 0f
            val geoField = GeomagneticField(
                location.latitude.toFloat(), location.longitude.toFloat(),
                location.altitude.toFloat(), System.currentTimeMillis()
            )
            declinationDeg = geoField.declination
        } // If location is null, previous values are retained (or defaults used)

        // Always update time strings
        val now = Calendar.getInstance()
        val localFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        localFmt.timeZone = now.timeZone
        val localStr = localFmt.format(now.time)

        val utcFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        utcFmt.timeZone = TimeZone.getTimeZone("UTC")
        val utcStr = utcFmt.format(now.time)

        // Send updated data to the view
        compassView.setData(currentSpeedKmh, currentAltitudeM, declinationDeg, localStr, utcStr)
    }

    // --- Sensor Handling (using TYPE_ROTATION_VECTOR) ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            // Get rotation matrix directly from the rotation vector
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            // Get orientation angles [azimuth, pitch, roll]
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            val azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            // Normalize to [0, 360)
            val normalizedAzimuthDeg = (azimuthDeg % 360f + 360f) % 360f

            // Low-Pass Filter (adjust ALPHA as needed for smoothness)
            var delta = normalizedAzimuthDeg - filteredAzimuthDeg
            while (delta <= -180f) delta += 360f
            while (delta > 180f) delta -= 360f

            // Apply filter (consider if jump detection is still needed, often less critical with ROTATION_VECTOR)
            // val MAX_JUMP_DEG = 150f
            // if (abs(delta) < MAX_JUMP_DEG) {
            filteredAzimuthDeg += ALPHA * delta
            // } else {
            //     filteredAzimuthDeg = normalizedAzimuthDeg // Reset on large jump
            // }

            // Normalize the filtered result
            filteredAzimuthDeg = (filteredAzimuthDeg % 360f + 360f) % 360f

            // Update Compass View needle position
            compassView.setAzimuth(filteredAzimuthDeg)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Accuracy for ROTATION_VECTOR reflects the overall fusion quality.
        // It relies on the underlying sensors (Accel, Mag, Gyro).
        // Low accuracy might still suggest calibrating the MAGNETOMETER.
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                // It might still be helpful to suggest calibration if accuracy is low
                Toast.makeText(this, "Compass accuracy low. Try calibrating (figure 8 motion).", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Crown Input Handling (Optimized) ---

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            val sensitivity = 4.0f // Adjust sensitivity as needed
            bezelRotationDeg = (bezelRotationDeg + delta * sensitivity % 360f + 360f) % 360f

            // --- Optimization: ---
            // ONLY update the bezel rotation in the view.
            // This triggers invalidate() inside setBezelRotation.
            // The BRG readout in onDraw will use the new value automatically.
            // We DO NOT call updateCompassViewData() here anymore.
            compassView.setBezelRotation(bezelRotationDeg)

            return true // Event handled
        }
        return super.onGenericMotionEvent(event)
    }

    // --- Permission Result Handling ---

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates() // Permission granted
            } else {
                Toast.makeText(this, "Location permission denied. GPS features disabled.", Toast.LENGTH_LONG).show()
                updateCompassViewData() // Update UI to show lack of GPS data
            }
        }
    }
}