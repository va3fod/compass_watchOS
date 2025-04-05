package com.compass_gpt.presentation

import android.content.Context
import android.graphics.*
import android.os.Handler // Import Handler
import android.os.Looper  // Import Looper
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
    private val LONG_PRESS_DURATION_MS = 3000L // 3 seconds

    // --- Paints ---
    // ... (bezelPaint, readoutPaint, needle paints, marker paint, symbol paints, level paints unchanged) ...
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
    // --- NEW: Secret Text Paint ---
    private val secretTextPaint = Paint().apply {
        color = Color.RED
        textSize = 60f // Make it reasonably large
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) // Monospaced bold font
        // Optional: Add shadow for visibility
        // setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }


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
    // --- NEW: Long Press State ---
    private var isHoldingSymbol = false
    private var showSecretText = false
    private val longPressHandler = Handler(Looper.getMainLooper()) // Handler for delayed task
    private var longPressRunnable: Runnable? = null // Runnable to show secret text


    // --- Public Methods (setters) ---
    // ... (setSensorData, setBezelRotation, setGpsData remain unchanged) ...
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
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        val touchX = event.x - width / 2f // Adjust coords relative to center
        val touchY = event.y - height / 2f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (symbolBounds.contains(touchX, touchY)) {
                    isHoldingSymbol = true
                    showSecretText = false // Reset secret text flag on new press

                    // Define and post the long press runnable
                    longPressRunnable = Runnable {
                        // This runs after LONG_PRESS_DURATION_MS if still holding
                        if (isHoldingSymbol) { // Double check if still holding
                            showSecretText = true
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) // Feedback
                            invalidate() // Redraw to show text
                        }
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION_MS)

                    return true // Consume the DOWN event
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Optional: If finger moves OFF the symbol while holding, cancel the long press
                if (isHoldingSymbol && !symbolBounds.contains(touchX, touchY)) {
                    resetLongPressState()
                    // Don't consume the event here, let parent handle scrolling etc. if needed
                    // return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoldingSymbol) {
                    // Check if it was a short tap (UP occurred before long press triggered secret text)
                    // and also ensure the finger is still on the symbol on ACTION_UP
                    if (!showSecretText && symbolBounds.contains(touchX, touchY)) {
                        // It was a short tap on the symbol
                        showTrueNorth = !showTrueNorth
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) // Tap feedback
                    }
                    // Always cancel long press logic on UP or CANCEL
                    resetLongPressState()
                    return true // Consume the UP/CANCEL event if we started holding
                }
            }
        }
        // If event wasn't consumed above, pass it on
        return super.onTouchEvent(event)
    }

    // --- NEW: Helper to cancel long press timer and reset flags ---
    private fun resetLongPressState() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) } // Cancel pending runnable
        longPressRunnable = null
        if (showSecretText || isHoldingSymbol) { // Only redraw if state actually changed
            invalidate() // Redraw to hide secret text if it was visible
        }
        isHoldingSymbol = false
        showSecretText = false // Ensure secret text is hidden
    }

    // --- Drawing Logic ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy) // Origin is now center screen

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
        canvas.withRotation(-headingToShow) { drawNeedle(this, mainRadius, currentNeedlePaint) }

        // --- Draw Fixed Elements ---
        // 3) Draw Bearing Marker
        drawBearingMarker(canvas, edgeRadius)

        // 4) Draw Mode Symbol (Cat/Paws)
        drawModeSymbol(canvas, mainRadius)

        // 5) Draw Bubble Level
        drawBubbleLevel(canvas, mainRadius)

        // 6) Draw Readouts
        drawReadouts(canvas)

        // --- NEW: Draw Secret Text Overlay ---
        if (showSecretText) {
            drawSecretTextOverlay(canvas)
        }
    }

    // --- Private Drawing Helpers ---
    // ... (drawBezel, drawNeedle, drawReadouts, drawBearingMarker, drawModeSymbol, drawBubbleLevel unchanged) ...
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
        val needleLen = radius * 0.9f
        canvas.drawLine(0f, 0f, 0f, -needleLen, paint)
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

    // --- NEW: Helper to draw the secret text overlay ---
    private fun drawSecretTextOverlay(canvas: Canvas) {
        // Draw centered at (0,0) because canvas is already translated
        val textBounds = Rect()
        secretTextPaint.getTextBounds(secretText, 0, secretText.length, textBounds)
        val yOffset = textBounds.height() / 2f - textBounds.bottom // Center vertically

        canvas.drawText(secretText, 0f, yOffset, secretTextPaint)
    }
}