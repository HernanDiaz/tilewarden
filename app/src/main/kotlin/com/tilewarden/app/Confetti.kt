package com.tilewarden.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT       = 60
private const val LIFETIME_MS          = 3000f
private const val GRAVITY_PX_PER_S2    = 1400f
private const val INITIAL_SPEED_MIN    = 250f
private const val INITIAL_SPEED_MAX    = 550f
private const val PARTICLE_SIZE_MIN_PX = 6f
private const val PARTICLE_SIZE_MAX_PX = 11f

private val CONFETTI_COLORS = listOf(
    Color(0xFFF0C969),  // gold
    Color(0xFFFFFFFF),  // white
    Color(0xFFE25D4A),  // terracotta
    Color(0xFF7AA84A),  // green
    Color(0xFF6FB4FF),  // sky blue
    Color(0xFFFF8AC4),  // pink
)

/**
 * Particle "snow" of coloured squares falling from the top of the screen,
 * spinning and fading. Lives [LIFETIME_MS] ms and then disappears.
 *
 * Uses [withFrameMillis] for an actual wall-clock time base, so all
 * particles move consistently regardless of how heavy the rest of the
 * frame is.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val particles = remember { List(PARTICLE_COUNT) { Particle.spawn() } }
    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (elapsed < LIFETIME_MS) {
            withFrameMillis { now ->
                elapsed = (now - start).toFloat()
            }
        }
    }

    if (elapsed >= LIFETIME_MS) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tSec = elapsed / 1000f
        val lifeFrac = elapsed / LIFETIME_MS

        for (p in particles) {
            val cx = p.startXFrac * w + p.vx * tSec
            val cy = p.startYFrac * h + p.vy * tSec + 0.5f * GRAVITY_PX_PER_S2 * tSec * tSec

            if (cy > h + 60f) continue  // already fell off the screen

            val angle = p.rotationStart + p.rotationSpeed * tSec
            val alpha = (1f - lifeFrac).coerceIn(0f, 1f)

            rotate(degrees = angle, pivot = Offset(cx, cy)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(cx - p.size / 2f, cy - p.size / 2f),
                    size = Size(p.size, p.size),
                )
            }
        }
    }
}

/** A single confetti chip with its own random initial conditions. */
private data class Particle(
    val startXFrac: Float,
    val startYFrac: Float,
    val vx: Float,
    val vy: Float,
    val rotationStart: Float,
    val rotationSpeed: Float,
    val size: Float,
    val color: Color,
) {
    companion object {
        fun spawn(): Particle {
            // Launch all particles from a band near the top of the screen,
            // shooting outward and slightly downward. Gravity does the rest.
            val angleDeg = Random.nextFloat() * 120f + 30f      // 30°..150°  (mostly downward / sideways)
            val angleRad = angleDeg * PI.toFloat() / 180f
            val speed = Random.nextFloat() * (INITIAL_SPEED_MAX - INITIAL_SPEED_MIN) + INITIAL_SPEED_MIN
            return Particle(
                startXFrac    = Random.nextFloat(),                  // anywhere across the width
                startYFrac    = Random.nextFloat() * 0.1f - 0.05f,   // slightly above-screen so it sweeps in
                vx            = cos(angleRad) * speed,
                vy            = sin(angleRad) * speed,
                rotationStart = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,  // -360..360 deg/s
                size          = Random.nextFloat() * (PARTICLE_SIZE_MAX_PX - PARTICLE_SIZE_MIN_PX) + PARTICLE_SIZE_MIN_PX,
                color         = CONFETTI_COLORS.random(),
            )
        }
    }
}
