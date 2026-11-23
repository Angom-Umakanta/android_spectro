package com.example.spectro

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Captures microphone audio and emits one spectrum frame (BINS values, in dBFS)
 * every HOP samples (~11.6 ms at 44.1 kHz).
 * Bin width = 44100 / 1024 ≈ 43 Hz, so BINS=256 covers 0 – 11 kHz.
 */
class AudioEngine(private val onFrame: (FloatArray) -> Unit) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val FFT_SIZE = 1024
        const val HOP = 512
        const val BINS = 256
    }

    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // permission is requested in the UI before start()
    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, HOP * 2 * 4)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release(); running = false; return@Thread
            }

            val fft = Fft(FFT_SIZE)
            val window = FloatArray(FFT_SIZE) { (0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1))).toFloat() }
            val samples = FloatArray(FFT_SIZE)       // sliding window
            val chunk = ShortArray(HOP)
            val re = FloatArray(FFT_SIZE)
            val im = FloatArray(FFT_SIZE)

            rec.startRecording()
            try {
                while (running) {
                    var read = 0
                    while (read < HOP && running) {
                        val r = rec.read(chunk, read, HOP - read)
                        if (r <= 0) break
                        read += r
                    }
                    if (read < HOP) continue

                    System.arraycopy(samples, HOP, samples, 0, FFT_SIZE - HOP)
                    for (i in 0 until HOP) samples[FFT_SIZE - HOP + i] = chunk[i] / 32768f

                    for (i in 0 until FFT_SIZE) { re[i] = samples[i] * window[i]; im[i] = 0f }
                    fft.transform(re, im)

                    val out = FloatArray(BINS)
                    for (b in 0 until BINS) {
                        val mag = sqrt(re[b] * re[b] + im[b] * im[b]) / (FFT_SIZE / 4f)
                        out[b] = 20f * log10(mag + 1e-9f)
                    }
                    onFrame(out)
                }
            } finally {
                rec.stop(); rec.release()
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}
