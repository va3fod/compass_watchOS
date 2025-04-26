package com.compass_gpt.presentation

// Base Imports from User
import android.content.Context
import android.graphics.*
import android.hardware.SensorManager // Import SensorManager constants
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import com.compass_gpt.R // Import R for raw resource
import kotlin.math.*
// Additional required imports
import android.view.HapticFeedbackConstants // For haptic feedback
import android.os.SystemClock // Import SystemClock for elapsedRealtime
import android.animation.Animator // Base class for AnimatorListenerAdapter
import android.animation.AnimatorListenerAdapter // To know when animation ends
import android.animation.ValueAnimator // For fireworks animation
import android.view.animation.AccelerateInterpolator // For fireworks effect
import kotlin.random.Random // For random firework colors


class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- Constants ---
    private val LONG_PRESS_DURATION_MS = 3000L
    private val DOUBLE_TAP_TIMEOUT_MS = 300L
    // Easter Egg duration is now controlled by FIREWORKS_DURATION_MS
    private val SINGLE_TAP_CONFIRM_DELAY_MS = DOUBLE_TAP_TIMEOUT_MS + 50L
    // Easter Egg Chase Constants
    private val SQUIRREL_CATCH_THRESHOLD_DP = 15f
    private val SQUIRREL_SPEED_FACTOR = 0.02f // Slower squirrel
    private val CAT_MOVE_TIMEOUT_MS = 400L
    // Fireworks Constants
    private val FIREWORKS_DURATION_MS = 3000L // 3 seconds duration
    private val FIREWORKS_PARTICLE_COUNT = 50 // Number of particles per burst
    private val FIREWORKS_MAX_RADIUS_FACTOR = 0.4f // Max distance particles travel relative to mainRadius
    private val FIREWORKS_MIN_SPEED_FACTOR = 0.8f // Min initial speed factor
    private val FIREWORKS_MAX_SPEED_FACTOR = 1.4f // Max initial speed factor


    // --- Paints ---
    private val bezelPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val readoutPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val magneticReadoutPaint = Paint().apply {
        color = Color.RED
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val trueNorthReadoutPaint = Paint().apply {
        color = Color.BLUE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val magneticNeedlePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val trueNorthNeedlePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val bearingMarkerPaint = Paint().apply {
        color = "#ADD8E6".toColorInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val catSymbolMagneticPaint = Paint().apply {
        color = Color.RED
        textSize = 45f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val catSymbolTrueNorthPaint = Paint().apply {
        color = Color.BLUE
        textSize = 45f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val bubbleLevelOutlinePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val bubbleLevelBubblePaint = Paint().apply {
        color = Color.parseColor("#88FFFFFF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val secretTextPaint = Paint().apply {
        color = Color.RED
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    // Optional boundary paint - uncomment if needed for debug
    // private val catLevelBoundaryPaint = Paint().apply { /* ... */ }
    private val goToNeedlePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val accuracyPaintHigh = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
    private val accuracyPaintMedium = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val accuracyPaintLow = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val accuracyPaintUnreliable = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL }
    private val squirrelPaint = Paint().apply {
        color = Color.parseColor("#FFA500") // Orange-ish
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val fireworksParticlePaint = Paint().apply {
        // Color will be set per particle
        strokeWidth = 5f // Make particles visible
        style = Paint.Style.FILL // Draw dots/circles
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val fireworkColors = listOf(
        Color.YELLOW, Color.RED, Color.WHITE, Color.CYAN, Color.MAGENTA, Color.GREEN
    )


    // --- Path & Symbols ---
    private val bearingMarkerPath = Path()
    private val magneticNorthSymbol = "🐈"
    private val trueNorthSymbol = "🐾"
    private val secretText = "VA3FOD"
    private val squirrelSymbol = "🐿️"   // ha ha ha

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
    private var showTrueNorth = false
    private var symbolBounds = RectF()
    private val symbolClickPadding = 20f
    private var isHoldingSymbol = false
    private var showSecretText = false
    private var lastTapTimeMs = 0L
    private var isEasterEggModeActive = false
    private var sensorAccuracyLevel: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    private val interactionHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    // Timeout runnable removed, handled by fireworks end
    private var singleTapRunnable: Runnable? = null
    private var mediaPlayer: MediaPlayer? = null
    private var catPositionX: Float = 0f
    private var catPositionY: Float = 0f
    private var squirrelPositionX: Float = 0f
    private var squirrelPositionY: Float = 0f
    private var isCaught: Boolean = false
    private var lastCatMoveTimeMs: Long = 0L
    private val catchThresholdPx: Float
    private var isFireworksActive: Boolean = false
    private val fireworksParticles = mutableListOf<FireworkParticle>()
    private var fireworksAnimator: ValueAnimator? = null


    // --- Initialization ---
    init {
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.meow)
            mediaPlayer?.setOnErrorListener { mp, what, extra -> true }
        } catch (e: Exception) { mediaPlayer = null }
        isHapticFeedbackEnabled = true
        catchThresholdPx = SQUIRREL_CATCH_THRESHOLD_DP * resources.displayMetrics.density
    }

    // --- Public Methods (setters) ---
    fun setSensorData(azimuth: Float, pitch: Float, roll: Float) {
        magneticNeedleDeg = (azimuth % 360f + 360f) % 360f
        this.pitchDeg = pitch
        this.rollDeg = roll
        invalidate()
    }
    fun setBezelRotation(angle: Float) {
        bezelRotationDeg = (angle % 360f + 360f) % 360f
        invalidate()
    }
    fun setGpsData(speed: Float, altitude: Float, decl: Float, local: String, utc: String) {
        speedKmh = speed
        altitudeM = altitude
        declDeg = decl
        localTime = local
        utcTime = utc
        invalidate()
    }
    fun setSensorAccuracy(accuracy: Int) {
        if (accuracy != sensorAccuracyLevel) {
            sensorAccuracyLevel = accuracy
            invalidate()
        }
    }


    // --- Touch Event Handling ---
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (isEasterEggModeActive) return true

        if (event == null) return super.onTouchEvent(event)
        val touchX = event.x - width / 2f
        val touchY = event.y - height / 2f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                singleTapRunnable?.let { interactionHandler.removeCallbacks(it) }
                singleTapRunnable = null

                if (symbolBounds.contains(touchX, touchY)) {
                    isHoldingSymbol = true
                    showSecretText = false
                    longPressRunnable = Runnable {
                        if (isHoldingSymbol) {
                            showSecretText = true
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            invalidate()
                        }
                    }
                    interactionHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION_MS)
                    return true
                }
                lastTapTimeMs = 0L
            }

            MotionEvent.ACTION_MOVE -> {
                if (isHoldingSymbol && !symbolBounds.contains(touchX, touchY)) {
                    resetSymbolInteractionState()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoldingSymbol) {
                    val currentTime = System.currentTimeMillis()
                    val wasLongPress = showSecretText
                    val wasHolding = isHoldingSymbol
                    resetSymbolInteractionState()

                    if (event.action == MotionEvent.ACTION_UP && symbolBounds.contains(touchX, touchY)) {
                        if (wasLongPress) {
                            // Long press release
                        } else if (currentTime - lastTapTimeMs <= DOUBLE_TAP_TIMEOUT_MS) {
                            // Double tap
                            lastTapTimeMs = 0L
                            activateEasterEggMode()
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        } else {
                            // Potential single tap
                            lastTapTimeMs = currentTime
                            singleTapRunnable = Runnable {
                                showTrueNorth = !showTrueNorth
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                invalidate()
                                singleTapRunnable = null
                            }
                            interactionHandler.postDelayed(singleTapRunnable!!, SINGLE_TAP_CONFIRM_DELAY_MS)
                        }
                    } else {
                        lastTapTimeMs = 0L
                    }
                    if (wasHolding) return true
                }
                lastTapTimeMs = 0L
            }
        }
        return super.onTouchEvent(event)
    }


    // --- Reset Interaction State ---
    private fun resetSymbolInteractionState() {
        longPressRunnable?.let { interactionHandler.removeCallbacks(it) }
        longPressRunnable = null
        singleTapRunnable?.let { interactionHandler.removeCallbacks(it) }
        singleTapRunnable = null

        val needsRedraw = showSecretText || isHoldingSymbol
        isHoldingSymbol = false
        showSecretText = false
        if (needsRedraw) {
            invalidate()
        }
    }

    // --- Activate Easter Egg (Chase Mode) ---
    private fun activateEasterEggMode() {
        if (isEasterEggModeActive) return
        resetSymbolInteractionState()

        isEasterEggModeActive = true
        isCaught = false
        isFireworksActive = false
        fireworksAnimator?.cancel()
        fireworksParticles.clear()

        // Calculate initial Cat position based on current tilt
        val chaseContainerRadius = (min(width / 2f, height / 2f) * 0.90f) * 0.90f // Use estimated radius
        val maxAngleMap = 45f
        val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        catPositionX = -chaseContainerRadius * sin(rollRad) * (90f / maxAngleMap)
        catPositionY = chaseContainerRadius * sin(pitchRad) * (90f / maxAngleMap)
        // Clamp initial cat position
        val catTotalOffset = sqrt(catPositionX.pow(2) + catPositionY.pow(2))
        if (catTotalOffset > chaseContainerRadius) {
            val scale = chaseContainerRadius / catTotalOffset
            catPositionX *= scale
            catPositionY *= scale
        }


        // Calculate opposite angle for squirrel start
        val catAngleRad = atan2(catPositionY, catPositionX)
        val squirrelAngleRad = catAngleRad + PI.toFloat()
        val squirrelDist = chaseContainerRadius * 0.8f
        squirrelPositionX = squirrelDist * cos(squirrelAngleRad)
        squirrelPositionY = squirrelDist * sin(squirrelAngleRad)


        lastCatMoveTimeMs = SystemClock.elapsedRealtime()

        if (mediaPlayer?.isPlaying == false) {
            try { mediaPlayer?.seekTo(0); mediaPlayer?.start() }
            catch (e: Exception) { /* Handle error */ }
        }

        // No fixed timeout runnable needed anymore

        invalidate()
    }

    // --- Deactivate Easter Egg ---
    private fun deactivateEasterEggMode() {
        isEasterEggModeActive = false
        // Timeout runnable removed
        fireworksAnimator?.cancel()
        isFireworksActive = false
        lastTapTimeMs = 0L
        invalidate()
    }

    // --- Start Fireworks Animation ---
    private fun startFireworksAnimation(x: Float, y: Float) {
        if (isFireworksActive) return

        isFireworksActive = true
        fireworksParticles.clear()

        val maxRadiusPx = (min(width / 2f, height / 2f) * 0.90f) * FIREWORKS_MAX_RADIUS_FACTOR
        for (i in 0 until FIREWORKS_PARTICLE_COUNT) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * (FIREWORKS_MAX_SPEED_FACTOR - FIREWORKS_MIN_SPEED_FACTOR) + FIREWORKS_MIN_SPEED_FACTOR
            val velocityX = cos(angle) * speed * maxRadiusPx / (FIREWORKS_DURATION_MS / 16f)
            val velocityY = sin(angle) * speed * maxRadiusPx / (FIREWORKS_DURATION_MS / 16f)
            val color = fireworkColors.random()
            fireworksParticles.add(FireworkParticle(x, y, velocityX, velocityY, color))
        }


        fireworksAnimator?.cancel()
        fireworksAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FIREWORKS_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)

            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                updateFireworks(progress)
                invalidate()
            }
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isFireworksActive = false
                    fireworksParticles.clear()
                    fireworksAnimator = null
                    // --- END EASTER EGG HERE ---
                    deactivateEasterEggMode()
                }
                override fun onAnimationCancel(animation: Animator) {
                    isFireworksActive = false
                    fireworksParticles.clear()
                    fireworksAnimator = null
                    if (isEasterEggModeActive) {
                        deactivateEasterEggMode()
                    }
                }
            })
        }
        fireworksAnimator?.start()
    }

    // --- Update Fireworks Particles ---
    private fun updateFireworks(progress: Float) {
        val iterator = fireworksParticles.iterator()
        while(iterator.hasNext()){
            val particle = iterator.next()
            particle.x += particle.velocityX
            particle.y += particle.velocityY
            if (progress > 0.5f) {
                particle.alpha = (255 * (1f - (progress - 0.5f) * 2)).toInt().coerceIn(0, 255)
            }
        }
        // Remove particles that are fully faded? (Optional)
        // fireworksParticles.removeAll { it.alpha == 0 }
    }


    // --- Drawing Logic ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy)

        val edgeRadius = min(cx, cy)
        val mainRadius = edgeRadius * 0.90f

        // 1) Draw Bezel (Always)
        canvas.withRotation(bezelRotationDeg) { drawBezel(this, edgeRadius) }

        // 3) Draw Bearing Marker (Always)
        drawBearingMarker(canvas, edgeRadius)


        if (isEasterEggModeActive) {
            // --- Draw Easter Egg Scene ---
            drawChaseScene(canvas, mainRadius)
            // --- Draw Fireworks if active ---
            if (isFireworksActive) {
                drawFireworks(canvas)
            }

        } else {
            // --- Draw Normal Compass Elements ---
            // 2) Draw North Needle & Go-To Needle
            val headingToShow: Float
            val currentNeedlePaint: Paint
            if (showTrueNorth) {
                headingToShow = (magneticNeedleDeg - declDeg + 360f) % 360f
                currentNeedlePaint = trueNorthNeedlePaint
            } else {
                headingToShow = magneticNeedleDeg
                currentNeedlePaint = magneticNeedlePaint
            }
            canvas.withRotation(-headingToShow) {
                drawNeedle(this, mainRadius, currentNeedlePaint)
            }
            drawGoToNeedle(canvas, mainRadius, goToNeedlePaint)

            // 4) Draw Static Mode Symbol
            drawModeSymbol(canvas, mainRadius)

            // 5) Draw Bubble Level & Accuracy Indicator
            drawBubbleLevel(canvas, mainRadius)
            drawAccuracyIndicator(canvas, mainRadius)

            // 6) Draw Readouts
            drawReadouts(canvas)
        }

        // 7) Draw Secret Text Overlay (Always drawn on top if active)
        if (showSecretText) {
            drawSecretTextOverlay(canvas, mainRadius)
        }
    }

    // --- Private Drawing Helpers ---

    private fun drawBearingMarker(canvas: Canvas, radius: Float) {
        val markerOuterY = -radius * 1.0f
        val markerHeight = radius * 0.1f
        val markerInnerY = markerOuterY + markerHeight
        val markerWidth = radius * 0.12f

        bearingMarkerPath.reset()
        bearingMarkerPath.moveTo(0f, markerOuterY)
        bearingMarkerPath.lineTo(-markerWidth / 2f, markerInnerY)
        bearingMarkerPath.lineTo(markerWidth / 2f, markerInnerY)
        bearingMarkerPath.close()
        canvas.drawPath(bearingMarkerPath, bearingMarkerPaint)
    }

    private fun drawBezel(canvas: Canvas, radius: Float) {
        for (angle in 0 until 360 step 15) {
            val isMajor = (angle % 90 == 0)
            val tickOuter = radius
            val tickInner = if (isMajor) radius * 0.90f else radius * 0.95f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val sinA = sin(rad)
            val cosA = cos(rad)
            canvas.drawLine(tickOuter * sinA, -tickOuter * cosA, tickInner * sinA, -tickInner * cosA, bezelPaint)
        }
        val textRadius = radius * 0.80f
        for ((angle, label) in listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val sinA = sin(rad)
            val cosA = cos(rad)
            val x = textRadius * sinA
            val y = -textRadius * cosA
            val textBounds = Rect()
            readoutPaint.getTextBounds(label, 0, label.length, textBounds)
            val textHeightOffset = textBounds.height() / 2f
            canvas.drawText(label, x, y + textHeightOffset, readoutPaint)
        }
    }

    private fun drawNeedle(canvas: Canvas, radius: Float, paint: Paint) {
        val innerNeedleRadius = radius * 0.3f
        val outerNeedleRadius = radius * 0.9f
        canvas.drawLine(0f, -innerNeedleRadius, 0f, -outerNeedleRadius, paint)
    }

    // Only draws the static symbol
    private fun drawModeSymbol(canvas: Canvas, radius: Float) {
        if (isEasterEggModeActive) return

        val currentSymbol: String
        val currentPaint: Paint
        if (showTrueNorth) {
            currentSymbol = trueNorthSymbol
            currentPaint = catSymbolTrueNorthPaint
        } else {
            currentSymbol = magneticNorthSymbol
            currentPaint = catSymbolMagneticPaint
        }
        val textBounds = Rect()
        currentPaint.getTextBounds(currentSymbol, 0, currentSymbol.length, textBounds)
        val textHeight = textBounds.height().toFloat()
        val textWidth = currentPaint.measureText(currentSymbol)
        val yOffset = textHeight / 2f - textBounds.bottom

        val symbolX = -radius * 0.60f // Static X position
        val symbolY = 0f             // Static Y position
        canvas.drawText(currentSymbol, symbolX, symbolY + yOffset, currentPaint)

        // Update clickable bounds ONLY when static
        val clickLeft = symbolX - textWidth / 2f - symbolClickPadding
        val clickTop = symbolY + yOffset - textHeight / 2f - symbolClickPadding
        val clickRight = symbolX + textWidth / 2f + symbolClickPadding
        val clickBottom = symbolY + yOffset + textHeight / 2f + symbolClickPadding
        symbolBounds.set(clickLeft, clickTop, clickRight, clickBottom)
    }

    private fun drawBubbleLevel(canvas: Canvas, radius: Float) {
        val levelCenterX = radius * 0.60f
        val levelCenterY = 0f
        val levelRadius = radius * 0.15f

        canvas.drawCircle(levelCenterX, levelCenterY, levelRadius, bubbleLevelOutlinePaint)

        val maxAngleMap = 45f
        val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()

        var bubbleOffsetX = -levelRadius * sin(rollRad) * (90f / maxAngleMap)
        var bubbleOffsetY = levelRadius * sin(pitchRad) * (90f / maxAngleMap)

        val totalOffset = sqrt(bubbleOffsetX.pow(2) + bubbleOffsetY.pow(2))
        val bubbleRadius = levelRadius * 0.3f

        if (totalOffset > levelRadius - bubbleRadius) {
            val scale = (levelRadius - bubbleRadius) / totalOffset
            bubbleOffsetX *= scale
            bubbleOffsetY *= scale
        }
        canvas.drawCircle(
            levelCenterX + bubbleOffsetX,
            levelCenterY + bubbleOffsetY,
            bubbleRadius,
            bubbleLevelBubblePaint
        )
    }

    private fun drawSecretTextOverlay(canvas: Canvas, mainRadius: Float) {
        val targetCenterY = -mainRadius * 0.5f
        val textBounds = Rect()
        secretTextPaint.getTextBounds(secretText, 0, secretText.length, textBounds)
        val yOffset = textBounds.height() / 2f - textBounds.bottom
        val drawY = targetCenterY + yOffset
        canvas.drawText(secretText, 0f, drawY, secretTextPaint)
    }

    private fun drawReadouts(canvas: Canvas) {
        val bearing = ((360f - bezelRotationDeg) % 360f + 360f) % 360f
        val bearingSuffix = if (showTrueNorth) "°T" else "°M"
        val lineBRGUpdated = "BRG ${bearing.toInt()}$bearingSuffix"
        val lineSpd = "Spd: %.1f km/h".format(speedKmh)
        val lineAlt = "Alt: %.0f m".format(altitudeM)
        val lineDecl = "Decl: %+.1f°".format(declDeg)
        val lineLocal = "Loc: $localTime"
        val lineUTC = "UTC: $utcTime"
        val lines = listOf(lineBRGUpdated, lineSpd, lineAlt, lineDecl, lineLocal, lineUTC)

        val textHeight = readoutPaint.descent() - readoutPaint.ascent()
        val lineSpacing = textHeight * 1.15f
        val totalHeight = (lines.size - 1) * lineSpacing
        var currentY = -totalHeight / 2f - readoutPaint.ascent()

        val brgPaint = if (showTrueNorth) trueNorthReadoutPaint else magneticReadoutPaint

        for ((index, line) in lines.withIndex()) {
            val paintToUse = if (index == 0) brgPaint else readoutPaint
            canvas.drawText(line, 0f, currentY, paintToUse)
            currentY += lineSpacing
        }
    }

    private fun drawGoToNeedle(canvas: Canvas, radius: Float, paint: Paint) {
        val finalInnerRadius = radius * 0.65f
        val finalOuterRadius = radius * 0.80f
        canvas.drawLine(0f, -finalInnerRadius, 0f, -finalOuterRadius, paint)
    }

    private fun drawAccuracyIndicator(canvas: Canvas, radius: Float) {
        val indicatorRadius = radius * 0.05f
        val indicatorX = radius * 0.60f
        val indicatorY = radius * 0.15f + indicatorRadius * 2.5f

        val paintToUse = when (sensorAccuracyLevel) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> accuracyPaintHigh
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> accuracyPaintMedium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> accuracyPaintLow
            else -> accuracyPaintUnreliable
        }
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius, paintToUse)
    }

    // --- Helper to draw the chase scene ---
    private fun drawChaseScene(canvas: Canvas, radius: Float) { // radius is mainRadius
        // Use larger chase area
        val chaseContainerRadius = radius * 0.90f
        val maxAngleMap = 45f

        // Optional: Draw boundary circle for debugging
        // canvas.drawCircle(0f, 0f, chaseContainerRadius, catLevelBoundaryPaint)

        // Cat Calculation
        val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        var targetCatX = -chaseContainerRadius * sin(rollRad) * (90f / maxAngleMap)
        var targetCatY = chaseContainerRadius * sin(pitchRad) * (90f / maxAngleMap)
        val catTotalOffset = sqrt(targetCatX.pow(2) + targetCatY.pow(2))
        if (catTotalOffset > chaseContainerRadius) {
            val scale = chaseContainerRadius / catTotalOffset
            targetCatX *= scale
            targetCatY *= scale
        }
        val dxCat = targetCatX - catPositionX
        val dyCat = targetCatY - catPositionY
        val distMovedCat = sqrt(dxCat*dxCat + dyCat*dyCat)
        val currentTime = SystemClock.elapsedRealtime()
        if (distMovedCat > 2.0f) { lastCatMoveTimeMs = currentTime }
        catPositionX = targetCatX
        catPositionY = targetCatY

        // Squirrel Calculation
        if (!isCaught) {
            val deltaX = catPositionX - squirrelPositionX
            val deltaY = catPositionY - squirrelPositionY
            val distance = sqrt(deltaX.pow(2) + deltaY.pow(2))

            if (distance < catchThresholdPx) {
                isCaught = true
                squirrelPositionX = catPositionX
                squirrelPositionY = catPositionY
                performHapticFeedback(HapticFeedbackConstants.REJECT)
                startFireworksAnimation(catPositionX, catPositionY) // Trigger fireworks
            } else {
                if (currentTime - lastCatMoveTimeMs > CAT_MOVE_TIMEOUT_MS) {
                    if (distance > 0) {
                        val moveX = deltaX / distance * (distance * SQUIRREL_SPEED_FACTOR)
                        val moveY = deltaY / distance * (distance * SQUIRREL_SPEED_FACTOR)
                        squirrelPositionX += moveX
                        squirrelPositionY += moveY
                    }
                }
            }
        } else { // Stay caught
            squirrelPositionX = catPositionX
            squirrelPositionY = catPositionY
        }

        // Drawing Symbols
        val currentCatSymbol: String
        val currentCatPaint: Paint
        if (showTrueNorth) { currentCatSymbol = trueNorthSymbol; currentCatPaint = catSymbolTrueNorthPaint }
        else { currentCatSymbol = magneticNorthSymbol; currentCatPaint = catSymbolMagneticPaint }

        val catTextBounds = Rect(); currentCatPaint.getTextBounds(currentCatSymbol, 0, currentCatSymbol.length, catTextBounds)
        val catYOffset = catTextBounds.height() / 2f - catTextBounds.bottom
        val squirrelTextBounds = Rect(); squirrelPaint.getTextBounds(squirrelSymbol, 0, squirrelSymbol.length, squirrelTextBounds)
        val squirrelYOffset = squirrelTextBounds.height() / 2f - squirrelTextBounds.bottom

        canvas.drawText(currentCatSymbol, catPositionX, catPositionY + catYOffset, currentCatPaint)
        canvas.drawText(squirrelSymbol, squirrelPositionX, squirrelPositionY + squirrelYOffset, squirrelPaint)
    }

    // --- Helper to draw particle-based fireworks ---
    private fun drawFireworks(canvas: Canvas) {
        if (!isFireworksActive || fireworksParticles.isEmpty()) return

        for (particle in fireworksParticles) {
            fireworksParticlePaint.color = particle.color
            fireworksParticlePaint.alpha = particle.alpha
            canvas.drawCircle(particle.x, particle.y, fireworksParticlePaint.strokeWidth / 2f, fireworksParticlePaint)
        }
    }

    // --- Data class for firework particles ---
    data class FireworkParticle(
        var x: Float,
        var y: Float,
        val velocityX: Float,
        val velocityY: Float,
        val color: Int,
        var alpha: Int = 255
    )

    // --- Resource Cleanup ---
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mediaPlayer?.release()
        mediaPlayer = null
        // Clear all runnables from the handler
        interactionHandler.removeCallbacksAndMessages(null)
        longPressRunnable = null
        singleTapRunnable = null
        // easterEggTimeoutRunnable removed
        // Cancel fireworks animator
        fireworksAnimator?.cancel()
        fireworksAnimator = null
    }
}