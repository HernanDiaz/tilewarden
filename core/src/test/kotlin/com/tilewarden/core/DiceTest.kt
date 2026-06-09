package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiceTest {

    @Test
    fun `default roll stays within 1 to 6`() {
        Dice.setSeed(0L)
        repeat(1000) {
            val r = Dice.roll()
            assertTrue(r in 1..6, "1d6 produced out-of-range value: $r")
        }
    }

    @Test
    fun `roll N stays within 1 to N`() {
        Dice.setSeed(0L)
        repeat(1000) {
            val r = Dice.roll(20)
            assertTrue(r in 1..20, "1d20 produced out-of-range value: $r")
        }
    }

    @Test
    fun `seeding produces deterministic sequences`() {
        Dice.setSeed(42L)
        val first = List(10) { Dice.roll() }

        Dice.setSeed(42L)
        val second = List(10) { Dice.roll() }

        assertEquals(first, second)
    }

    @Test
    fun `different seeds give different sequences`() {
        Dice.setSeed(1L)
        val a = List(10) { Dice.roll() }

        Dice.setSeed(2L)
        val b = List(10) { Dice.roll() }

        // Vanishingly unlikely 10 rolls collide entirely under two different seeds.
        assertTrue(a != b, "Two different seeds produced identical 10-roll sequences: $a")
    }
}
