package com.compass_gpt.presentation

import android.content.Context
import android.graphics.*
// No longer need Drawable or ContextCompat for the cat
import android.util.AttributeSet
import android.view.View
// No longer need R for the cat drawable
import kotlin.math.*

class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- Existing Paints ---
    private val bezelPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
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
    private val redNeedlePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val bearingMarkerPaint = Paint().apply {
        color = Color.parseColor("#ADD8E6") // Light Blue color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // --- NEW: Paint for the Cat Symbol ---
    private val catSymbolPaint = Paint().apply {
        color = Color.WHITE // Color of the symbol
        textSize = 45f // Adjust size as needed
        textAlign = Paint.Align.CENTER // Align horizontally to the calculated X
        isAntiAlias = true
        // Consider Typeface if you want a specific font style for the emoji/symbol
        // typeface = Typeface.DEFAULT
    }
    // The actual symbol string
    private val catSymbol = "🐈" // Cat Emoji (U+1F408)
    // Alternatives: "🐾" (paw print), "=^.^=" (ASCII - might render oddly depending on font)


    // --- Path for Bearing Marker ---
    private val bearingMarkerPath = Path()

    // --- State Variables ---
    private var magneticNeedleDeg = 0f
    private var bezelRotationDeg = 0f
    private var speedKmh = 0f
    private var altitudeM = 0f
    private var declDeg = 0f
    private var localTime = ""
    private var utcTime = ""

    // --- Initialization ---
    // No longer need the init block for the cat drawable

    // --- Public Methods (setters) ---
    fun setAzimuth(angle: Float) {
        magneticNeedleDeg = (angle % 360f + 360f) % 360f
        invalidate()
    }

    fun setBezelRotation(angle: Float) {
        bezelRotationDeg = (angle % 360f + 360f) % 360f
        invalidate()
    }

    fun setData(speed: Float, altitude: Float, decl: Float, local: String, utc: String) {
        speedKmh = speed
        altitudeM = altitude
        declDeg = decl
        localTime = local
        utcTime = utc
        invalidate()
    }

    // --- Drawing Logic ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy) // Move origin to center
        val radius = min(cx, cy) * 0.85f // Main compass radius

        // 1) Draw the bezel (rotates)
        canvas.save()
        canvas.rotate(bezelRotationDeg)
        drawBezel(canvas, radius)
        canvas.restore()

        // 2) Draw the needle (rotates)
        canvas.save()
        canvas.rotate(-magneticNeedleDeg)
        drawNeedle(canvas, radius, redNeedlePaint)
        canvas.restore()

        // --- Draw Fixed Elements (Relative to Screen Center) ---

        // 3) Draw the fixed Bearing Marker Triangle at the top
        drawBearingMarker(canvas, radius)

        // 4) Draw the fixed Cat Symbol Text on the left
        drawCatSymbolText(canvas, radius) // Changed function call

        // 5) Draw the centered text readouts
        drawReadouts(canvas)
    }

    // --- Private Drawing Helpers ---

    private fun drawBezel(canvas: Canvas, radius: Float) {
        // (Keep this function exactly as it was)
        for (angle in 0 until 360 step 15) {
            val isMajor = (angle % 90 == 0)
            val tickOuter = radius
            val tickInner = if (isMajor) radius * 0.85f else radius * 0.9f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val sinA = sin(rad)
            val cosA = cos(rad)
            canvas.drawLine(tickOuter * sinA, -tickOuter * cosA, tickInner * sinA, -tickInner * cosA, bezelPaint)
        }
        val directions = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        val textRadius = radius * 0.75f
        for ((angle, label) in directions) {
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
        // (Keep this function exactly as it was)
        val needleLen = radius * 0.8f
        canvas.drawLine(0f, 0f, 0f, -needleLen, paint)
    }

    private fun drawReadouts(canvas: Canvas) {
        // (Keep this function exactly as it was)
        val bearing = ((360f - bezelRotationDeg) % 360f + 360f) % 360f
        val lineBRG = "BRG ${bearing.toInt()}°"
        val lineSpd = "Spd: %.1f km/h".format(speedKmh)
        val lineAlt = "Alt: %.0f m".format(altitudeM)
        val lineDecl = "Decl: %.1f°".format(declDeg)
        val lineLocal = "Loc: $localTime"
        val lineUTC = "UTC: $utcTime"
        val lines = listOf(lineBRG, lineSpd, lineAlt, lineDecl, lineLocal, lineUTC)

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
        // (Keep this function exactly as it was)
        val markerOuterY = -radius * 1.02f
        val markerInnerY = -radius * 0.90f
        val markerWidth = radius * 0.15f

        bearingMarkerPath.reset()
        bearingMarkerPath.moveTo(0f, markerOuterY)
        bearingMarkerPath.lineTo(-markerWidth / 2f, markerInnerY)
        bearingMarkerPath.lineTo(markerWidth / 2f, markerInnerY)
        bearingMarkerPath.close()

        canvas.drawPath(bearingMarkerPath, bearingMarkerPaint)
    }

    // --- NEW: Helper to draw the fixed cat symbol TEXT ---
    private fun drawCatSymbolText(canvas: Canvas, radius: Float) {
        // Calculate position for the cat symbol (left side, vertically centered)
        val catSymbolX = -radius * 0.7f // Horizontal position (adjust 0.7f as needed)
        val catSymbolY = 0f             // Vertical position (0f for center line)

        // Adjust Y position slightly to center the text vertically
        // (ascent/descent gives distance from baseline, we want center)
        val textBounds = Rect()
        catSymbolPaint.getTextBounds(catSymbol, 0, catSymbol.length, textBounds)
        // Offset by half the text height to center properly
        val yOffset = textBounds.height() / 2f - textBounds.bottom // Adjust based on bounds bottom relative to baseline

        canvas.drawText(catSymbol, catSymbolX, catSymbolY + yOffset, catSymbolPaint)
    }

}