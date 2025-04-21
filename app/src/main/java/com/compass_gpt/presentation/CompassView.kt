package com.compass_gpt.presentation

// Imports provided by user (and HapticFeedbackConstants added)
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
import android.view.HapticFeedbackConstants // For haptic feedback


class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- Constants ---
    private val LONG_PRESS_DURATION_MS = 3000L
    private val DOUBLE_TAP_TIMEOUT_MS = 300L
    private val EASTER_EGG_DURATION_MS = 6000L
    private val SINGLE_TAP_CONFIRM_DELAY_MS = DOUBLE_TAP_TIMEOUT_MS + 50L
    private val CAT_LEVEL_SCALE = 1.8f

    // --- Paints ---
    private val bezelPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    // Readout paints: one default, two colored for BRG line
    private val readoutPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val magneticReadoutPaint = Paint().apply {
        color = Color.RED // Match magnetic needle
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val trueNorthReadoutPaint = Paint().apply {
        color = Color.BLUE // Match true north needle
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
    private val catLevelBoundaryPaint = Paint().apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        isAntiAlias = true
    }
    // --- Go-To Needle Paint ---
    private val goToNeedlePaint = Paint().apply {
        color = Color.YELLOW // Distinct color
        strokeWidth = 5f    // Thinner than main needle
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    // --- Sensor Accuracy Paints ---
    private val accuracyPaintHigh = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
    private val accuracyPaintMedium = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val accuracyPaintLow = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val accuracyPaintUnreliable = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL }


    // --- Path & Symbols ---
    private val bearingMarkerPath = Path()
    private val magneticNorthSymbol = "🐈"
    private val trueNorthSymbol = "🐾"
    private val secretText = "VA3FOD"

    // --- State Variables ---
    // Sensor Data
    private var magneticNeedleDeg = 0f
    private var pitchDeg = 0f
    private var rollDeg = 0f
    // GPS/Time Data
    private var bezelRotationDeg = 0f
    private var speedKmh = 0f
    private var altitudeM = 0f
    private var declDeg = 0f
    private var localTime = ""
    private var utcTime = ""
    // UI State
    private var showTrueNorth = false
    private var symbolBounds = RectF()
    private val symbolClickPadding = 20f
    // Interaction State
    private var isHoldingSymbol = false
    private var showSecretText = false
    private var lastTapTimeMs = 0L
    private var isCatLevelModeActive = false
    // Accuracy State
    private var sensorAccuracyLevel: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    // Handlers and Runnables
    private val interactionHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var catLevelTimeoutRunnable: Runnable? = null
    private var singleTapRunnable: Runnable? = null
    // Sound State
    private var mediaPlayer: MediaPlayer? = null

    // --- Initialization ---
    init {
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.meow) // Ensure R is imported
            mediaPlayer?.setOnErrorListener { mp, what, extra -> true }
        } catch (e: Exception) {
            // Log.e("CompassView", "Error loading media player", e)
            mediaPlayer = null
        }
        // Enable haptic feedback
        isHapticFeedbackEnabled = true
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
        if (isCatLevelModeActive) return true // Block interaction during easter egg

        if (event == null) return super.onTouchEvent(event)
        val touchX = event.x - width / 2f // Adjust coords relative to center
        val touchY = event.y - height / 2f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Cancel any pending single tap confirmation first
                singleTapRunnable?.let { interactionHandler.removeCallbacks(it) }
                singleTapRunnable = null

                if (symbolBounds.contains(touchX, touchY)) {
                    isHoldingSymbol = true
                    showSecretText = false // Reset secret text flag on new press

                    // Define and post the long press runnable
                    longPressRunnable = Runnable {
                        // This runs after LONG_PRESS_DURATION_MS if still holding
                        if (isHoldingSymbol) { // Double check if still holding
                            showSecretText = true
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) // Feedback
                            invalidate() // Redraw to show text
                        }
                    }
                    interactionHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION_MS)

                    return true // Consume the DOWN event
                }
                // If DOWN is outside symbol, reset double tap timer
                lastTapTimeMs = 0L
            }

            MotionEvent.ACTION_MOVE -> {
                // Optional: If finger moves OFF the symbol while holding, cancel the long press
                if (isHoldingSymbol && !symbolBounds.contains(touchX, touchY)) {
                    resetSymbolInteractionState() // Cancels long press AND pending single tap
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoldingSymbol) {
                    val currentTime = System.currentTimeMillis()
                    val wasLongPress = showSecretText // Did the long press already trigger?
                    val wasHolding = isHoldingSymbol // Remember hold state before resetting

                    // Always reset long press state first
                    resetSymbolInteractionState() // This cancels long press timer and pending single tap

                    // Only process tap/double tap if finger lifted UP on the symbol
                    if (event.action == MotionEvent.ACTION_UP && symbolBounds.contains(touchX, touchY)) {
                        if (wasLongPress) {
                            // Long press finished, do nothing on UP
                        } else if (currentTime - lastTapTimeMs <= DOUBLE_TAP_TIMEOUT_MS) {
                            // --- Double tap ---
                            lastTapTimeMs = 0L // Consume the double tap
                            activateCatLevelMode() // Start easter egg
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) // Haptic
                            // DO NOT schedule a single tap runnable here
                        } else {
                            // --- Potential single tap ---
                            lastTapTimeMs = currentTime // Record time for next potential tap

                            // Schedule the single tap action with a delay
                            singleTapRunnable = Runnable {
                                showTrueNorth = !showTrueNorth // Toggle North mode
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) // Tap feedback
                                invalidate()
                                singleTapRunnable = null // Clear self after running
                            }
                            interactionHandler.postDelayed(singleTapRunnable!!, SINGLE_TAP_CONFIRM_DELAY_MS)
                        }
                    } else {
                        // Finger lifted off symbol or ACTION_CANCEL - reset double tap timer
                        lastTapTimeMs = 0L
                    }
                    // Consume the event if we started holding
                    if (wasHolding) return true
                }
                // Reset double tap timer if we weren't holding (e.g., tap outside)
                lastTapTimeMs = 0L
            }
        }
        // If event wasn't consumed above, pass it on
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

    // --- Activate/Deactivate Cat Level Mode ---
    private fun activateCatLevelMode() {
        if (isCatLevelModeActive) return
        resetSymbolInteractionState() // Ensure other interactions stopped
        isCatLevelModeActive = true
        if (mediaPlayer?.isPlaying == false) {
            try { mediaPlayer?.seekTo(0); mediaPlayer?.start() }
            catch (e: Exception) { /* Handle error */ }
        }
        catLevelTimeoutRunnable = Runnable { deactivateCatLevelMode() }
        interactionHandler.postDelayed(catLevelTimeoutRunnable!!, EASTER_EGG_DURATION_MS)
        invalidate() // Initial draw in cat level mode
    }
    private fun deactivateCatLevelMode() {
        isCatLevelModeActive = false
        catLevelTimeoutRunnable?.let { interactionHandler.removeCallbacks(it) }
        catLevelTimeoutRunnable = null
        lastTapTimeMs = 0L // Reset tap timer after easter egg finishes
        invalidate() // Redraw in normal mode
    }


    // --- Drawing Logic ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy) // Origin is now center screen

        val edgeRadius = min(cx, cy)
        val mainRadius = edgeRadius * 0.90f

        // 1) Draw Bezel - Rotated internally for drawing ticks/labels
        canvas.withRotation(bezelRotationDeg) { drawBezel(this, edgeRadius) }

        // 2) Draw North Needle & Go-To Needle (Hide if cat level active)
        if (!isCatLevelModeActive) {
            // Calculate North heading relative to screen top
            val headingToShow: Float
            val currentNeedlePaint: Paint
            if (showTrueNorth) {
                headingToShow = (magneticNeedleDeg - declDeg + 360f) % 360f
                currentNeedlePaint = trueNorthNeedlePaint
            } else {
                headingToShow = magneticNeedleDeg
                currentNeedlePaint = magneticNeedlePaint
            }
            // Rotate canvas so UP points towards North
            canvas.withRotation(-headingToShow) {
                drawNeedle(this, mainRadius, currentNeedlePaint)
            }

            // Draw Go-To Needle (Points straight UP towards blue marker)
            // NO specific rotation needed here, drawn directly on translated canvas
            drawGoToNeedle(canvas, mainRadius, goToNeedlePaint)
        }

        // 3) Draw Bearing Marker (Fixed at screen top)
        drawBearingMarker(canvas, edgeRadius)

        // 4) Draw Mode Symbol (handles cat level mode internally)
        drawModeSymbol(canvas, mainRadius)

        // 5) Draw Bubble Level & Accuracy Indicator (Hide if cat level active)
        if (!isCatLevelModeActive) {
            drawBubbleLevel(canvas, mainRadius)
            drawAccuracyIndicator(canvas, mainRadius)
        }

        // 6) Draw Readouts (Hide if cat level active)
        if (!isCatLevelModeActive) {
            drawReadouts(canvas) // Uses colored BRG line internally now
        }

        // 7) Draw Secret Text Overlay
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
        val textBounds = Rect()
        currentPaint.getTextBounds(currentSymbol, 0, currentSymbol.length, textBounds)
        val textHeight = textBounds.height().toFloat()
        val textWidth = currentPaint.measureText(currentSymbol)
        val yOffset = textHeight / 2f - textBounds.bottom

        if (isCatLevelModeActive) {
            val catLevelContainerRadius = (radius * 0.35f) * CAT_LEVEL_SCALE
            val maxAngleMap = 45f
            canvas.drawCircle(0f, 0f, catLevelContainerRadius, catLevelBoundaryPaint)
            val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
            val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
            var catOffsetX = -catLevelContainerRadius * sin(rollRad) * (90f / maxAngleMap)
            var catOffsetY = catLevelContainerRadius * sin(pitchRad) * (90f / maxAngleMap)
            val totalOffset = sqrt(catOffsetX.pow(2) + catOffsetY.pow(2))
            if (totalOffset > catLevelContainerRadius) {
                val scale = catLevelContainerRadius / totalOffset
                catOffsetX *= scale
                catOffsetY *= scale
            }
            canvas.drawText(currentSymbol, catOffsetX, catOffsetY + yOffset, currentPaint)
        } else {
            val symbolX = -radius * 0.60f
            val symbolY = 0f
            canvas.drawText(currentSymbol, symbolX, symbolY + yOffset, currentPaint)
            val clickLeft = symbolX - textWidth / 2f - symbolClickPadding
            val clickTop = symbolY + yOffset - textHeight / 2f - symbolClickPadding
            val clickRight = symbolX + textWidth / 2f + symbolClickPadding
            val clickBottom = symbolY + yOffset + textHeight / 2f + symbolClickPadding
            symbolBounds.set(clickLeft, clickTop, clickRight, clickBottom)
        }
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
        // Start at 65%, End at 80% of mainRadius
        val finalInnerRadius = radius * 0.65f
        val finalOuterRadius = radius * 0.80f
        // Draw directly on the provided canvas (already translated to center)
        canvas.drawLine(0f, -finalInnerRadius, 0f, -finalOuterRadius, paint)
    }

    private fun drawAccuracyIndicator(canvas: Canvas, radius: Float) {
        val indicatorRadius = radius * 0.05f
        val indicatorX = radius * 0.60f // Same X as bubble level center
        val indicatorY = radius * 0.15f + indicatorRadius * 2.5f // Below bubble level with padding

        val paintToUse = when (sensorAccuracyLevel) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> accuracyPaintHigh
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> accuracyPaintMedium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> accuracyPaintLow
            else -> accuracyPaintUnreliable
        }
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius, paintToUse)
    }

    // --- Resource Cleanup ---
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mediaPlayer?.release()
        mediaPlayer = null
        // Clear all runnables from the handler
        interactionHandler.removeCallbacksAndMessages(null)
        longPressRunnable = null
        singleTapRunnable = null
        catLevelTimeoutRunnable = null
    }
}