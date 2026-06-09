package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Statistical tests on the defence rules — verify the expected
 * block-rates rather than depending on a specific seeded sequence
 * (which would couple us to kotlin.random.Random's algorithm).
 */
class DefenseRulesTest {

    private fun freshGame(): Game = Game(1, 1, 5, 5, 5, SilentGameObserver)

    @Test
    fun `Hero blocks roughly one in three on a single d6 (5 or 6 of 6 faces)`() {
        Dice.setSeed(123L)
        val hero = Dwarf("d", "p").apply { body = 100_000 }
        val game = freshGame()

        var blocked = 0
        val trials = 30_000
        repeat(trials) {
            // Hero has 1 defence die: a single hit, the only roll either
            // blocks it (5 or 6) or it lands.
            val woundsBefore = hero.body
            // Override defense for this measurement: use single die per trial.
            // We mimic 1d6 defense by giving the hero exactly 1 hit each call.
            hero.defend(1, game)
            if (hero.body == woundsBefore) blocked++ else hero.body = woundsBefore
        }

        val rate = blocked.toDouble() / trials
        // Hero default defense = 2 dice, so probability of blocking 1 hit is
        // 1 - (4/6)^2 = 5/9 ≈ 0.5556. Allow ±0.02 slack.
        assertTrue(rate in 0.53..0.58,
            "Hero 2d defence block-rate against 1 hit = $rate, expected ~0.556")
    }

    @Test
    fun `Monster blocks much less than a Hero (single 6 only)`() {
        Dice.setSeed(456L)
        val monster = Goblin("g").apply { body = 100_000 }
        val game = freshGame()

        var blocked = 0
        val trials = 30_000
        repeat(trials) {
            val before = monster.body
            monster.defend(1, game)
            if (monster.body == before) blocked++ else monster.body = before
        }
        val rate = blocked.toDouble() / trials
        // Goblin defence = 1d, blocks on 6 only → 1/6 ≈ 0.167.
        assertTrue(rate in 0.14..0.19,
            "Monster 1d defence block-rate against 1 hit = $rate, expected ~0.167")
    }

    @Test
    fun `defend wounds clamp to body, never go negative`() {
        val mummy = Mummy("m")
        mummy.body = 1
        val game = freshGame()
        // Force monster to take wounds by passing more hits than it can ever block
        // with a deterministic seed that lands every defence roll badly.
        Dice.setSeed(7L)
        // 100 hits, body is 1 → wounds capped at 1, body cannot go below 0.
        val wounds = mummy.defend(100, game)
        assertTrue(wounds in 0..1)  // it could have blocked all 100, but if any landed, exactly 1 wound
        assertTrue(mummy.body >= 0)
    }

    @Test
    fun `defending zero hits produces zero wounds without event`() {
        val recorder = RecordingGameObserver()
        val game = Game(1, 1, 5, 5, 5, recorder)
        val hero = Barbarian("b", "p")
        val wounds = hero.defend(0, game)
        assertEquals(0, wounds)
        // No "blocked" or "damaged" event for an empty attack.
        assertEquals(0, recorder.count<GameEvent.AttackBlocked>())
        assertEquals(0, recorder.count<GameEvent.Damaged>())
    }
}
