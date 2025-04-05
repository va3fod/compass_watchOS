package com.compass_gpt.presentation

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import kotlin.math.*

class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- Constants ---
    private val LONG_PRESS_DURATION_MS = 3000L

    // --- Paints ---
    // ... (All paints remain the same) ...
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


    // --- Path & Symbols ---
    // ... (Path and Symbols remain the same) ...
    private val bearingMarkerPath = Path()
    private val magneticNorthSymbol = "🐈"
    private val trueNorthSymbol = "🐾"
    private val secretText = "VA3FOD"


    // --- State Variables ---
    // ... (State variables remain the same) ...
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
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null


    // --- Public Methods (setters) ---
    // ... (Setters remain the same) ...
    fun setSensorData(azimuth: Float, pitch: Float, roll: Float) {
        magneticNeedleDeg = (azimuth % 360f + 360f) % 360f
        pitchDeg = pitch
        rollDeg = roll
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
    // ... (onTouchEvent remains the same) ...
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        val touchX = event.x - width / 2f
        val touchY = event.y - height / 2f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
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
                    longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION_MS)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isHoldingSymbol && !symbolBounds.contains(touchX, touchY)) {
                    resetSymbolLongPressState()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoldingSymbol) {
                    if (!showSecretText && symbolBounds.contains(touchX, touchY)) {
                        showTrueNorth = !showTrueNorth
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    resetSymbolLongPressState()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    // --- Helper to reset long press ---
    // ... (resetSymbolLongPressState remains the same) ...
    private fun resetSymbolLongPressState() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        val needsRedraw = showSecretText || isHoldingSymbol
        isHoldingSymbol = false
        showSecretText = false
        if (needsRedraw) {
            invalidate()
        }
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

        // 2) Draw Needle
        val headingToShow: Float
        val currentNeedlePaint: Paint
        if (showTrueNorth) {
            headingToShow = (magneticNeedleDeg - declDeg + 360f) % 360f
            currentNeedlePaint = trueNorthNeedlePaint
        } else {
            headingToShow = magneticNeedleDeg
            currentNeedlePaint = magneticNeedlePaint
        }
        // Draw needle using the updated helper function
        canvas.withRotation(-headingToShow) {
            // Pass mainRadius which defines the outer extent
            drawNeedle(this, mainRadius, currentNeedlePaint)
        }

        // --- Draw Fixed Elements ---
        // 3) Draw Bearing Marker
        drawBearingMarker(canvas, edgeRadius)

        // 4) Draw Mode Symbol (Cat/Paws)
        drawModeSymbol(canvas, mainRadius)

        // 5) Draw Bubble Level
        drawBubbleLevel(canvas, mainRadius)

        // 6) Draw Readouts
        drawReadouts(canvas) // Readouts are centered, not directly affected by mainRadius here

        // 7) Draw Secret Text Overlay if needed
        if (showSecretText) {
            // Pass mainRadius to position text correctly
            drawSecretTextOverlay(canvas, mainRadius)
        }
    }

    // --- Private Drawing Helpers ---
    // ... (drawBezel, drawReadouts, drawBearingMarker, drawModeSymbol, drawBubbleLevel remain the same) ...
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
    private fun drawModeSymbol(canvas: Canvas, radius: Float) {
        val symbolX = -radius * 0.60f
        val symbolY = 0f

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
        val textWidth = currentPaint.measureText(currentSymbol)
        val textHeight = textBounds.height().toFloat()
        val yOffset = textHeight / 2f - textBounds.bottom

        canvas.drawText(currentSymbol, symbolX, symbolY + yOffset, currentPaint)

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


    // --- UPDATED: drawNeedle draws only outer part ---
    // The 'radius' parameter here is the maximum extent (mainRadius)
    private fun drawNeedle(canvas: Canvas, radius: Float, paint: Paint) {
        // Define the inner starting point and outer end point
        val innerNeedleRadius = radius * 0.3f // Start drawing from 30% of the main radius
        val outerNeedleRadius = radius * 0.9f // End drawing at 90% of the main radius

        // Draw the line segment from inner radius to outer radius along the negative Y axis
        canvas.drawLine(0f, -innerNeedleRadius, 0f, -outerNeedleRadius, paint)
    }

    // --- UPDATED: drawSecretTextOverlay positions text higher ---
    // Accepts mainRadius for positioning calculation
    private fun drawSecretTextOverlay(canvas: Canvas, mainRadius: Float) {
        // Calculate the desired Y coordinate for the center of the text
        // Move it up significantly from the center (0,0)
        val targetCenterY = -mainRadius * 0.5f // Position center at 50% of mainRadius upwards

        // Calculate vertical centering offset based on text bounds
        val textBounds = Rect()
        secretTextPaint.getTextBounds(secretText, 0, secretText.length, textBounds)
        val yOffset = textBounds.height() / 2f - textBounds.bottom

        // Calculate final draw Y coordinate
        val drawY = targetCenterY + yOffset

        // Draw the text centered horizontally (X=0) at the calculated Y
        canvas.drawText(secretText, 0f, drawY, secretTextPaint)
    }
}