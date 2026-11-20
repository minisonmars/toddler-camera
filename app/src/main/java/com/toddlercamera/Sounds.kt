package com.toddlercamera

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaActionSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Shutter and recording sounds. "click" is the phone's own camera sound; the others are
 * short tones made by the app itself, played at the media volume.
 */
class Sounds {

    private val system = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
        load(MediaActionSound.START_VIDEO_RECORDING)
        load(MediaActionSound.STOP_VIDEO_RECORDING)
    }
    private var track: AudioTrack? = null
    private val cache = HashMap<String, ShortArray>()

    fun playShutter(name: String) {
        when (name) {
            "off" -> {}
            "click" -> system.play(MediaActionSound.SHUTTER_CLICK)
            else -> playPcm(cache.getOrPut(name) { render(segmentsFor(name)) })
        }
    }

    fun playRecordStart() = system.play(MediaActionSound.START_VIDEO_RECORDING)

    fun playRecordStop() = system.play(MediaActionSound.STOP_VIDEO_RECORDING)

    fun release() {
        system.release()
        track?.release()
        track = null
    }

    // ---------- Making the tones ----------

    private class Seg(
        val f0: Double,        // start pitch in Hz
        val f1: Double,        // end pitch in Hz (differs from f0 for a slide)
        val ms: Int,
        val decay: Double,     // higher fades out faster
        val amp: Double = 0.7
    )

    private fun segmentsFor(name: String): List<Seg> = when (name) {
        "pop" -> listOf(Seg(500.0, 1400.0, 80, 4.0))
        "bell" -> listOf(Seg(1047.0, 1047.0, 600, 6.0))
        "boing" -> listOf(Seg(200.0, 600.0, 120, 2.5), Seg(600.0, 180.0, 240, 4.0))
        "chime" -> listOf(Seg(784.0, 784.0, 130, 4.0), Seg(988.0, 988.0, 130, 4.0), Seg(1319.0, 1319.0, 320, 6.0))
        else -> listOf(Seg(1000.0, 1000.0, 130, 1.0, 0.5)) // beep
    }

    private fun render(segs: List<Seg>): ShortArray {
        val total = segs.sumOf { RATE * it.ms / 1000 }
        val out = ShortArray(total)
        var idx = 0
        for (s in segs) {
            val n = RATE * s.ms / 1000
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / n
                val f = s.f0 + (s.f1 - s.f0) * t
                phase += 2 * PI * f / RATE
                val attack = if (i < 150) i / 150.0 else 1.0 // avoids a click at the start
                val env = exp(-s.decay * t) * attack
                out[idx++] = (sin(phase) * env * s.amp * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    private fun playPcm(data: ShortArray) {
        try {
            track?.release()
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(data, 0, data.size)
            t.play()
            track = t
        } catch (e: Exception) {
            // No sound is better than a crash
        }
    }

    companion object {
        private const val RATE = 22050
    }
}
