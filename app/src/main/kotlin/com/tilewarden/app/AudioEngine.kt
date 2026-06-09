package com.tilewarden.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
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
    STEP,
}

/**
 * Plays short 8-bit-feel sound effects synthesised in code at construction
 * time, plus a low dungeon-drone ambient loop. No external audio assets, no
 * resource files — every sample is rendered into 16-bit PCM (mono, 22050
 * Hz) by the `synthXxx` helpers and cached.
 *
 * SFX: [play] spins up a fresh [AudioTrack] in `MODE_STATIC`, writes the
 * whole buffer in one shot, fires `play()`, and self-releases on the
 * playback marker. Cheap enough for the cadence of a turn-based replay
 * without juggling a pool.
 *
 * Ambient: [setAmbientActive] (typically called from the game screen's
 * `DisposableEffect`) owns a single persistent looping `AudioTrack`. The
 * loop buffer is 8 seconds long; every tonal component is a multiple of
 * 0.125 Hz so cycles close exactly at the wrap-around, hiding any click.
 *
 * [muted] is Compose-observable. Flipping it to true silences SFX AND
 * suspends the ambient; flipping it back resumes ambient if a previous
 * [setAmbientActive] call asked for it.
 */
class AudioEngine {

    private val _muted = mutableStateOf(false)
    var muted: Boolean
        get() = _muted.value
        set(value) {
            if (_muted.value == value) return
            _muted.value = value
            if (value) stopAmbient()
            else if (ambientWanted) startAmbient()
        }

    /** True iff the caller has asked for ambient to be on; honoured as long
     *  as we're not muted. Survives mute/unmute toggles. */
    private var ambientWanted: Boolean = false
    private var ambientTrack: AudioTrack? = null

    private val pcm: Map<SoundId, ShortArray> = mapOf(
        SoundId.ATTACK_SWING to synthAttackSwing(),
        SoundId.HIT          to synthHit(),
        SoundId.BLOCK        to synthBlock(),
        SoundId.DEATH        to synthDeath(),
        SoundId.VICTORY      to synthVictory(),
        SoundId.DEFEAT       to synthDefeat(),
        SoundId.STEP         to synthStep(),
    )

    private val ambientPcm: ShortArray = synthAmbientLoop()

    /** Main-looper handler used to schedule deterministic release() calls
     *  after each SFX is done playing. We deliberately do NOT rely on
     *  AudioTrack's setNotificationMarkerPosition callback — in practice
     *  it's unreliable when the track is built from a coroutine
     *  dispatcher, which leaks slots and eventually silences everything. */
    private val callbackHandler = Handler(Looper.getMainLooper())

    fun play(id: SoundId) {
        if (muted) return
        val samples = pcm[id] ?: return
        // Audio failures must NEVER interrupt the game-state mutations
        // around the call site. Swallow any exception (rare: out-of-memory
        // on the audio HAL, resource exhaustion, etc.) and move on.
        try {
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
            track.play()
            // Deterministic release: schedule stop+release for slightly after
            // the sample ends. Doesn't depend on marker listeners firing,
            // which historically have leaked tracks on coroutine threads.
            val durMs = samples.size * 1000L / SAMPLE_RATE + 150L
            callbackHandler.postDelayed({
                try { track.stop() }   catch (_: Throwable) {}
                try { track.release() } catch (_: Throwable) {}
            }, durMs)
        } catch (_: Throwable) {
            // Couldn't allocate / play. The game logic carries on regardless.
        }
    }

    /** True iff the persistent ambient track is currently live. Used by the
     *  game screen to re-arm ambient if the audio HAL evicted it (which can
     *  happen under transient pressure even when we manage our own tracks). */
    fun isAmbientPlaying(): Boolean {
        val t = ambientTrack ?: return false
        return try {
            t.playState == AudioTrack.PLAYSTATE_PLAYING
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Declare whether ambient should be playing. Idempotent: only kicks the
     * track when state actually changes. Honours [muted] — when muted, we
     * remember the intent ([ambientWanted]) and resume on unmute.
     */
    fun setAmbientActive(active: Boolean) {
        if (ambientWanted == active && (active.not() == (ambientTrack == null))) return
        ambientWanted = active
        if (active && !muted) startAmbient()
        else if (!active) stopAmbient()
    }

    private fun startAmbient() {
        if (ambientTrack != null) return
        val samples = ambientPcm
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
            // Loop the whole buffer forever (-1).
            track.setLoopPoints(0, samples.size, -1)
            track.play()
            ambientTrack = track
        } catch (_: Throwable) {
            // Ambient is decorative; if it can't start we silently skip it.
            ambientTrack = null
        }
    }

    private fun stopAmbient() {
        ambientTrack?.let { t ->
            try { t.pause() }   catch (_: Throwable) {}
            try { t.flush() }   catch (_: Throwable) {}
            try { t.release() } catch (_: Throwable) {}
        }
        ambientTrack = null
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

    // ---- Concrete SFX ----

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

    /** Soft footstep: noise burst with a low pop, ~45 ms. Volume kept low so
     *  it doesn't fight the swoosh / hit cluster during a multi-step replay. */
    private fun synthStep(): ShortArray = render(45) { t ->
        val env = decay(t, 0.04, 8.0)
        noiseW() * env * 0.35 + sineW(t, 80.0) * env * 0.25
    }

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

    // ---- Ambient loop ----

    /**
     * 8-second dungeon drone. Three low sines (A1, E2, A2 — root + fifth +
     * octave) modulated by a 0.25 Hz LFO (exactly two full LFO cycles per
     * buffer so the envelope is continuous at the loop point), plus a
     * 1-pole low-passed noise layer for breath / wind.
     *
     * Every tonal frequency is a multiple of 1/8 Hz (= 0.125 Hz), so each
     * sine closes an integer number of cycles in the 8-second buffer —
     * the loop wrap has no waveform discontinuity. The noise layer is
     * random so it can pop microscopically, but its amplitude is low and
     * the low-pass smooths it; in practice the loop sounds seamless.
     */
    private fun synthAmbientLoop(): ShortArray {
        val durSec = 8.0
        val n = (SAMPLE_RATE * durSec).toInt()
        val out = ShortArray(n)
        var lpf = 0.0
        val lpfAlpha = 0.04  // strong low-pass for the wind layer

        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            // 0.25 Hz LFO, two full cycles over 8 s; ranges 0.5..1.0
            val lfo = 0.75 + 0.25 * sin(2 * PI * 0.25 * t)
            val a1     = sineW(t, 55.0)   * 0.35 * lfo
            val a2     = sineW(t, 110.0)  * 0.18 * lfo
            val fifth  = sineW(t, 82.5)   * 0.10
            val noiseRaw = noiseW() * 0.5
            lpf += lpfAlpha * (noiseRaw - lpf)
            // Overall scale 0.42 keeps the drone clearly under the SFX.
            val v = (a1 + a2 + fifth + lpf * 0.25) * 0.42
            out[i] = toSample(v)
        }
        return out
    }
}
