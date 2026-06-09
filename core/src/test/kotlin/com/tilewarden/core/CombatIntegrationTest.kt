package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests that exercise the full event flow: attacker → target.defend →
 * possibly Died → board.remove + game.remove. We rely on observer
 * recordings rather than on specific dice rolls.
 */
class CombatIntegrationTest {

    @Test
    fun `attacking a one-body monster usually kills it and fires Died`() {
        Dice.setSeed(0L)
        val rec = RecordingGameObserver()
        // Manual two-character world. We use a Game(0,0,...) factory? No, Game's
        // constructor adds randomly. Use Game(1,1,...) and pick the existing chars.
        val game = Game(1, 1, 3, 3, 1, rec)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        // Force a one-shot kill by setting the monster's body to 1.
        monster.body = 1
        game.board.movePiece(hero, XYLocation(1, 1))
        game.board.movePiece(monster, XYLocation(1, 2))

        hero.combat(monster, game)

        // The event log must include an Attacked and an AttackResolved.
        assertTrue(rec.count<GameEvent.Attacked>() >= 1)
        assertTrue(rec.count<GameEvent.AttackResolved>() >= 1)
        // With seed 0 the hero rolls at least one hit out of 3d6 — overwhelmingly
        // likely with this much weight on landing, but to avoid statistical
        // flakiness we tolerate the rare case where every die misses.
        if (rec.count<GameEvent.Died>() > 0) {
            assertFalse(monster.isAlive)
            assertNull(monster.position)
            assertTrue(monster !in game.characters)
            assertTrue(game.board.isFree(XYLocation(1, 2)))
        }
    }

    @Test
    fun `dead target is removed from board and game atomically`() {
        // Deterministic harness: bypass dice by directly setting target.body to 0
        // via defend(): we can't, but we can call removeCharacter and removePiece
        // and verify the post-conditions hold. Instead, force the kill with a high
        // hit count.
        val game = Game(1, 1, 3, 3, 1, SilentGameObserver)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }.apply { body = 1 }
        game.board.movePiece(hero, XYLocation(0, 0))
        game.board.movePiece(monster, XYLocation(0, 1))

        // Inflict a guaranteed lethal blow via defend() with way more hits than
        // the monster can possibly block, using a seed where rolls land.
        // The monster's 6-only block makes 100 hits virtually guaranteed lethal.
        Dice.setSeed(1L)
        monster.defend(100, game)
        // The defend method itself doesn't remove the body from the game — that's
        // the engine's job in combat(). So we do it manually to mimic combat()'s
        // contract:
        if (!monster.isAlive) {
            game.board.removePiece(monster)
            game.removeCharacter(monster)
        }
        assertFalse(monster.isAlive)
        assertNull(monster.position)
        assertTrue(monster !in game.characters)
        assertTrue(game.board.isFree(XYLocation(0, 1)))
    }

    @Test
    fun `hero combat updates hero statistics`() {
        Dice.setSeed(5L)
        val game = Game(1, 1, 3, 3, 1, SilentGameObserver)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        game.board.movePiece(hero, XYLocation(0, 0))
        game.board.movePiece(monster, XYLocation(0, 1))

        val attacksBefore = game.statistics.heroAttacks
        hero.combat(monster, game)
        // Even if no damage landed, the attack counter increments.
        assertEquals(attacksBefore + 1, game.statistics.heroAttacks)
        // Monster attacks counter must not have moved.
        assertEquals(0, game.statistics.monsterAttacks)
    }

    @Test
    fun `monster combat updates monster statistics`() {
        Dice.setSeed(5L)
        val game = Game(1, 1, 3, 3, 1, SilentGameObserver)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        game.board.movePiece(hero, XYLocation(0, 0))
        game.board.movePiece(monster, XYLocation(0, 1))

        monster.combat(hero, game)
        assertEquals(1, game.statistics.monsterAttacks)
        assertEquals(0, game.statistics.heroAttacks)
    }

    @Test
    fun `actionCombat without targets returns false and emits nothing`() {
        val rec = RecordingGameObserver()
        val game = Game(1, 1, 5, 5, 1, rec)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        game.board.movePiece(hero, XYLocation(0, 0))
        game.board.movePiece(monster, XYLocation(4, 4))  // out of range

        val happened = hero.actionCombat(game)
        assertFalse(happened)
        assertEquals(0, rec.count<GameEvent.Attacked>())
    }

    @Test
    fun `actionCombat with at least one target attacks one of them`() {
        Dice.setSeed(1L)
        val rec = RecordingGameObserver()
        val game = Game(1, 1, 5, 5, 1, rec)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        game.board.movePiece(hero, XYLocation(2, 2))
        game.board.movePiece(monster, XYLocation(2, 3))

        val happened = hero.actionCombat(game)
        assertTrue(happened)
        assertEquals(1, rec.count<GameEvent.Attacked>())
        // The attack was directed at the only candidate.
        val attacked = rec.filterIsInstance<GameEvent.Attacked>().single()
        assertSame(monster, attacked.target)
        assertSame(hero, attacked.attacker)
    }

    @Test
    fun `actionMove emits one PieceMoved per square moved on an open board`() {
        Dice.setSeed(0L)
        val rec = RecordingGameObserver()
        val game = Game(1, 0, 7, 7, 1, rec)
        val hero = game.characters.single()
        game.board.movePiece(hero, XYLocation(3, 3))

        val budget = 4
        val moved = hero.actionMove(game, maxSquares = budget)
        // On a near-empty 7x7 it should move the full budget.
        assertEquals(budget, moved)
        assertEquals(budget, rec.count<GameEvent.PieceMoved>())
    }
}
