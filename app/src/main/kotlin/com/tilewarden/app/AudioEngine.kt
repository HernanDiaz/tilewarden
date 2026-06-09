package com.tilewarden.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import androidx.compose.runtime.mutableStateOf
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
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
 * Plays short 8-bit-feel sound effects and a low dungeon-drone ambient loop.
 *
 * Two distinct pipelines:
 *
 * - **SFX**: each `synthXxx` helper renders a 16-bit mono PCM buffer; on
 *   construction those buffers are written to `cacheDir/sfx_*.wav` and
 *   loaded into a single [SoundPool]. [play] just delegates to
 *   `soundPool.play`. SoundPool manages its own stream pool, so there's
 *   no AudioTrack leak risk — the previous per-SFX `AudioTrack` strategy
 *   was unreliable (slots accumulated across rounds until the HAL refused
 *   new tracks).
 *
 * - **Ambient**: a single 8-second drone buffer played by a persistent
 *   [AudioTrack] in `MODE_STATIC` with `setLoopPoints(0, n, -1)`. SoundPool
 *   isn't a great fit for an indefinitely-looping background drone, and
 *   one persistent track is trivial to manage. [isAmbientPlaying] lets a
 *   watchdog detect HAL evictions and re-arm.
 *
 * [muted] is Compose-observable. Flipping it to true silences SFX *and*
 * suspends the ambient; flipping it back resumes ambient if a previous
 * [setAmbientActive] call asked for it.
 */
class AudioEngine(context: Context) {

    private val _muted = mutableStateOf(false)
    var muted: Boolean
        get() = _muted.value
        set(value) {
            if (_muted.value == value) return
            _muted.value = value
            if (value) {
                stopAmbient()
                soundPool.autoPause()
            } else {
                soundPool.autoResume()
                if (ambientWanted) startAmbient()
            }
        }

    /** True iff the caller has asked for ambient to be on; honoured as long
     *  as we're not muted. Survives mute/unmute toggles. */
    private var ambientWanted: Boolean = false
    private var ambientTrack: AudioTrack? = null

    private val ambientPcm: ShortArray = synthAmbientLoop()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** SoundId → SoundPool sample id (handed back by [SoundPool.load]). */
    private val sfxIds: Map<SoundId, Int>

    init {
        // Materialise every SFX as a WAV in cacheDir and hand it to the pool.
        // Files are deterministic and small (<50 KB each); rewriting on every
        // launch is fine and keeps the code stateless.
        val cacheDir = context.cacheDir
        sfxIds = SoundId.entries.associateWith { id ->
            val file = File(cacheDir, "sfx_${id.name}.wav")
            writeWav(file, synthFor(id))
            soundPool.load(file.absolutePath, 1)
        }
    }

    fun play(id: SoundId) {
        if (muted) return
        val sampleId = sfxIds[id] ?: return
        // play returns 0 on failure (e.g. sample still loading); that's fine,
        // we just drop the SFX. Throwing must never propagate up.
        try {
            soundPool.play(sampleId, 1f, 1f, /*priority*/ 1, /*loop*/ 0, /*rate*/ 1f)
        } catch (_: Throwable) {
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
            track.setLoopPoints(0, samples.size, -1)  // forever
            track.play()
            ambientTrack = track
        } catch (_: Throwable) {
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

    // ---- WAV writer ----

    /** Minimal RIFF/WAVE writer for 16-bit signed-PCM mono at [SAMPLE_RATE].
     *  Used to materialise synthesised buffers for [SoundPool.load]. */
    private fun writeWav(file: File, samples: ShortArray) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = samples.size * 2
        val riffSize = 36 + dataSize
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLE(riffSize)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLE(16)                          // PCM subchunk size
            out.writeShortLE(1)                         // PCM format
            out.writeShortLE(numChannels)
            out.writeIntLE(SAMPLE_RATE)
            out.writeIntLE(byteRate)
            out.writeShortLE(blockAlign)
            out.writeShortLE(bitsPerSample)
            out.writeBytes("data")
            out.writeIntLE(dataSize)
            for (s in samples) out.writeShortLE(s.toInt())
        }
    }

    private fun DataOutputStream.writeIntLE(v: Int) {
        writeByte(v and 0xFF)
        writeByte((v ushr 8) and 0xFF)
        writeByte((v ushr 16) and 0xFF)
        writeByte((v ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(v: Int) {
        writeByte(v and 0xFF)
        writeByte((v ushr 8) and 0xFF)
    }

    // ---- Render helpers ----

    private fun synthFor(id: SoundId): ShortArray = when (id) {
        SoundId.ATTACK_SWING -> synthAttackSwing()
        SoundId.HIT          -> synthHit()
        SoundId.BLOCK        -> synthBlock()
        SoundId.DEATH        -> synthDeath()
        SoundId.VICTORY      -> synthVictory()
        SoundId.DEFEAT       -> synthDefeat()
        SoundId.STEP         -> synthStep()
    }

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

    private fun square(t: Double, freq: Double): Double =
        if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0

    private fun sineW(t: Double, freq: Double): Double = sin(2 * PI * freq * t)

    private fun noiseW(): Double = Random.nextDouble() * 2 - 1

    private fun decay(t: Double, dur: Double, k: Double = 4.0): Double =
        exp(-t * k / dur)

    // ---- Concrete SFX ----

    private fun synthAttackSwing(): ShortArray = render(120) { t ->
        val env = decay(t, 0.12, 5.0)
        noiseW() * env * 0.6 + sineW(t, 600.0 - 4500.0 * t) * env * 0.25
    }

    private fun synthHit(): ShortArray = render(150) { t ->
        val env = decay(t, 0.15, 6.0)
        square(t, 90.0) * env * 0.55 + noiseW() * env * 0.35
    }

    private fun synthBlock(): ShortArray = render(180) { t ->
        val a = if (t < 0.05) sineW(t, 1900.0) * decay(t, 0.05, 8.0) * 0.5 else 0.0
        val b = if (t in 0.06..0.18) sineW(t, 1300.0) * decay(t - 0.06, 0.12, 6.0) * 0.4
                else 0.0
        a + b
    }

    private fun synthDeath(): ShortArray = render(400) { t ->
        val env = decay(t, 0.4, 4.0)
        val pitch = 220.0 * exp(-t * 3.5)
        square(t, pitch) * env * 0.55 + noiseW() * env * 0.15
    }

    private fun synthVictory(): ShortArray =
        concatNotes(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), noteMs = 110)

    private fun synthDefeat(): ShortArray =
        concatNotes(doubleArrayOf(523.25, 440.0, 349.23, 261.63), noteMs = 140)

    private fun synthStep(): ShortArray = render(45) { t ->
        val env = decay(t, 0.04, 8.0)
        noiseW() * env * 0.35 + sineW(t, 80.0) * env * 0.25
    }

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
     * 8-second dungeon drone. Three low sines (A1, E2-ish, A2 — root + fifth
     * + octave) modulated by a 0.25 Hz LFO (exactly two full LFO cycles per
     * buffer so the envelope is continuous at the loop point), plus a
     * 1-pole low-passed noise layer for breath / wind.
     *
     * Every tonal frequency is a multiple of 1/8 Hz (= 0.125 Hz), so each
     * sine closes an integer number of cycles in the 8-second buffer.
     */
    private fun synthAmbientLoop(): ShortArray {
        val durSec = 8.0
        val n = (SAMPLE_RATE * durSec).toInt()
        val out = ShortArray(n)
        var lpf = 0.0
        val lpfAlpha = 0.04
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val lfo = 0.75 + 0.25 * sin(2 * PI * 0.25 * t)
            val a1    = sineW(t, 55.0)  * 0.35 * lfo
            val a2    = sineW(t, 110.0) * 0.18 * lfo
            val fifth = sineW(t, 82.5)  * 0.10
            val noiseRaw = noiseW() * 0.5
            lpf += lpfAlpha * (noiseRaw - lpf)
            val v = (a1 + a2 + fifth + lpf * 0.25) * 0.42
            out[i] = toSample(v)
        }
        return out
    }
}
