package com.compass_gpt.presentation

// ... (Imports remain the same) ...
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import com.compass_gpt.R
import kotlin.math.*

class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- Constants ---
    private val LONG_PRESS_DURATION_MS = 3000L
    private val DOUBLE_TAP_TIMEOUT_MS = 300L
    private val EASTER_EGG_DURATION_MS = 6000L // 6 seconds
    private val SINGLE_TAP_CONFIRM_DELAY_MS = DOUBLE_TAP_TIMEOUT_MS + 50L
    // --- NEW: Scale factor for Cat Level mode ---
    private val CAT_LEVEL_SCALE = 1.8f // How much larger the movement area is


    // --- Paints ---
    // ... (All paints remain the same, including catLevelBoundaryPaint) ...
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


    // --- Path & Symbols ---
    // ... (Path and Symbols remain the same) ...
    private val bearingMarkerPath = Path()
    private val magneticNorthSymbol = "🐈"
    private val trueNorthSymbol = "🐾"
    private val secretText = "VA3FOD"


    // --- State Variables ---
    // ... (Sensor, GPS/Time, UI State remain the same) ...
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
    // Interaction State
    private var isHoldingSymbol = false
    private var showSecretText = false
    private var lastTapTimeMs = 0L
    private var isCatLevelModeActive = false // Back to this name
    // Handlers and Runnables
    private val interactionHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var catLevelTimeoutRunnable: Runnable? = null // Back to this name
    private var singleTapRunnable: Runnable? = null
    // Sound State
    private var mediaPlayer: MediaPlayer? = null


    // --- Initialization ---
    // ... (init block remains the same) ...
    init {
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.meow)
            mediaPlayer?.setOnErrorListener { mp, what, extra -> true }
        } catch (e: Exception) {
            mediaPlayer = null
        }
    }


    // --- Public Methods (setters) ---
    // ... (Setters remain the same) ...
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


    // --- Touch Event Handling ---
    // ... (onTouchEvent logic remains the same as previous Cat Level version) ...
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (isCatLevelModeActive) return true // Block interaction during easter egg

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
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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
                            activateCatLevelMode() // Activate Cat Level easter egg
                            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        } else {
                            // Potential single tap
                            lastTapTimeMs = currentTime
                            singleTapRunnable = Runnable {
                                showTrueNorth = !showTrueNorth
                                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
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
    // ... (resetSymbolInteractionState remains the same) ...
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

    // --- Activate Cat Level Mode ---
    // ... (activateCatLevelMode remains the same) ...
    private fun activateCatLevelMode() {
        if (isCatLevelModeActive) return
        resetSymbolInteractionState()

        isCatLevelModeActive = true

        if (mediaPlayer?.isPlaying == false) {
            try { mediaPlayer?.seekTo(0); mediaPlayer?.start() }
            catch (e: Exception) { /* Handle error */ }
        }

        catLevelTimeoutRunnable = Runnable { deactivateCatLevelMode() }
        interactionHandler.postDelayed(catLevelTimeoutRunnable!!, EASTER_EGG_DURATION_MS)

        invalidate()
    }


    // --- Deactivate Cat Level Mode ---
    // ... (deactivateCatLevelMode remains the same) ...
    private fun deactivateCatLevelMode() {
        isCatLevelModeActive = false
        catLevelTimeoutRunnable?.let { interactionHandler.removeCallbacks(it) }
        catLevelTimeoutRunnable = null
        lastTapTimeMs = 0L
        invalidate()
    }


    // --- Drawing Logic ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy)

        val edgeRadius = min(cx, cy)
        val mainRadius = edgeRadius * 0.90f

        // 1) Draw Bezel
        canvas.withRotation(bezelRotationDeg) { drawBezel(this, edgeRadius) }

        // 2) Draw Needle (Hide if cat level active)
        if (!isCatLevelModeActive) {
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
        }

        // 3) Draw Bearing Marker
        drawBearingMarker(canvas, edgeRadius)

        // 4) Draw Mode Symbol (handles cat level mode internally)
        drawModeSymbol(canvas, mainRadius) // Pass mainRadius for positioning

        // 5) Draw Bubble Level (Hide if cat level active)
        if (!isCatLevelModeActive) {
            drawBubbleLevel(canvas, mainRadius)
        }

        // 6) Draw Readouts (Hide if cat level active)
        if (!isCatLevelModeActive) {
            drawReadouts(canvas)
        }

        // 7) Draw Secret Text Overlay
        if (showSecretText) {
            drawSecretTextOverlay(canvas, mainRadius)
        }
    }

    // --- Private Drawing Helpers ---
    // ... (drawBezel, drawNeedle, drawReadouts, drawBearingMarker, drawBubbleLevel, drawSecretTextOverlay remain the same) ...
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
        var y = -totalHeight / 2f - readoutPaint.ascent()

        for (line in lines) {
            canvas.drawText(line, 0f, y, readoutPaint)
            y += lineSpacing
        }
    }
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
    private fun drawBubbleLevel(canvas: Canvas, radius: Float) { // Removed scale param
        val levelCenterX = radius * 0.60f
        val levelCenterY = 0f
        val levelRadius = radius * 0.15f // Use normal size

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


    // --- UPDATED: Handles drawing static symbol OR ENLARGED cat level mode ---
    private fun drawModeSymbol(canvas: Canvas, radius: Float) { // radius is mainRadius
        // Select correct SYMBOL and PAINT based on North mode
        val currentSymbol: String
        val currentPaint: Paint
        if (showTrueNorth) {
            currentSymbol = trueNorthSymbol
            currentPaint = catSymbolTrueNorthPaint
        } else {
            currentSymbol = magneticNorthSymbol
            currentPaint = catSymbolMagneticPaint
        }

        // Calculate text properties (needed for centering)
        val textBounds = Rect()
        currentPaint.getTextBounds(currentSymbol, 0, currentSymbol.length, textBounds)
        val textHeight = textBounds.height().toFloat()
        val textWidth = currentPaint.measureText(currentSymbol)
        val yOffset = textHeight / 2f - textBounds.bottom // Vertical centering offset

        if (isCatLevelModeActive) {
            // --- Draw Cat Level Mode (Enlarged) ---
            // --- SIZE ADJUSTMENT ---
            // Use the scale factor here for the container radius
            val catLevelContainerRadius = (radius * 0.35f) * CAT_LEVEL_SCALE
            val maxAngleMap = 45f // Keep sensitivity the same

            // Optional: Draw enlarged boundary circle
            canvas.drawCircle(0f, 0f, catLevelContainerRadius, catLevelBoundaryPaint)

            // Calculate offset based on pitch/roll relative to the ENLARGED radius
            val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
            val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
            var catOffsetX = -catLevelContainerRadius * sin(rollRad) * (90f / maxAngleMap)
            var catOffsetY = catLevelContainerRadius * sin(pitchRad) * (90f / maxAngleMap)

            // Clamp within the ENLARGED radius
            val totalOffset = sqrt(catOffsetX.pow(2) + catOffsetY.pow(2))
            if (totalOffset > catLevelContainerRadius) {
                val scale = catLevelContainerRadius / totalOffset
                catOffsetX *= scale
                catOffsetY *= scale
            }

            // Optional: Scale the symbol size slightly as well?
            // val currentTextSize = currentPaint.textSize
            // currentPaint.textSize = currentTextSize * sqrt(CAT_LEVEL_SCALE) // Example scaling

            // Draw the symbol at the calculated offset position
            canvas.drawText(currentSymbol, catOffsetX, catOffsetY + yOffset, currentPaint)

            // Restore text size if changed
            // currentPaint.textSize = currentTextSize

            // DO NOT update symbolBounds when in this mode

        } else {
            // --- Draw Static Symbol ---
            val symbolX = -radius * 0.60f // Original static X position
            val symbolY = 0f             // Original static Y position

            // Draw the symbol statically
            canvas.drawText(currentSymbol, symbolX, symbolY + yOffset, currentPaint)

            // Update clickable bounds ONLY when static
            val clickLeft = symbolX - textWidth / 2f - symbolClickPadding
            val clickTop = symbolY + yOffset - textHeight / 2f - symbolClickPadding
            val clickRight = symbolX + textWidth / 2f + symbolClickPadding
            val clickBottom = symbolY + yOffset + textHeight / 2f + symbolClickPadding
            symbolBounds.set(clickLeft, clickTop, clickRight, clickBottom)
        }
    }


    // --- Resource Cleanup ---
    // ... (onDetachedFromWindow remains the same) ...
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mediaPlayer?.release()
        mediaPlayer = null
        interactionHandler.removeCallbacksAndMessages(null)
        longPressRunnable = null
        singleTapRunnable = null
        catLevelTimeoutRunnable = null // Use correct name
    }
}