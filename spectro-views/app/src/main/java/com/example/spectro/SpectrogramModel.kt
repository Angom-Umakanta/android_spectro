package com.example.spectro

import android.graphics.Bitmap
import android.graphics.Color

class Detection(val name: String, val score: Float)

/** Holds the scrolling spectrogram bitmap, recent frames, and live sound matching. */
class SpectrogramModel {
    companion object {
        const val COLS = 400            // ~4.6 s of history
        const val BINS = AudioEngine.BINS
        const val RANGE_DB = 60f        // dynamic range shown
        const val RECENT = 90           // ~1 s of frames kept for labeling
        const val MATCH_FRAMES = 30     // ~0.35 s window used for matching
        const val MATCH_THRESHOLD = 0.80f

        /** Inferno-like color map, 256 entries. */
        private val stops = arrayOf(
            intArrayOf(0, 0, 4), intArrayOf(87, 16, 110), intArrayOf(188, 55, 84),
            intArrayOf(249, 142, 9), intArrayOf(252, 255, 164)
        )
        val LUT = IntArray(256) { i ->
            val t = i / 255f * (stops.size - 1)
            val idx = t.toInt().coerceAtMost(stops.size - 2)
            val a = stops[idx]
            val b = stops[idx + 1]
            val f = t - idx
            Color.rgb(
                (a[0] + (b[0] - a[0]) * f).toInt(),
                (a[1] + (b[1] - a[1]) * f).toInt(),
                (a[2] + (b[2] - a[2]) * f).toInt()
            )
        }
    }

    val bitmap: Bitmap = Bitmap.createBitmap(COLS, BINS, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(Color.BLACK) }

    @Volatile var writeX = 0
    @Volatile var floorDb = -90f
    @Volatile var paused = false
    @Volatile var library: List<LabeledSound> = emptyList()

    /** Called on the audio thread whenever a new column is drawn. */
    @Volatile var onUpdate: (() -> Unit)? = null
    /** Called on the audio thread when the detected sound changes (null = nothing). */
    @Volatile var onDetection: ((Detection?) -> Unit)? = null

    private val recent = ArrayDeque<FloatArray>()
    private val column = IntArray(BINS)
    private var frameCount = 0
    private var lastShown: String? = null

    /** Called from the audio thread. */
    fun onFrame(db: FloatArray) {
        synchronized(recent) {
            recent.addLast(db)
            if (recent.size > RECENT) recent.removeFirst()
        }
        if (!paused) {
            val floor = floorDb
            for (bin in 0 until BINS) {
                val t = ((db[bin] - floor) / RANGE_DB).coerceIn(0f, 1f)
                column[BINS - 1 - bin] = LUT[(t * 255).toInt()]   // low freq at bottom
            }
            val x = writeX
            bitmap.setPixels(column, 0, 1, x, 0, 1, BINS)
            writeX = (x + 1) % COLS
            onUpdate?.invoke()
        }
        if (++frameCount % 8 == 0) detect()
    }

    fun grabRecent(): List<FloatArray> = synchronized(recent) { recent.toList() }

    private fun publish(d: Detection?) {
        val key = d?.let { "${it.name}:${(it.score * 100).toInt()}" }
        if (key != lastShown) {
            lastShown = key
            onDetection?.invoke(d)
        }
    }

    private fun detect() {
        val lib = library
        if (lib.isEmpty()) { publish(null); return }
        val last = synchronized(recent) { recent.toList().takeLast(MATCH_FRAMES) }
        val sig = SoundLibrary.signature(last)
        if (sig == null) { publish(null); return }
        var best: LabeledSound? = null
        var bestScore = -1f
        for (s in lib) {
            var dot = 0f
            for (i in sig.indices) dot += sig[i] * s.signature[i]
            if (dot > bestScore) { bestScore = dot; best = s }
        }
        publish(if (best != null && bestScore >= MATCH_THRESHOLD) Detection(best.name, bestScore) else null)
    }
}
