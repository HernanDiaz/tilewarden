package com.tilewarden.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Sample rate of every synthesised buffer. 22050 Hz keeps PCM small without
 *  sacrificing perceptible quality for short, retro-feel SFX. */
private const val SAMPLE_RATE = 22050

/** All the discrete sound effects the game can request. */
enum class SoundId {
    ATTACK_SWING,
    HIT,
    BLOCK,
    DEATH,
    VICTORY,
    DEFEAT,
}

/**
 * Plays short 8-bit-feel sound effects synthesised in code at construction
 * time. No external audio assets, no resource files — every sample is
 * rendered into a 16-bit PCM buffer (mono, 22050 Hz) by the `synthXxx`
 * helpers and cached in [pcm].
 *
 * [play] spins up a fresh [AudioTrack] in `MODE_STATIC`, writes the whole
 * buffer in one shot, fires `play()`, and self-releases on the playback
 * marker. Cheap enough for the cadence of a turn-based replay (a handful
 * of SFX per second peak) without juggling a pool.
 *
 * [muted] is a Compose-observable property: flip it from the UI and every
 * subsequent [play] turns into a no-op until you flip it back.
 */
class AudioEngine {

    var muted: Boolean by mutableStateOf(false)

    private val pcm: Map<SoundId, ShortArray> = mapOf(
        SoundId.ATTACK_SWING to synthAttackSwing(),
        SoundId.HIT          to synthHit(),
        SoundId.BLOCK        to synthBlock(),
        SoundId.DEATH        to synthDeath(),
        SoundId.VICTORY      to synthVictory(),
        SoundId.DEFEAT       to synthDefeat(),
    )

    fun play(id: SoundId) {
        if (muted) return
        val samples = pcm[id] ?: return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            }
        )
        track.play()
    }

    // ---- Render helpers ----

    /** Quantise a -1..1 sample into a 16-bit signed value with 6 dB headroom
     *  so summed components don't clip. */
    private fun toSample(v: Double): Short =
        (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.5).toInt().toShort()

    /** Render `durMs` of audio by sampling `gen(t)` at the sample rate. */
    private inline fun render(durMs: Int, gen: (Double) -> Double): ShortArray {
        val n = SAMPLE_RATE * durMs / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            out[i] = toSample(gen(t))
        }
        return out
    }

    /** Square wave at `freq` Hz, range ±1. */
    private fun square(t: Double, freq: Double): Double =
        if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0

    /** Sine wave at `freq` Hz, range ±1. */
    private fun sineW(t: Double, freq: Double): Double = sin(2 * PI * freq * t)

    /** White noise, range ±1. */
    private fun noiseW(): Double = Random.nextDouble() * 2 - 1

    /** Exponential decay from 1 at t=0 toward 0 at t=dur. `k` controls
     *  steepness — higher k means a snappier tail. */
    private fun decay(t: Double, dur: Double, k: Double = 4.0): Double =
        exp(-t * k / dur)

    // ---- Concrete effects ----

    /** Whoosh: noise burst + descending sine sweep, ~120 ms. */
    private fun synthAttackSwing(): ShortArray = render(120) { t ->
        val env = decay(t, 0.12, 5.0)
        noiseW() * env * 0.6 + sineW(t, 600.0 - 4500.0 * t) * env * 0.25
    }

    /** Thud: low square + noise impact, ~150 ms. */
    private fun synthHit(): ShortArray = render(150) { t ->
        val env = decay(t, 0.15, 6.0)
        square(t, 90.0) * env * 0.55 + noiseW() * env * 0.35
    }

    /** Clink: two short metallic pings spaced 60 ms apart, ~180 ms total. */
    private fun synthBlock(): ShortArray = render(180) { t ->
        val a = if (t < 0.05) sineW(t, 1900.0) * decay(t, 0.05, 8.0) * 0.5 else 0.0
        val b = if (t in 0.06..0.18) sineW(t, 1300.0) * decay(t - 0.06, 0.12, 6.0) * 0.4
                else 0.0
        a + b
    }

    /** Sinking square-wave tone with a noise tail — character down, ~400 ms. */
    private fun synthDeath(): ShortArray = render(400) { t ->
        val env = decay(t, 0.4, 4.0)
        val pitch = 220.0 * exp(-t * 3.5)  // 220 Hz down to a sub-audible rumble
        square(t, pitch) * env * 0.55 + noiseW() * env * 0.15
    }

    /** Major ascending arpeggio: C5 E5 G5 C6. */
    private fun synthVictory(): ShortArray =
        concatNotes(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), noteMs = 110)

    /** Minor descending arpeggio: C5 A4 F4 C4. */
    private fun synthDefeat(): ShortArray =
        concatNotes(doubleArrayOf(523.25, 440.0, 349.23, 261.63), noteMs = 140)

    /** Render a sequence of square-wave notes back to back, each with its own
     *  decay envelope. Used for VICTORY / DEFEAT jingles. */
    private fun concatNotes(notes: DoubleArray, noteMs: Int): ShortArray {
        val noteSamples = SAMPLE_RATE * noteMs / 1000
        val out = ShortArray(notes.size * noteSamples)
        var off = 0
        for (f in notes) {
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = decay(t, noteMs / 1000.0, 3.5)
                out[off + i] = toSample(square(t, f) * env * 0.4)
            }
            off += noteSamples
        }
        return out
    }
}
