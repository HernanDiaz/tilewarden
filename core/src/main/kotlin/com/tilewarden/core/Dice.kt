package com.tilewarden.core

import kotlin.random.Random

/**
 * Simple dice roller.
 *
 * The RNG is replaceable via [setSeed] so tests can be deterministic.
 */
object Dice {
    private var rng: Random = Random.Default

    /** Roll a single 6-sided die (1d6). */
    fun roll(): Int = roll(6)

    /** Roll a single N-sided die (1dN). */
    fun roll(sides: Int): Int = rng.nextInt(sides) + 1

    /** Seed the RNG. Use in tests for reproducible rolls. */
    fun setSeed(seed: Long) {
        rng = Random(seed)
    }
}
