package com.compass_gpt.presentation
// ... (Imports remain the same) ...
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import kotlin.math.*


class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

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

    // --- Path & Symbols ---
    // ... (Path and Symbols remain the same) ...
    private val bearingMarkerPath = Path()
    private val magneticNorthSymbol = "🐈"
    private val trueNorthSymbol = "🐾"

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
                if (symbolBounds.contains(touchX, touchY)) return true
            }
            MotionEvent.ACTION_UP -> {
                if (symbolBounds.contains(touchX, touchY)) {
                    showTrueNorth = !showTrueNorth
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    // --- Drawing Logic ---
    // ... (onDraw remains the same, calling the updated helpers) ...
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy)

        val edgeRadius = min(cx, cy)
        val mainRadius = edgeRadius * 0.90f // Inner radius stays the same

        // 1) Draw bezel at edge
        canvas.withRotation(bezelRotationDeg) {
            drawBezel(this, edgeRadius)
        }

        // 2) Draw needle within mainRadius
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

        // 3) Draw Bearing Marker at edge
        drawBearingMarker(canvas, edgeRadius)

        // 4) Draw Mode Symbol (Cat/Paws) - uses updated helper
        drawModeSymbol(canvas, mainRadius)

        // 5) Draw Bubble Level - uses updated helper
        drawBubbleLevel(canvas, mainRadius)

        // 6) Draw Readouts
        drawReadouts(canvas)
    }

    // --- Private Drawing Helpers ---
    // ... (drawBezel, drawNeedle, drawReadouts, drawBearingMarker remain the same) ...
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


    // --- UPDATED: drawModeSymbol uses smaller multiplier for X position ---
    private fun drawModeSymbol(canvas: Canvas, radius: Float) {
        // --- POSITION ADJUSTMENT ---
        // Reduced multiplier from 0.75f to move symbol inwards
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

    // --- UPDATED: drawBubbleLevel uses smaller multiplier for X position ---
    private fun drawBubbleLevel(canvas: Canvas, radius: Float) {
        // --- POSITION ADJUSTMENT ---
        // Reduced multiplier from 0.75f to move level inwards
        val levelCenterX = radius * 0.60f
        val levelCenterY = 0f
        val levelRadius = radius * 0.15f // Size of the level circle remains relative to mainRadius

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
}