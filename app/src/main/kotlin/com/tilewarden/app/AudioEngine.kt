package com.tilewarden.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Sample rate of every synthesised buffer. 22050 Hz keeps PCM small without
 *  sacrificing perceptible quality for short, retro-feel SFX. */
private const val SAMPLE_RATE = 22050

/** Samples mixed per write. 1024 ≈ 46 ms of latency — imperceptible for
 *  turn-based SFX, long enough to keep the mixer thread cheap. */
private const val MIX_FRAME = 1024

private const val TAG = "Tilewarden"

/** Ambient drone gain relative to SFX at 1.0. */
private const val AMBIENT_GAIN = 0.5f

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
 * Synthesised 8-bit-style audio with a tiny software mixer.
 *
 * Architecture (third iteration — the robust one):
 *
 * - **One process-wide singleton** ([AudioEngine.get]). Earlier versions
 *   were remembered in composition, so every activity recreation stacked
 *   another engine + sound pool.
 *
 * - **One persistent AudioTrack in MODE_STREAM**, owned by a dedicated
 *   mixer thread. The thread mixes whatever voices are active (SFX
 *   one-shots + the looping ambient drone) into a small frame buffer and
 *   blocking-writes it to the track forever. When nothing is playing it
 *   writes silence — that's normal for games and keeps the stream warm.
 *
 *   Why not per-SFX AudioTracks? They leaked HAL slots (audio died after
 *   a round). Why not MODE_STATIC? It wedged the emulator's audioserver
 *   hard enough to ANR the app at startup. Why not SoundPool? Its
 *   MediaCodec decode sporadically failed on the emulator (status
 *   -2147483648 for random samples) and loads took seconds.
 *
 * - **Everything stays in memory.** PCM is synthesised once on the mixer
 *   thread at startup; no files, no decoders, no binder traffic beyond
 *   the single track.
 *
 * [muted] is Compose-observable. Muting clears live SFX voices and
 * detaches the ambient voice; unmuting re-attaches ambient if a previous
 * [setAmbientActive] call asked for it.
 */
class AudioEngine private constructor() {

    companion object {
        @Volatile private var instance: AudioEngine? = null

        /** Process-wide singleton. Safe to call from any thread. */
        fun get(): AudioEngine =
            instance ?: synchronized(AudioEngine::class.java) {
                instance ?: AudioEngine().also { instance = it }
            }
    }

    private val _muted = mutableStateOf(false)
    var muted: Boolean
        get() = _muted.value
        set(value) {
            if (_muted.value == value) return
            _muted.value = value
            synchronized(voices) {
                if (value) voices.clear()
            }
        }

    /** True iff the game screen wants the ambient drone audible. */
    @Volatile private var ambientWanted: Boolean = false

    /** A sound currently being mixed. One-shots are removed at the end of
     *  their buffer; looping voices wrap around. */
    private class Voice(
        val samples: ShortArray,
        val gain: Float,
        val loop: Boolean,
    ) {
        var pos: Int = 0
    }

    private val voices = ArrayList<Voice>(8)

    /** Filled by the mixer thread before it opens the AudioTrack. */
    @Volatile private var pcm: Map<SoundId, ShortArray> = emptyMap()
    @Volatile private var ambientPcm: ShortArray = ShortArray(0)

    init {
        thread(name = "tilewarden-mixer", isDaemon = true) { mixerLoop() }
    }

    fun play(id: SoundId) {
        if (muted) return
        val samples = pcm[id] ?: return   // synthesis not finished yet
        synchronized(voices) {
            // Cap concurrent voices to keep the mix clean; drop the oldest
            // one-shot if we're full (never the ambient loop).
            if (voices.count { !it.loop } >= 6) {
                voices.indexOfFirst { !it.loop }.takeIf { it >= 0 }?.let { voices.removeAt(it) }
            }
            voices.add(Voice(samples, gain = 1f, loop = false))
        }
    }

    /** Declare whether ambient should be playing. Idempotent; honours
     *  [muted]; the intent survives mute/unmute. */
    fun setAmbientActive(active: Boolean) {
        ambientWanted = active
        synchronized(voices) {
            val current = voices.firstOrNull { it.loop }
            if (!active && current != null) voices.remove(current)
        }
    }

    // ---- Mixer ----

    private fun mixerLoop() {
        // Synthesis happens here so the constructor (main thread) returns
        // instantly and the app can never ANR on audio init.
        pcm = SoundId.entries.associateWith { synthFor(it) }
        ambientPcm = synthAmbientLoop()
        Log.d(TAG, "mixer: synthesis done (${pcm.size} sfx + ambient)")

        val mixBuf = FloatArray(MIX_FRAME)
        val outBuf = ShortArray(MIX_FRAME)

        while (true) {
            val track = openTrack()
            if (track == null) {
                Log.w(TAG, "mixer: could not open AudioTrack, retrying in 3s")
                Thread.sleep(3000)
                continue
            }
            Log.d(TAG, "mixer: AudioTrack open, streaming")
            try {
                track.play()
                while (true) {
                    // (Re)attach the ambient voice when wanted and absent.
                    if (ambientWanted && !muted && ambientPcm.isNotEmpty()) {
                        synchronized(voices) {
                            if (voices.none { it.loop }) {
                                voices.add(Voice(ambientPcm, gain = AMBIENT_GAIN, loop = true))
                            }
                        }
                    }
                    mixFrame(mixBuf, outBuf)
                    val written = track.write(outBuf, 0, MIX_FRAME)
                    if (written < 0) {
                        Log.w(TAG, "mixer: write returned $written, reopening track")
                        break
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "mixer: track died, reopening", t)
            } finally {
                try { track.release() } catch (_: Throwable) {}
            }
            Thread.sleep(250)
        }
    }

    /** Mix all active voices into [outBuf]. Finished one-shots are removed. */
    private fun mixFrame(mixBuf: FloatArray, outBuf: ShortArray) {
        java.util.Arrays.fill(mixBuf, 0f)
        synchronized(voices) {
            val it = voices.iterator()
            while (it.hasNext()) {
                val v = it.next()
                var i = 0
                while (i < MIX_FRAME) {
                    if (v.pos >= v.samples.size) {
                        if (v.loop) v.pos = 0 else break
                    }
                    mixBuf[i] += v.samples[v.pos] * v.gain
                    v.pos++
                    i++
                }
                if (!v.loop && v.pos >= v.samples.size) it.remove()
            }
        }
        for (i in 0 until MIX_FRAME) {
            val s = mixBuf[i]
            outBuf[i] = when {
                s >  Short.MAX_VALUE.toFloat() -> Short.MAX_VALUE
                s <  Short.MIN_VALUE.toFloat() -> Short.MIN_VALUE
                else                           -> s.toInt().toShort()
            }
        }
    }

    private fun openTrack(): AudioTrack? = try {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        AudioTrack.Builder()
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
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, MIX_FRAME * 4))
            .build()
    } catch (t: Throwable) {
        Log.w(TAG, "openTrack failed", t)
        null
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
