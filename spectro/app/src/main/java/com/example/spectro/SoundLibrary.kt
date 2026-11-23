package com.example.spectro

import android.content.Context
import kotlin.math.sqrt

class LabeledSound(val name: String, val signature: FloatArray)

/** Stores labeled sounds (as normalized spectral fingerprints) in SharedPreferences. */
class SoundLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("sounds", Context.MODE_PRIVATE)

    fun loadAll(): List<LabeledSound> = prefs.all.mapNotNull { (name, v) ->
        (v as? String)?.let { s ->
            LabeledSound(name, s.split(",").map { it.toFloat() }.toFloatArray())
        }
    }.sortedBy { it.name.lowercase() }

    fun save(sound: LabeledSound) {
        prefs.edit().putString(sound.name, sound.signature.joinToString(",")).apply()
    }

    fun delete(name: String) {
        prefs.edit().remove(name).apply()
    }

    companion object {
        /** Frames quieter than this (peak bin, dBFS) are treated as silence. */
        const val GATE_DB = -55f

        /**
         * Fingerprint = mean dB spectrum of the loud frames, with the mean removed
         * and scaled to unit length. That makes it independent of volume, so
         * dot(a, b) is the cosine similarity of the spectral *shapes*.
         */
        fun signature(frames: List<FloatArray>): FloatArray? {
            val loud = frames.filter { f -> f.max() > GATE_DB }
            if (loud.size < 3) return null
            val n = loud[0].size
            val mean = FloatArray(n)
            for (f in loud) for (i in 0 until n) mean[i] += f[i] / loud.size
            val avg = mean.average().toFloat()
            var norm = 0f
            for (i in 0 until n) { mean[i] -= avg; norm += mean[i] * mean[i] }
            norm = sqrt(norm)
            if (norm < 1e-3f) return null
            for (i in 0 until n) mean[i] /= norm
            return mean
        }
    }
}
