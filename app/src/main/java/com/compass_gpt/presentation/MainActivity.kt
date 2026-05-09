package com.compass_gpt.presentation

import android.Manifest
import android.app.Activity
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
    private val locationPermissionRequestCode = 1001
    private val alpha = 0.25f

    // --- UI ---
    private lateinit var compassView: CompassView

    // --- Sensors ---
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // --- State ---
    private var filteredAzimuthDeg = 0f
    private var currentPitchDeg = 0f
    private var currentRollDeg = 0f
    private var currentSensorAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
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

        val container = findViewById<FrameLayout>(R.id.compassContainer)
        compassView = CompassView(this)
        container.addView(
            compassView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor == null) {
            Toast.makeText(this, "Core sensors not found!", Toast.LENGTH_LONG).show()
        } else {
            compassView.setSensorAccuracy(currentSensorAccuracy)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()
        startLocationUpdates()
        updateCompassViewGpsData()
    }

    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        // FORCE a redraw when returning from a paused state (like dismissing a permission dialog)
        compassView.invalidate()

        if (checkLocationPermission()) {
            requestLocationUpdatesInternal()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            locationPermissionRequestCode,
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
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
                .setMinUpdateIntervalMillis(5000L)
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            Toast.makeText(this, "Location permission error.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            // Added broader catch block in case OS has globally disabled location services
            Toast.makeText(this, "Location services unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastKnownLocation = location
                    updateCompassViewGpsData()
                }
            }
        }
    }

    private fun updateCompassViewGpsData() {
        lastKnownLocation?.let { location ->
            currentSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            currentAltitudeM = if (location.hasAltitude()) location.altitude.toFloat() else 0f
            val geoField = GeomagneticField(
                location.latitude.toFloat(), location.longitude.toFloat(),
                location.altitude.toFloat(), System.currentTimeMillis(),
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

        compassView.setGpsData(currentSpeedKmh, currentAltitudeM, declinationDeg, localStr, utcStr)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val azimuthRad = orientationAngles[0]
            val pitchRad = orientationAngles[1]
            val rollRad = orientationAngles[2]

            val azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            var delta = azimuthDeg - filteredAzimuthDeg
            while (delta <= -180f) delta += 360f
            while (delta > 180f) delta -= 360f
            filteredAzimuthDeg += alpha * delta
            filteredAzimuthDeg = ((filteredAzimuthDeg % 360f) + 360f) % 360f

            currentPitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat()
            currentRollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()

            compassView.setSensorData(filteredAzimuthDeg, currentPitchDeg, currentRollDeg)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if ((sensor?.type == Sensor.TYPE_ROTATION_VECTOR) && (accuracy != currentSensorAccuracy)) {
            currentSensorAccuracy = accuracy
            compassView.setSensorAccuracy(accuracy)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if ((event?.action == MotionEvent.ACTION_SCROLL) &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            val sensitivity = 4.0f
            bezelRotationDeg = ((((bezelRotationDeg + (delta * sensitivity)) % 360f) + 360f) % 360f)
            compassView.setBezelRotation(bezelRotationDeg)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode) {
            if (grantResults.isNotEmpty() && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission denied. GPS features disabled.", Toast.LENGTH_LONG).show()
                updateCompassViewGpsData()
            }
        }
    }
}