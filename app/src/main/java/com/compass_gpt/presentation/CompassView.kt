package com.compass_gpt.presentation

import android.content.Context
import android.graphics.*
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import kotlin.math.*
import android.view.HapticFeedbackConstants

class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    // --- Constants ---
    private val longPressDurationMs = 3000L
    private val doubleTapTimeoutMs = 300L
    private val singleTapConfirmDelayMs = doubleTapTimeoutMs + 50L

    // --- Paints ---
    private val bezelPaint = Paint().apply { color = Color.WHITE; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val readoutPaint = Paint().apply { color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val magneticReadoutPaint = Paint().apply { color = Color.RED; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val trueNorthReadoutPaint = Paint().apply { color = Color.BLUE; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val magneticNeedlePaint = Paint().apply { color = Color.RED; strokeWidth = 8f; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
    private val trueNorthNeedlePaint = Paint().apply { color = Color.BLUE; strokeWidth = 8f; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
    private val bearingMarkerPaint = Paint().apply { color = "#ADD8E6".toColorInt(); style = Paint.Style.FILL; isAntiAlias = true }
    private val catSymbolMagneticPaint = Paint().apply { color = Color.RED; textSize = 45f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val catSymbolTrueNorthPaint = Paint().apply { color = Color.BLUE; textSize = 45f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val bubbleLevelOutlinePaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val bubbleLevelBubblePaint = Paint().apply { color = "#88FFFFFF".toColorInt(); style = Paint.Style.FILL; isAntiAlias = true }
    private val secretTextPaint = Paint().apply { color = Color.RED; textSize = 50f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val goToNeedlePaint = Paint().apply { color = Color.YELLOW; strokeWidth = 5f; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
    private val accuracyPaintHigh = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
    private val accuracyPaintMedium = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val accuracyPaintLow = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val accuracyPaintUnreliable = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL }

    // --- Path & Symbols ---
    private val bearingMarkerPath = Path()
    private val magneticNorthSymbol = "🐈"
    private val trueNorthSymbol = "🐾"
    private val secretText = "VA3FOD"

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
    private var showTrueNorth = false
    private var symbolBounds = RectF()
    private val symbolClickPadding = 20f
    private var isHoldingSymbol = false
    private var showSecretText = false
    private var lastTapTimeMs = 0L
    private var sensorAccuracyLevel: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    private val interactionHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var singleTapRunnable: Runnable? = null

    init {
        isHapticFeedbackEnabled = true
    }

    fun setSensorData(azimuth: Float, pitch: Float, roll: Float) {
        magneticNeedleDeg = ((azimuth % 360f) + 360f) % 360f
        this.pitchDeg = pitch
        this.rollDeg = roll
        invalidate()
    }

    fun setBezelRotation(angle: Float) {
        bezelRotationDeg = ((angle % 360f) + 360f) % 360f
        invalidate()
    }

    fun setGpsData(speed: Float, altitude: Float, decl: Float, local: String, utc: String) {
        speedKmh = speed; altitudeM = altitude; declDeg = decl; localTime = local; utcTime = utc
        invalidate()
    }

    fun setSensorAccuracy(accuracy: Int) {
        if (accuracy != sensorAccuracyLevel) {
            sensorAccuracyLevel = accuracy
            invalidate()
        }
    }

    // --- FORCE DRAW ON SIZING ---
    // If the OS auto-revokes permissions and pauses the app on startup,
    // the sensors won't trigger a draw. This guarantees the compass renders.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if ((w > 0) && (h > 0)) {
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        val touchX = event.x - (width / 2f)
        val touchY = event.y - (height / 2f)

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
                    interactionHandler.postDelayed(longPressRunnable!!, longPressDurationMs)
                    return true
                }
                lastTapTimeMs = 0L
            }

            MotionEvent.ACTION_MOVE -> {
                if (isHoldingSymbol && !symbolBounds.contains(touchX, touchY)) {
                    resetSymbolInteractionState()
                }
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoldingSymbol) {
                    val currentTime = System.currentTimeMillis()
                    val wasLongPress = showSecretText
                    val wasHolding = isHoldingSymbol
                    resetSymbolInteractionState()

                    if ((event.action == MotionEvent.ACTION_UP) && symbolBounds.contains(touchX, touchY)) {
                        if (wasLongPress) {
                            // Long press finished, do nothing
                        } else {
                            // Single tap
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
                    if (wasHolding) {
                        performClick()
                        return true
                    }
                }
                lastTapTimeMs = 0L
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

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

    override fun onDraw(canvas: Canvas) {
        if ((width == 0) || (height == 0)) {
            return
        }
        super.onDraw(canvas)

        canvas.drawColor(Color.BLACK)

        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy)

        val edgeRadius = min(cx, cy)
        val mainRadius = edgeRadius * 0.90f

        canvas.withRotation(bezelRotationDeg) { drawBezel(this, edgeRadius) }
        drawBearingMarker(canvas, edgeRadius)

        val headingToShow: Float
        val currentNeedlePaint: Paint
        if (showTrueNorth) {
            headingToShow = ((magneticNeedleDeg - declDeg) + 360f) % 360f
            currentNeedlePaint = trueNorthNeedlePaint
        } else {
            headingToShow = magneticNeedleDeg
            currentNeedlePaint = magneticNeedlePaint
        }
        canvas.withRotation(-headingToShow) {
            drawNeedle(this, mainRadius, currentNeedlePaint)
        }
        drawGoToNeedle(canvas, mainRadius, goToNeedlePaint)
        drawModeSymbol(canvas, mainRadius)
        drawBubbleLevel(canvas, mainRadius)
        drawAccuracyIndicator(canvas, mainRadius)
        drawReadouts(canvas)

        if (showSecretText) {
            drawSecretTextOverlay(canvas)
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

    private fun drawBezel(canvas: Canvas, radius: Float) {
        for (angle in 0 until 360 step 15) {
            val isMajor = ((angle % 90) == 0)
            val tickInner = if (isMajor) radius * 0.90f else radius * 0.95f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val sinA = sin(rad); val cosA = cos(rad)
            canvas.drawLine(radius * sinA, -radius * cosA, tickInner * sinA, -tickInner * cosA, bezelPaint)
        }
        val textRadius = radius * 0.80f
        for ((angle, label) in listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val sinA = sin(rad); val cosA = cos(rad)
            val x = textRadius * sinA; val y = -textRadius * cosA
            readoutPaint.getTextBounds(label, 0, label.length, tempTextBounds)
            val textHeightOffset = tempTextBounds.height() / 2f
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
        if (showTrueNorth) { currentSymbol = trueNorthSymbol; currentPaint = catSymbolTrueNorthPaint }
        else { currentSymbol = magneticNorthSymbol; currentPaint = catSymbolMagneticPaint }

        currentPaint.getTextBounds(currentSymbol, 0, currentSymbol.length, tempTextBounds)
        val textHeight = tempTextBounds.height().toFloat()
        val textWidth = currentPaint.measureText(currentSymbol)
        val yOffset = (textHeight / 2f) - tempTextBounds.bottom

        val symbolX = -radius * 0.60f
        val symbolY = 0f
        canvas.drawText(currentSymbol, symbolX, symbolY + yOffset, currentPaint)

        symbolBounds.apply {
            set(
                symbolX - (textWidth / 2f) - symbolClickPadding,
                (symbolY + yOffset) - (textHeight / 2f) - symbolClickPadding,
                symbolX + (textWidth / 2f) + symbolClickPadding,
                (symbolY + yOffset) + (textHeight / 2f) + symbolClickPadding,
            )
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
        if (totalOffset > (levelRadius - bubbleRadius)) {
            val scale = (levelRadius - bubbleRadius) / totalOffset
            bubbleOffsetX *= scale
            bubbleOffsetY *= scale
        }
        canvas.drawCircle(levelCenterX + bubbleOffsetX, levelCenterY + bubbleOffsetY, bubbleRadius, bubbleLevelBubblePaint)
    }

    private fun drawSecretTextOverlay(canvas: Canvas) {
        val linesCountForReadouts = 6
        val textHeightReadout = readoutPaint.descent() - readoutPaint.ascent()
        val lineSpacingReadout = textHeightReadout * 1.15f
        val totalHeightReadouts = (linesCountForReadouts - 1) * lineSpacingReadout
        val firstLineYReadout = (-totalHeightReadouts / 2f) - readoutPaint.ascent()
        secretTextPaint.getTextBounds(secretText, 0, secretText.length, tempTextBounds)
        val secretTextHeight = tempTextBounds.height()
        val secretTextY = firstLineYReadout - (secretTextHeight * 0.5f)
        canvas.drawText(secretText, 0f, secretTextY, secretTextPaint)
    }

    private fun drawReadouts(canvas: Canvas) {
        val bearing = ((360f - bezelRotationDeg) + 360f) % 360f
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
        var currentY = (-totalHeight / 2f) - readoutPaint.ascent()
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
        val indicatorY = (radius * 0.15f) + (indicatorRadius * 2.5f)
        val paintToUse = when (sensorAccuracyLevel) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> accuracyPaintHigh
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> accuracyPaintMedium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> accuracyPaintLow
            else -> accuracyPaintUnreliable
        }
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius, paintToUse)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        interactionHandler.removeCallbacksAndMessages(null)
        longPressRunnable = null
        singleTapRunnable = null
    }
}