package com.compass_gpt.presentation

import android.Manifest
import android.app.Activity
import android.content.Context // Added for getSystemService
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager // Import SensorManager constants
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Bundle
import android.os.Looper // Added for mainLooper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.compass_gpt.R
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity(), SensorEventListener {

    // --- Constants ---
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val ALPHA = 0.25f // Low-pass filter smoothing factor

    // --- UI ---
    private lateinit var compassView: CompassView

    // --- Sensors ---
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3) // [0] Azimuth, [1] Pitch, [2] Roll

    // --- State ---
    private var filteredAzimuthDeg = 0f
    private var currentPitchDeg = 0f
    private var currentRollDeg = 0f
    private var currentSensorAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE // Store accuracy state
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
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor == null) {
            Toast.makeText(this, "Rotation Vector Sensor not available!", Toast.LENGTH_LONG).show()
        } else {
            // Set initial accuracy state in the view when created
            compassView.setSensorAccuracy(currentSensorAccuracy)
        }

        // Initialize Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()
        startLocationUpdates()

        // Initial data update for time display etc.
        updateCompassViewGpsData()
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
        // Unregister listeners to save power
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
                    updateCompassViewGpsData() // Update UI data when location changes
                }
            }
        }
    }

    // Updates the GPS/Time data displayed in the CompassView
    private fun updateCompassViewGpsData() {
        lastKnownLocation?.let { location ->
            currentSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            currentAltitudeM = if (location.hasAltitude()) location.altitude.toFloat() else 0f
            val geoField = GeomagneticField(
                location.latitude.toFloat(), location.longitude.toFloat(),
                location.altitude.toFloat(), System.currentTimeMillis()
            )
            declinationDeg = geoField.declination
        }

        val now = Calendar.getInstance()
        val localFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        localFmt.timeZone = now.timeZone
        val localStr = localFmt.format(now.time)

        val utcFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        utcFmt.timeZone = TimeZone.getTimeZone("UTC")
        val utcStr = utcFmt.format(now.time)

        // Send updated GPS/Time data to the view
        compassView.setGpsData(currentSpeedKmh, currentAltitudeM, declinationDeg, localStr, utcStr)
    }

    // --- Sensor Handling (using TYPE_ROTATION_VECTOR) ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Extract Azimuth, Pitch, Roll in Radians
            val azimuthRad = orientationAngles[0]
            val pitchRad = orientationAngles[1]
            val rollRad = orientationAngles[2]

            // Azimuth Processing
            val azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            val normalizedAzimuthDeg = (azimuthDeg % 360f + 360f) % 360f
            var delta = normalizedAzimuthDeg - filteredAzimuthDeg
            while (delta <= -180f) delta += 360f
            while (delta > 180f) delta -= 360f
            filteredAzimuthDeg += ALPHA * delta
            filteredAzimuthDeg = (filteredAzimuthDeg % 360f + 360f) % 360f

            // Pitch and Roll Processing
            currentPitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat()
            currentRollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()

            // Update CompassView with Sensor Data
            compassView.setSensorData(filteredAzimuthDeg, currentPitchDeg, currentRollDeg)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Update accuracy state ONLY if it has actually changed
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR && accuracy != currentSensorAccuracy) {
            currentSensorAccuracy = accuracy
            // Pass the new accuracy level to the CompassView
            compassView.setSensorAccuracy(accuracy)
        }
    }

    // --- Crown Input Handling ---

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) // Use correct constant
        ) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            val sensitivity = 4.0f
            bezelRotationDeg = (bezelRotationDeg + delta * sensitivity % 360f + 360f) % 360f
            compassView.setBezelRotation(bezelRotationDeg)
            return true
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
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission denied. GPS features disabled.", Toast.LENGTH_LONG).show()
                updateCompassViewGpsData()
            }
        }
    }
}