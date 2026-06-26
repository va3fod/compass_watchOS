package com.compass_gpt.presentation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import com.compass_gpt.R
import kotlin.math.*
import kotlin.random.Random

class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    // --- Constants ---
    private val doubleTapTimeoutMs = 300L
    private val singleTapConfirmDelayMs = (doubleTapTimeoutMs + 50L)
    private val hideSecretTextDelayMs = 2000L
    private val squirrelCatchThresholdDp = 15f
    private val squirrelSpeedFactor = 0.02f
    private val catMoveTimeoutMs = 400L
    private val fireworksDurationMs = 3000L
    private val fireworksParticleCount = 75
    private val fireworksMinSpeedFactor = 0.9f
    private val fireworksMaxSpeedFactor = 1.5f

    // --- Paints ---
    private val bezelPaint = Paint().apply { color = Color.WHITE; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val readoutPaint = Paint().apply { color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val magneticReadoutPaint = Paint().apply { color = Color.RED; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val trueNorthReadoutPaint = Paint().apply { color = Color.BLUE; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val magneticNeedlePaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
    private val trueNorthNeedlePaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL; isAntiAlias = true }
    private val bearingMarkerPaint = Paint().apply { color = "#ADD8E6".toColorInt(); style = Paint.Style.FILL; isAntiAlias = true }
    private val catSymbolMagneticPaint = Paint().apply { color = Color.RED; textSize = 45f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val catSymbolTrueNorthPaint = Paint().apply { color = Color.BLUE; textSize = 45f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val bubbleLevelOutlinePaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val bubbleLevelBubblePaint = Paint().apply { color = "#88FFFFFF".toColorInt(); style = Paint.Style.FILL; isAntiAlias = true }
    private val secretTextPaint = Paint().apply { color = Color.RED; textSize = 100f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val versionTextPaint = Paint().apply { color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT }
    private val donationTextPaint = Paint().apply { color = Color.YELLOW; textSize = 22f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val goToNeedlePaint = Paint().apply { color = Color.YELLOW; strokeWidth = 5f; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
    private val accuracyPaintHigh = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
    private val accuracyPaintMedium = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val accuracyPaintLow = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val accuracyPaintUnreliable = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL }

    // Easter Egg Paints
    private val squirrelPaint = Paint().apply { color = "#FFA500".toColorInt(); textSize = 40f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val fireworksParticlePaint = Paint().apply { strokeWidth = 6f; style = Paint.Style.FILL; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
    private val fireworkColors = listOf(Color.YELLOW, Color.RED, Color.WHITE, Color.CYAN, Color.MAGENTA, Color.GREEN, "#FF8C00".toColorInt(), "#FF1493".toColorInt())

    // --- Path & Symbols ---
    private val trueNorthMarkerPath = Path()
    private val magneticNorthMarkerPath = Path()
    private val magneticNorthSymbol = "🐈"
    private val trueNorthSymbol = "🐾"
    private val secretText = "VA3FOD"
    private var versionText = "v1.5"
    private val donationEmail = "aschiuta@gmail.com"
    private val squirrelSymbol = "🐿️"

    // --- Reusable Rects ---
    private val tempTextBounds = Rect()

    // --- State Variables ---
    private var magneticNeedleDeg = 0f
    private var pitchDeg = 0f
    private var rollDeg = 0f
    private var bezelRotationDeg = 0f
    private var speedKmh = 0f
    private var altitudeM = 0f
    private var declDeg = 0f
    private var localTime = ""
    private var utcTime = ""
    private var latStr = "---"
    private var lonStr = "---"
    private var showTrueNorth = false
    private var currentThemeIndex = 0 // 0: NORMAL, 1: TACTICAL_RED, 2: NVG_GREEN
    private var waypointBearing: Float? = null
    private var symbolBounds = RectF()
    private val symbolClickPadding = 20f
    private var sensorAccuracyLevel: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var isAmbientMode = false

    // Interaction State
    private var isHoldingSymbol = false
    private var isHoldingCenter = false
    private var isHoldingEdge = false
    private var showSecretText = false
    private var lastTapTimeMs = 0L
    private val interactionHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var singleTapRunnable: Runnable? = null
    private val hideSecretTextRunnable = Runnable {
        showSecretText = false
        invalidate()
    }

    // Easter Egg State
    private var isEasterEggModeActive = false
    private var mediaPlayer: MediaPlayer? = null
    private var catPositionX: Float = 0f
    private var catPositionY: Float = 0f
    private var squirrelPositionX: Float = 0f
    private var squirrelPositionY: Float = 0f
    private var isCaught: Boolean = false
    private var lastCatMoveTimeMs: Long = 0L
    private var catchThresholdPx: Float = 0f
    private var isFireworksActive: Boolean = false
    private val fireworksParticles = mutableListOf<FireworkParticle>()
    private var fireworksAnimator: ValueAnimator? = null
    private var lastFireworksUpdate: Long = 0L

    init {
        isHapticFeedbackEnabled = true
        // Keep initialization light to prevent ANR/Black Screen
    }

    // --- Setters ---
    fun setSensorData(azimuth: Float, pitch: Float, roll: Float) {
        magneticNeedleDeg = (((azimuth % 360f) + 360f) % 360f)
        this.pitchDeg = pitch
        this.rollDeg = roll
        invalidate()
    }

    fun setBezelRotation(angle: Float) {
        bezelRotationDeg = (((angle % 360f) + 360f) % 360f)
        invalidate()
    }

    fun setGpsData(speed: Float, altitude: Float, decl: Float, local: String, utc: String, lat: String = "---", lon: String = "---") {
        speedKmh = speed
        altitudeM = altitude
        declDeg = decl
        localTime = local
        utcTime = utc
        latStr = lat
        lonStr = lon
        invalidate()
    }

    fun setWaypointBearing(bearing: Float?) {
        waypointBearing = bearing
        invalidate()
    }

    private var onWaypointSetListener: (() -> Unit)? = null
    fun setOnWaypointSetListener(listener: () -> Unit) {
        onWaypointSetListener = listener
    }

    fun setSensorAccuracy(accuracy: Int) {
        if (accuracy != sensorAccuracyLevel) {
            sensorAccuracyLevel = accuracy
            invalidate()
        }
    }

    fun setAmbientMode(enabled: Boolean) {
        if (isAmbientMode != enabled) {
            isAmbientMode = enabled
            invalidate()
        }
    }

    fun setVersionInfo(version: String) {
        versionText = version
        invalidate()
    }

    // --- Touch Event Handling ---
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (isEasterEggModeActive) return true // Block standard touches during easter egg

        if (event == null) return super.onTouchEvent(event)
        val touchX = (event.x - (width / 2f))
        val touchY = (event.y - (height / 2f))
        val distFromCenter = sqrt((touchX * touchX) + (touchY * touchY))
        val edgeThreshold = (min(width, height) / 2f) * 0.7f

        // --- Persistent Secret Screen Toggle ---
        if (showSecretText) {
            if (event.action == MotionEvent.ACTION_UP) {
                val currentTime = System.currentTimeMillis()
                if ((currentTime - lastTapTimeMs) <= doubleTapTimeoutMs) {
                    // Double tap detected: Exit secret screen
                    showSecretText = false
                    lastTapTimeMs = 0L
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                } else {
                    lastTapTimeMs = currentTime
                }
            }
            return true // Consume all touch events while in secret mode
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                singleTapRunnable?.let { interactionHandler.removeCallbacks(it) }
                singleTapRunnable = null
                interactionHandler.removeCallbacks(hideSecretTextRunnable)

                if (symbolBounds.contains(touchX, touchY)) {
                    isHoldingSymbol = true
                    // Note: Don't set showSecretText = false immediately to allow persistence
                    longPressRunnable = Runnable {
                        if (isHoldingSymbol) {
                            showSecretText = true
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            invalidate()
                            longPressRunnable = null // Mark as fired
                        }
                    }
                    interactionHandler.postDelayed(longPressRunnable!!, 2000L) // Exactly 2s for callsign
                    return true
                } else if (distFromCenter < (edgeThreshold / 2f)) {
                    // Holding center (Cycle Themes)
                    isHoldingCenter = true
                    longPressRunnable = Runnable {
                        if (isHoldingCenter) {
                            currentThemeIndex = (currentThemeIndex + 1) % 3
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            invalidate()
                            longPressRunnable = null // Mark as fired
                        }
                    }
                    interactionHandler.postDelayed(longPressRunnable!!, 2000L) // Center long press 2s
                    return true
                } else if (distFromCenter > edgeThreshold) {
                    // Holding edge (Waypoint set)
                    isHoldingEdge = true
                    longPressRunnable = Runnable {
                        if (isHoldingEdge) {
                            onWaypointSetListener?.invoke()
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            invalidate()
                            longPressRunnable = null // Mark as fired
                        }
                    }
                    interactionHandler.postDelayed(longPressRunnable!!, 2000L) // Edge long press 2s
                    return true
                }
                lastTapTimeMs = 0L
            }

            MotionEvent.ACTION_MOVE -> {
                if (isHoldingSymbol && (!symbolBounds.contains(touchX, touchY))) {
                    resetSymbolInteractionState()
                }
                if (isHoldingCenter && (distFromCenter >= (edgeThreshold / 2f))) {
                    resetSymbolInteractionState()
                }
                if (isHoldingEdge && (distFromCenter <= edgeThreshold)) {
                    resetSymbolInteractionState()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoldingSymbol || isHoldingCenter || isHoldingEdge) {
                    val currentTime = System.currentTimeMillis()
                    val wasLongPress = (longPressRunnable == null)
                    val wasHoldingSymbol = isHoldingSymbol
                    val wasHoldingAny = (isHoldingSymbol || isHoldingCenter || isHoldingEdge)

                    resetSymbolInteractionState(keepSecretIfHolding = true)

                    if ((event.action == MotionEvent.ACTION_UP) && wasHoldingSymbol && symbolBounds.contains(touchX, touchY)) {
                        if (wasLongPress) {
                            // Already handled above
                        } else if ((currentTime - lastTapTimeMs) <= doubleTapTimeoutMs) {
                            // --- DOUBLE TAP TRIGGER ---
                            lastTapTimeMs = 0L
                            activateEasterEggMode()
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        } else {
                            // --- SINGLE TAP TRIGGER ---
                            lastTapTimeMs = currentTime
                            singleTapRunnable = Runnable {
                                showTrueNorth = !showTrueNorth
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                invalidate()
                                singleTapRunnable = null
                            }
                            interactionHandler.postDelayed(singleTapRunnable!!, singleTapConfirmDelayMs)
                        }
                    } else {
                        lastTapTimeMs = 0L
                    }
                    if (wasHoldingAny) {
                        performClick()
                    }
                    return true
                }
                lastTapTimeMs = 0L
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun resetSymbolInteractionState(keepSecretIfHolding: Boolean = false) {
        longPressRunnable?.let { interactionHandler.removeCallbacks(it) }
        longPressRunnable = null
        singleTapRunnable?.let { interactionHandler.removeCallbacks(it) }
        singleTapRunnable = null

        val needsRedraw = (!keepSecretIfHolding && showSecretText) || isHoldingSymbol || isHoldingCenter || isHoldingEdge
        isHoldingSymbol = false
        isHoldingCenter = false
        isHoldingEdge = false
        if (!keepSecretIfHolding) showSecretText = false
        if (needsRedraw) invalidate()
    }

    // --- Easter Egg Activation/Deactivation ---
    private fun activateEasterEggMode() {
        if (isEasterEggModeActive) return
        if ((width == 0) || (height == 0)) return

        resetSymbolInteractionState()
        isEasterEggModeActive = true
        isCaught = false
        isFireworksActive = false
        fireworksAnimator?.cancel()
        fireworksParticles.clear()

        if (catchThresholdPx == 0f) {
            catchThresholdPx = (squirrelCatchThresholdDp * resources.displayMetrics.density)
        }

        val chaseContainerRadius = ((min(width / 2f, height / 2f) * 0.90f) * 0.90f)
        val maxAngleMap = 45f
        val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()

        catPositionX = (-chaseContainerRadius * sin(rollRad) * (90f / maxAngleMap))
        catPositionY = (chaseContainerRadius * sin(pitchRad) * (90f / maxAngleMap))

        val catTotalOffset = sqrt((catPositionX * catPositionX) + (catPositionY * catPositionY))
        if (catTotalOffset > chaseContainerRadius) {
            val scale = (chaseContainerRadius / catTotalOffset)
            catPositionX *= scale
            catPositionY *= scale
        }

        // Squirrel starts opposite to the cat
        val catAngleRad = atan2(catPositionY, catPositionX)
        val squirrelAngleRad = (catAngleRad + PI.toFloat())
        val squirrelDist = (chaseContainerRadius * 0.8f)
        squirrelPositionX = (squirrelDist * cos(squirrelAngleRad))
        squirrelPositionY = (squirrelDist * sin(squirrelAngleRad))

        lastCatMoveTimeMs = SystemClock.elapsedRealtime()

        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer.create(context, R.raw.meow)
            } catch (_: Exception) {
                // Ignore missing resource
            }
        }
        mediaPlayer?.let {
            if (!it.isPlaying) {
                try {
                    it.seekTo(0)
                    it.start()
                } catch (_: IllegalStateException) {
                    // Ignore state error
                }
            }
        }
        invalidate()
    }

    private fun deactivateEasterEggMode() {
        isEasterEggModeActive = false
        fireworksAnimator?.cancel()
        isFireworksActive = false
        lastTapTimeMs = 0L
        invalidate()
    }

    private fun startFireworksAnimation(x: Float, y: Float) {
        if (isFireworksActive) return
        if ((width == 0) || (height == 0)) return

        isFireworksActive = true
        fireworksParticles.clear()

        val screenRadius = min(width / 2f, height / 2f)

        repeat(fireworksParticleCount) {
            val angle = (Random.nextFloat() * 2 * PI.toFloat())
            val speed = ((Random.nextFloat() * (fireworksMaxSpeedFactor - fireworksMinSpeedFactor)) + fireworksMinSpeedFactor)
            val timeFactor = if (fireworksDurationMs > 0) (fireworksDurationMs / 1000f) else 1f
            val velocityX = ((cos(angle) * speed * screenRadius) / timeFactor)
            val velocityY = ((sin(angle) * speed * screenRadius) / timeFactor)
            val color = fireworkColors.random()
            fireworksParticles.add(FireworkParticle(x, y, velocityX, velocityY, color))
        }
        lastFireworksUpdate = SystemClock.elapsedRealtime()

        fireworksAnimator?.cancel()
        fireworksAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fireworksDurationMs
            interpolator = AccelerateInterpolator(1.5f)

            addUpdateListener { _ ->
                val currentTime = SystemClock.elapsedRealtime()
                val deltaTime = ((currentTime - lastFireworksUpdate) / 1000f)
                lastFireworksUpdate = currentTime
                updateFireworks(deltaTime)
                invalidate()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isFireworksActive = false
                        fireworksParticles.clear()
                        fireworksAnimator = null
                        deactivateEasterEggMode() // Finish easter egg after fireworks end
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        isFireworksActive = false
                        fireworksParticles.clear()
                        fireworksAnimator = null
                        if (isEasterEggModeActive) deactivateEasterEggMode()
                    }
                },
            )
        }
        fireworksAnimator?.start()
    }

    private fun updateFireworks(deltaTime: Float) {
        if (deltaTime <= 0) return
        val iterator = fireworksParticles.iterator()
        val gravity = (9.8f * 50f)
        val airResistanceFactor = (1.0f - ((1.0f - 0.99f) * deltaTime * 60f))

        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.velocityY += (gravity * deltaTime)
            particle.velocityX *= airResistanceFactor
            particle.velocityY *= airResistanceFactor
            particle.x += (particle.velocityX * deltaTime)
            particle.y += (particle.velocityY * deltaTime)
            particle.life -= (deltaTime * (1f / (fireworksDurationMs / 1000f)))
            if (particle.life <= 0f) {
                iterator.remove()
            } else {
                // Keep particles bright for first 80% of life, then fade
                val fadeStart = 0.2f
                val alphaFactor = if (particle.life < fadeStart) (particle.life / fadeStart) else 1.0f
                particle.alpha = (255 * alphaFactor).toInt().coerceIn(0, 255)
            }
        }
    }

    // --- Main Drawing Loop ---
    override fun onDraw(canvas: Canvas) {
        // SAFETY GUARD: Prevents the blank screen bug
        if ((width == 0) || (height == 0)) return
        super.onDraw(canvas)

        // SAFETY GUARD: Explicitly clear the background
        canvas.drawColor(Color.BLACK)

        val cx = (width / 2f)
        val cy = (height / 2f)
        canvas.translate(cx, cy)

        val edgeRadius = min(cx, cy)
        val mainRadius = (edgeRadius * 0.90f)

        // Update paint colors for Theme/Ambient
        updatePaintsForTheme()

        // 1) Draw Bezel
        if (!isAmbientMode) {
            canvas.withRotation(bezelRotationDeg) { drawBezel(this, edgeRadius) }
        } else {
            // Simplified bezel for ambient
            drawAmbientBezel(canvas, edgeRadius)
        }

        // 2) Draw Bearing Marker
        if (!isAmbientMode) {
            drawBearingMarker(canvas, edgeRadius)
        }

        if (isEasterEggModeActive && !isAmbientMode) {
            drawChaseScene(canvas, mainRadius)
            if (isFireworksActive) {
                drawFireworks(canvas)
            }
        } else {
            // --- DRAW COMPASS ELEMENTS ---

            // The magnetic heading is the raw azimuth from the sensor (filtered)
            // In a standard compass app, if you want to show where "North" is relative to the device, 
            // you rotate the whole canvas by -azimuth.
            canvas.withRotation(-magneticNeedleDeg) {
                // Draw Magnetic North Marker (RED Triangle) at 0 degrees on the rotating rose
                drawNorthMarker(this, edgeRadius, magneticNeedlePaint)

                // Draw True North Marker (BLUE Triangle) offset by declination
                // True North = Magnetic North + Declination
                canvas.withRotation(declDeg) {
                    drawNorthMarker(this, edgeRadius, trueNorthNeedlePaint)
                }
            }

            // Waypoint Needle
            if (!isAmbientMode) {
                val currentReferenceHeading = if (showTrueNorth) (magneticNeedleDeg - declDeg) else magneticNeedleDeg
                waypointBearing?.let { wpBearing ->
                    canvas.withRotation(-(currentReferenceHeading - wpBearing)) {
                        drawGoToNeedle(this, mainRadius, goToNeedlePaint)
                    }
                }
            }

            if (!isAmbientMode) {
                drawModeSymbol(canvas, mainRadius)
                drawBubbleLevel(canvas, mainRadius)
                drawAccuracyIndicator(canvas, mainRadius)
            }
            
            drawReadouts(canvas)
            
            if (!isAmbientMode && (sensorAccuracyLevel == SensorManager.SENSOR_STATUS_UNRELIABLE)) {
                drawCalibrationWarning(canvas, mainRadius)
            }
        }

        if (showSecretText && !isAmbientMode) {
            drawSecretTextOverlay(canvas)
        }
    }

    private fun updatePaintsForTheme() {
        if (isAmbientMode) {
            val ambientColor = Color.WHITE
            bezelPaint.color = ambientColor
            bezelPaint.strokeWidth = 1f
            readoutPaint.color = ambientColor
            magneticReadoutPaint.color = ambientColor
            trueNorthReadoutPaint.color = ambientColor
            magneticNeedlePaint.color = ambientColor
            magneticNeedlePaint.strokeWidth = 4f
            trueNorthNeedlePaint.color = ambientColor
            trueNorthNeedlePaint.strokeWidth = 4f
            return
        }

        val primary = when (currentThemeIndex) {
            1 -> Color.RED
            2 -> Color.GREEN
            else -> Color.WHITE
        }
        val accent = when (currentThemeIndex) {
            1 -> Color.RED
            2 -> Color.GREEN
            else -> "#ADD8E6".toColorInt()
        }
        
        bezelPaint.color = primary
        bezelPaint.strokeWidth = 2f
        readoutPaint.color = primary
        magneticReadoutPaint.color = Color.RED
        trueNorthReadoutPaint.color = if (currentThemeIndex != 0) primary else Color.BLUE
        magneticNeedlePaint.color = Color.RED
        magneticNeedlePaint.strokeWidth = 8f
        trueNorthNeedlePaint.color = if (currentThemeIndex != 0) primary else Color.BLUE
        trueNorthNeedlePaint.strokeWidth = 8f
        bearingMarkerPaint.color = accent
        catSymbolMagneticPaint.color = Color.RED
        catSymbolTrueNorthPaint.color = if (currentThemeIndex != 0) primary else Color.BLUE
        bubbleLevelOutlinePaint.color = if (currentThemeIndex != 0) primary else Color.DKGRAY
        bubbleLevelBubblePaint.color = if (currentThemeIndex == 1) "#44FF0000".toColorInt() else if (currentThemeIndex == 2) "#4400FF00".toColorInt() else "#88FFFFFF".toColorInt()
        goToNeedlePaint.color = if (currentThemeIndex != 0) primary else Color.YELLOW
    }

    private fun drawAmbientBezel(canvas: Canvas, radius: Float) {
        // Just draw 4 major ticks for ambient mode to save pixels
        for (angle in 0 until 360 step 90) {
            val tickInner = (radius * 0.90f)
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            canvas.drawLine((radius * sin(rad)), (-radius * cos(rad)), (tickInner * sin(rad)), (-tickInner * cos(rad)), bezelPaint)
        }
    }

    private fun drawCalibrationWarning(canvas: Canvas, radius: Float) {
        val warningPaint = Paint().apply {
            color = if (currentThemeIndex == 2) Color.GREEN else Color.RED
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            alpha = (127 + 128 * sin(SystemClock.elapsedRealtime() * 0.008f)).toInt().coerceIn(0, 255)
            typeface = Typeface.DEFAULT_BOLD
        }
        
        // Draw Figure-8 path above the text
        val f8Radius = radius * 0.08f
        val f8CenterY = -radius * 0.55f
        
        // Simple figure-8 approximation with two circles
        warningPaint.style = Paint.Style.STROKE
        warningPaint.strokeWidth = 3f
        canvas.drawCircle(-f8Radius, f8CenterY, f8Radius, warningPaint)
        canvas.drawCircle(f8Radius, f8CenterY, f8Radius, warningPaint)
        
        warningPaint.style = Paint.Style.FILL
        canvas.drawText("CALIBRATE (8)", 0f, -radius * 0.4f, warningPaint)
    }

    // --- Private Drawing Helpers ---
    private fun drawNorthMarker(canvas: Canvas, radius: Float, paint: Paint) {
        val markerInnerY = (-radius * 0.90f)
        val markerHeight = (radius * 0.08f)
        val markerOuterY = (markerInnerY - markerHeight)
        val markerWidth = (radius * 0.10f)
        
        val path = Path()
        path.moveTo(0f, markerOuterY)
        path.lineTo(-(markerWidth / 2f), markerInnerY)
        path.lineTo((markerWidth / 2f), markerInnerY)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawBearingMarker(canvas: Canvas, radius: Float) {
        val markerOuterY = (-radius * 1.0f)
        val markerHeight = (radius * 0.1f)
        val markerInnerY = (markerOuterY + markerHeight)
        val markerWidth = (radius * 0.12f)
        val path = Path()
        path.moveTo(0f, markerOuterY)
        path.lineTo(-(markerWidth / 2f), markerInnerY)
        path.lineTo((markerWidth / 2f), markerInnerY)
        path.close()
        canvas.drawPath(path, bearingMarkerPaint)
    }

    private fun drawBezel(canvas: Canvas, radius: Float) {
        for (angle in 0 until 360 step 15) {
            val isMajor = ((angle % 90) == 0)
            val tickInner = if (isMajor) (radius * 0.90f) else (radius * 0.95f)
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            canvas.drawLine((radius * sin(rad)), (-radius * cos(rad)), (tickInner * sin(rad)), (-tickInner * cos(rad)), bezelPaint)
        }
        val textRadius = (radius * 0.80f)
        for ((angle, label) in listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            readoutPaint.getTextBounds(label, 0, label.length, tempTextBounds)
            val textHeightOffset = (tempTextBounds.height() / 2f)
            canvas.drawText(label, (textRadius * sin(rad)), ((-textRadius * cos(rad)) + textHeightOffset), readoutPaint)
        }
    }

    private fun drawNeedle(canvas: Canvas, radius: Float, paint: Paint) {
        val innerNeedleRadius = (radius * 0.3f)
        val outerNeedleRadius = (radius * 0.9f)
        canvas.drawLine(0f, -innerNeedleRadius, 0f, -outerNeedleRadius, paint)
    }

    private fun drawModeSymbol(canvas: Canvas, radius: Float) {
        val currentSymbol: String
        val currentPaint: Paint
        if (showTrueNorth) {
            currentSymbol = trueNorthSymbol
            currentPaint = catSymbolTrueNorthPaint
        } else {
            currentSymbol = magneticNorthSymbol
            currentPaint = catSymbolMagneticPaint
        }

        currentPaint.getTextBounds(currentSymbol, 0, currentSymbol.length, tempTextBounds)
        val textHeight = tempTextBounds.height().toFloat()
        val textWidth = currentPaint.measureText(currentSymbol)
        val yOffset = ((textHeight / 2f) - tempTextBounds.bottom)

        val symbolX = (-radius * 0.60f)
        val symbolY = 0f
        canvas.drawText(currentSymbol, symbolX, (symbolY + yOffset), currentPaint)

        symbolBounds.apply {
            set(
                (symbolX - (textWidth / 2f)) - symbolClickPadding,
                ((symbolY + yOffset) - (textHeight / 2f)) - symbolClickPadding,
                (symbolX + (textWidth / 2f)) + symbolClickPadding,
                ((symbolY + yOffset) + (textHeight / 2f)) + symbolClickPadding,
            )
        }
    }

    private fun drawBubbleLevel(canvas: Canvas, radius: Float) {
        val levelCenterX = (radius * 0.60f)
        val levelCenterY = 0f
        val levelRadius = (radius * 0.15f)
        canvas.drawCircle(levelCenterX, levelCenterY, levelRadius, bubbleLevelOutlinePaint)

        val maxAngleMap = 45f
        val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        var bubbleOffsetX = (-levelRadius * sin(rollRad) * (90f / maxAngleMap))
        var bubbleOffsetY = (levelRadius * sin(pitchRad) * (90f / maxAngleMap))

        val totalOffset = sqrt((bubbleOffsetX * bubbleOffsetX) + (bubbleOffsetY * bubbleOffsetY))
        val bubbleRadius = (levelRadius * 0.3f)
        if (totalOffset > (levelRadius - bubbleRadius)) {
            val scale = ((levelRadius - bubbleRadius) / totalOffset)
            bubbleOffsetX *= scale
            bubbleOffsetY *= scale
        }
        canvas.drawCircle((levelCenterX + bubbleOffsetX), (levelCenterY + bubbleOffsetY), bubbleRadius, bubbleLevelBubblePaint)
    }

    private fun drawSecretTextOverlay(canvas: Canvas) {
        // Center the callsign and clear the area behind it for maximum visibility
        canvas.drawColor(Color.BLACK)
        
        // Draw Callsign
        secretTextPaint.getTextBounds(secretText, 0, secretText.length, tempTextBounds)
        val secretYOffset = (tempTextBounds.height() / 2f) - tempTextBounds.bottom
        canvas.drawText(secretText, 0f, secretYOffset - 50f, secretTextPaint)
        
        // Draw Version below it
        canvas.drawText(versionText, 0f, secretYOffset + 10f, versionTextPaint)
        
        // Draw Donation Request
        canvas.drawText("Support: PayPal/Zelle/Interac", 0f, secretYOffset + 60f, donationTextPaint)
        canvas.drawText(donationEmail, 0f, secretYOffset + 90f, donationTextPaint)
    }

    private fun drawReadouts(canvas: Canvas) {
        val magneticHeading = magneticNeedleDeg
        val trueHeading = (((magneticNeedleDeg - declDeg) + 360f) % 360f)
        val headingToReference = if (showTrueNorth) trueHeading else magneticHeading

        val bearing = (((360f - bezelRotationDeg) + 360f) % 360f)
        val bearingSuffix = if (showTrueNorth) "°T" else "°M"
        val lineBRGUpdated = "BRG ${bearing.toInt()}$bearingSuffix"
        val lineSpd = "Spd: %.1f km/h".format(speedKmh)
        val lineAlt = "Alt: %.0f m".format(altitudeM)
        val lineLocal = "Loc: $localTime"
        val lineDecl = "Decl: %+.1f°".format(declDeg)
        val lineLat = "Lat: $latStr"
        val lineLon = "Lon: $lonStr"
        val lines = listOf(lineBRGUpdated, lineSpd, lineAlt, lineLocal, lineDecl, lineLat, lineLon)

        val textHeight = (readoutPaint.descent() - readoutPaint.ascent())
        val lineSpacing = (textHeight * 1.15f)
        val totalHeight = ((lines.size - 1) * lineSpacing)
        var currentY = ((-totalHeight / 2f) - readoutPaint.ascent())

        val brgPaint = if (showTrueNorth) trueNorthReadoutPaint else magneticReadoutPaint
        for ((index, line) in lines.withIndex()) {
            val paintToUse = if (index == 0) brgPaint else readoutPaint
            canvas.drawText(line, 0f, currentY, paintToUse)
            currentY += lineSpacing
        }
    }

    private fun drawGoToNeedle(canvas: Canvas, radius: Float, paint: Paint) {
        val finalInnerRadius = (radius * 0.65f)
        val finalOuterRadius = (radius * 0.80f)
        canvas.drawLine(0f, -finalInnerRadius, 0f, -finalOuterRadius, paint)
    }

    private fun drawAccuracyIndicator(canvas: Canvas, radius: Float) {
        val indicatorRadius = (radius * 0.05f)
        val indicatorX = (radius * 0.60f)
        val indicatorY = ((radius * 0.15f) + (indicatorRadius * 2.5f))
        val paintToUse = when (sensorAccuracyLevel) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> accuracyPaintHigh
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> accuracyPaintMedium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> accuracyPaintLow
            else -> accuracyPaintUnreliable
        }
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius, paintToUse)
    }

    private fun drawChaseScene(canvas: Canvas, radius: Float) {
        val chaseContainerRadius = (radius * 0.90f)
        val maxAngleMap = 45f

        val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        var targetCatX = (-chaseContainerRadius * sin(rollRad) * (90f / maxAngleMap))
        var targetCatY = (chaseContainerRadius * sin(pitchRad) * (90f / maxAngleMap))

        val catTotalOffset = sqrt((targetCatX * targetCatX) + (targetCatY * targetCatY))
        if (catTotalOffset > chaseContainerRadius) {
            val scale = (chaseContainerRadius / catTotalOffset)
            targetCatX *= scale
            targetCatY *= scale
        }

        val dxCat = (targetCatX - catPositionX)
        val dyCat = (targetCatY - catPositionY)
        val distMovedCat = sqrt((dxCat * dxCat) + (dyCat * dyCat))
        val currentTime = SystemClock.elapsedRealtime()

        if (distMovedCat > 2.0f) {
            lastCatMoveTimeMs = currentTime
        }
        catPositionX = targetCatX
        catPositionY = targetCatY

        if (!isCaught) {
            val deltaX = (catPositionX - squirrelPositionX)
            val deltaY = (catPositionY - squirrelPositionY)
            val distance = sqrt((deltaX * deltaX) + (deltaY * deltaY))

            if (distance < catchThresholdPx) {
                isCaught = true
                squirrelPositionX = catPositionX
                squirrelPositionY = catPositionY
                performHapticFeedback(HapticFeedbackConstants.REJECT)
                // Start fireworks at the point of capture
                startFireworksAnimation(catPositionX, catPositionY)
            } else {
                if ((currentTime - lastCatMoveTimeMs) > catMoveTimeoutMs) {
                    if (distance > 0) {
                        val moveX = ((deltaX / distance) * (distance * squirrelSpeedFactor))
                        val moveY = ((deltaY / distance) * (distance * squirrelSpeedFactor))
                        squirrelPositionX += moveX
                        squirrelPositionY += moveY
                    }
                }
            }
        } else {
            squirrelPositionX = catPositionX
            squirrelPositionY = catPositionY
        }

        val fireworksMidPoint = (fireworksDurationMs / 3f)
        val currentPlayTime = (fireworksAnimator?.currentPlayTime ?: 0L).toFloat()
        val drawSymbolsDuringFireworks = (!isFireworksActive) || (currentPlayTime < fireworksMidPoint) ||
                (currentPlayTime > (fireworksDurationMs - fireworksMidPoint))

        if (drawSymbolsDuringFireworks) {
            val currentCatSymbol: String
            val currentCatPaint: Paint
            if (showTrueNorth) {
                currentCatSymbol = trueNorthSymbol
                currentCatPaint = catSymbolTrueNorthPaint
            } else {
                currentCatSymbol = magneticNorthSymbol
                currentCatPaint = catSymbolMagneticPaint
            }

            currentCatPaint.getTextBounds(currentCatSymbol, 0, currentCatSymbol.length, tempTextBounds)
            val catYOffset = ((tempTextBounds.height() / 2f) - tempTextBounds.bottom)

            squirrelPaint.getTextBounds(squirrelSymbol, 0, squirrelSymbol.length, tempTextBounds)
            val squirrelYOffset = ((tempTextBounds.height() / 2f) - tempTextBounds.bottom)

            canvas.drawText(currentCatSymbol, catPositionX, (catPositionY + catYOffset), currentCatPaint)
            canvas.drawText(squirrelSymbol, squirrelPositionX, (squirrelPositionY + squirrelYOffset), squirrelPaint)
        }
    }

    private fun drawFireworks(canvas: Canvas) {
        if ((!isFireworksActive) || fireworksParticles.isEmpty()) return
        for (particle in fireworksParticles) {
            fireworksParticlePaint.color = particle.color
            fireworksParticlePaint.alpha = particle.alpha
            // Sparkle effect: vary radius slightly based on current time
            val sparkle = (1.0f + (0.3f * sin(SystemClock.elapsedRealtime() * 0.02f)))
            val radius = (fireworksParticlePaint.strokeWidth / 2f) * sparkle
            canvas.drawCircle(particle.x, particle.y, radius, fireworksParticlePaint)
        }
    }

    data class FireworkParticle(
        var x: Float, var y: Float,
        var velocityX: Float, var velocityY: Float,
        val color: Int, var alpha: Int = 255,
        var life: Float = 1.0f,
    )

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mediaPlayer?.release()
        mediaPlayer = null
        interactionHandler.removeCallbacksAndMessages(null)
        longPressRunnable = null
        singleTapRunnable = null
        fireworksAnimator?.cancel()
        fireworksAnimator = null
    }
}