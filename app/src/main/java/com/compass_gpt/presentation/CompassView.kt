package com.compass_gpt.presentation

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // Paint for bezel tick marks (we do not draw the outer circle)
    private val bezelPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Paint for cardinal directions and readouts (centered)
    private val readoutPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f // Adjust size as needed for watch screen
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD // Make text bolder
    }

    // Paint for the red needle (magnetic north)
    private val redNeedlePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    // Magnetic needle heading (in degrees) and bezel rotation from crown
    private var magneticNeedleDeg = 0f
    private var bezelRotationDeg = 0f

    // Data readouts (from GPS)
    private var speedKmh = 0f
    private var altitudeM = 0f
    private var declDeg = 0f
    private var localTime = ""
    private var utcTime = ""

    // The early algorithm sets the magnetic needle via setAzimuth()
    fun setAzimuth(angle: Float) {
        // Ensure angle is within [0, 360) before storing
        magneticNeedleDeg = (angle % 360f + 360f) % 360f
        invalidate() // Request redraw
    }

    fun setBezelRotation(angle: Float) {
        // Ensure angle is within [0, 360) before storing
        bezelRotationDeg = (angle % 360f + 360f) % 360f
        invalidate() // Request redraw
    }

    fun setData(speed: Float, altitude: Float, decl: Float, local: String, utc: String) {
        speedKmh = speed
        altitudeM = altitude
        declDeg = decl
        localTime = local
        utcTime = utc
        invalidate() // Request redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Translate canvas so that (0,0) is the center.
        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy)
        val radius = min(cx, cy) * 0.85f // Use slightly more screen space

        // 1) Draw the bezel tick marks and cardinal directions (rotated by bezelRotationDeg)
        canvas.save()
        canvas.rotate(bezelRotationDeg)
        drawBezel(canvas, radius)
        canvas.restore()

        // 2) Draw the red magnetic needle (rotate by negative magnetic heading)
        // The needle itself points "up" (negative Y), so rotating the canvas
        // by the negative heading makes the needle point correctly.
        canvas.save()
        canvas.rotate(-magneticNeedleDeg)
        drawNeedle(canvas, radius, redNeedlePaint)
        canvas.restore()

        // 3) Draw the centered block of readouts over the compass
        drawReadouts(canvas)
    }

    private fun drawBezel(canvas: Canvas, radius: Float) {
        // Draw tick marks every 15° (outer circle is not drawn)
        for (angle in 0 until 360 step 15) {
            val isMajor = (angle % 90 == 0)
            val tickOuter = radius
            val tickInner = if (isMajor) radius * 0.85f else radius * 0.9f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val sinA = sin(rad)
            val cosA = cos(rad)
            canvas.drawLine(tickOuter * sinA, -tickOuter * cosA, tickInner * sinA, -tickInner * cosA, bezelPaint)
        }
        // Draw cardinal directions: N, E, S, W.
        val directions = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        val textRadius = radius * 0.75f
        for ((angle, label) in directions) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val sinA = sin(rad)
            val cosA = cos(rad)
            val x = textRadius * sinA
            val y = -textRadius * cosA
            // Adjust Y position to truly center the text vertically
            val textBounds = Rect()
            readoutPaint.getTextBounds(label, 0, label.length, textBounds)
            val textHeightOffset = textBounds.height() / 2f
            canvas.drawText(label, x, y + textHeightOffset, readoutPaint) // Use '+' for vertical centering
        }
    }

    private fun drawNeedle(canvas: Canvas, radius: Float, paint: Paint) {
        // Draw a simple line needle pointing up (towards negative Y)
        val needleLen = radius * 0.8f
        canvas.drawLine(0f, 0f, 0f, -needleLen, paint) // Draw from center upwards
        // Optional: Add a small circle at the base
        // canvas.drawCircle(0f, 0f, paint.strokeWidth / 2f, paint)
    }

    /**
     * Draw the centered block of readouts over the compass.
     * The block includes "BRG", "Spd", "Alt", "Decl", "Loc", and "UTC".
     */
    private fun drawReadouts(canvas: Canvas) {
        // Calculate bearing based on bezel rotation relative to magnetic north (which is at the top of the rotated bezel)
        val bearing = ((360f - bezelRotationDeg) % 360f + 360f) % 360f
        val lineBRG = "BRG ${bearing.toInt()}°"
        val lineSpd = "Spd: %.1f km/h".format(speedKmh)
        val lineAlt = "Alt: %.0f m".format(altitudeM)
        val lineDecl = "Decl: %.1f°".format(declDeg) // Display the declination
        val lineLocal = "Loc: $localTime"
        val lineUTC = "UTC: $utcTime"
        val lines = listOf(lineBRG, lineSpd, lineAlt, lineDecl, lineLocal, lineUTC)

        val textHeight = readoutPaint.descent() - readoutPaint.ascent()
        val lineSpacing = textHeight * 1.15f // Slightly more spacing
        val totalHeight = (lines.size -1) * lineSpacing // Total height based on spacing

        // Start drawing from top, centered vertically
        var y = -totalHeight / 2f - readoutPaint.ascent() // Adjust start based on ascent

        // Optional: Shift the block down slightly if needed
        // y += textHeight * 0.2f

        for (line in lines) {
            canvas.drawText(line, 0f, y, readoutPaint)
            y += lineSpacing
        }
    }
}