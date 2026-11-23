package com.example.spectro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

/** Draws the scrolling spectrogram bitmap (oldest column on the left, newest on the right). */
class SpectrogramView(context: Context, private val model: SpectrogramModel) : View(context) {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }
    private val src = Rect()
    private val dst = Rect()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val w = width
        val h = height
        val cols = SpectrogramModel.COLS
        val bins = SpectrogramModel.BINS
        val wx = model.writeX
        val firstW = cols - wx
        val split = (firstW.toFloat() / cols * w).toInt()

        if (firstW > 0) {
            src.set(wx, 0, cols, bins); dst.set(0, 0, split, h)
            canvas.drawBitmap(model.bitmap, src, dst, bitmapPaint)
        }
        if (wx > 0) {
            src.set(0, 0, wx, bins); dst.set(split, 0, w, h)
            canvas.drawBitmap(model.bitmap, src, dst, bitmapPaint)
        }

        val pad = 4f * density
        canvas.drawText("11 kHz", pad, textPaint.textSize + pad, textPaint)
        canvas.drawText("5.5 kHz", pad, h / 2f, textPaint)
        canvas.drawText("0", pad, h - pad, textPaint)
    }
}
