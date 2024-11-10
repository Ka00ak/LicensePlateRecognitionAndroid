package com.example.licenseplatedetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var results = mutableListOf<BoundingBox>()
    private var plateTexts = mutableMapOf<BoundingBox, String>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var bounds = Rect()

    init {
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textSize = 50f
        }

        textPaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 50f
        }

        boxPaint.apply {
            color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
            strokeWidth = 8F
            style = Paint.Style.STROKE
        }
    }

    fun clearResults() {
        if (results.isNotEmpty()) {
            results.clear()
            plateTexts.clear()
            invalidate()
        }
    }

    fun updateResults(newResults: List<BoundingBox>) {
        if (newResults != results) {
            results.clear()
            results.addAll(newResults)
            // Clear texts for boxes that no longer exist
            plateTexts.keys.removeAll { !newResults.contains(it) }
            invalidate()
        }
    }
    fun updatePlateText(boundingBox: BoundingBox, text: String) {
        plateTexts[boundingBox] = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (results.isEmpty()) return

        for (result in results) {
            val left = result.x1 * width
            val top = result.y1 * height
            val right = result.x2 * width
            val bottom = result.y2 * height

            // Draw bounding box
            canvas.drawRect(left, top, right, bottom, boxPaint)

            // Draw plate text if available
            plateTexts[result]?.let { plateText ->
                textBackgroundPaint.getTextBounds(plateText, 0, plateText.length, bounds)
                val plateTextWidth = bounds.width()
                val plateTextHeight = bounds.height()

                // Calculate background position for the plate text above the bounding box
                val plateTextBackgroundTop = top - (plateTextHeight + BOUNDING_RECT_TEXT_PADDING * 2)
                canvas.drawRect(
                    left,
                    plateTextBackgroundTop,
                    left + plateTextWidth + BOUNDING_RECT_TEXT_PADDING,
                    top - BOUNDING_RECT_TEXT_PADDING,
                    textBackgroundPaint
                )

                // Draw plate text
                canvas.drawText(
                    plateText,
                    left + BOUNDING_RECT_TEXT_PADDING / 2,
                    plateTextBackgroundTop + plateTextHeight + BOUNDING_RECT_TEXT_PADDING,
                    textPaint
                )
            }
        }
    }


    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 12
    }
}