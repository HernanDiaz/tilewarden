package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end smoke test: spin up a complete game, run it via the engine,
 * and verify the high-level invariants that any consumer (the future
 * Android UI, the future desktop UI) will care about.
 *
 * We avoid asserting on specific moves/hits to keep the test stable
 * against RNG implementation changes; the assertions describe behaviour
 * any sensible game loop must satisfy.
 */
class GameLoopSmokeTest {

    @Test
    fun `a full game emits GameStarted and GameEnded in order`() {
        Dice.setSeed(42L)
        val rec = RecordingGameObserver()
        val game = Game(2, 3, 5, 6, 10, rec)
        GameEngine.placeCharactersRandomly(game)

        GameEngine.runGame(game)

        // First event = GameStarted
        assertTrue(rec.events.first() is GameEvent.GameStarted)
        // Last event = GameEnded
        assertTrue(rec.events.last() is GameEvent.GameEnded)
        // Some rounds happened
        assertTrue(rec.count<GameEvent.RoundStarted>() >= 1)
        // Each RoundStarted has a matching RoundEnded (game may end mid-round
        // but the very last RoundEnded should pair with the last RoundStarted).
        assertTrue(
            rec.count<GameEvent.RoundEnded>() == rec.count<GameEvent.RoundStarted>(),
            "Mismatched rounds: started=${rec.count<GameEvent.RoundStarted>()} " +
                "ended=${rec.count<GameEvent.RoundEnded>()}"
        )
    }

    @Test
    fun `rounds are numbered sequentially starting at 1`() {
        Dice.setSeed(1L)
        val rec = RecordingGameObserver()
        val game = Game(2, 2, 4, 4, 5, rec)
        GameEngine.placeCharactersRandomly(game)
        GameEngine.runGame(game)

        val numbers = rec.filterIsInstance<GameEvent.RoundStarted>().map { it.round }
        assertEquals(numbers.indices.map { it + 1 }, numbers)
    }

    @Test
    fun `game terminates within the round budget`() {
        Dice.setSeed(99L)
        val rec = RecordingGameObserver()
        val budget = 7
        val game = Game(2, 2, 5, 5, budget, rec)
        GameEngine.placeCharactersRandomly(game)
        GameEngine.runGame(game)

        assertTrue(
            game.currentRound <= budget + 1,
            "Game ran past its budget: round=${game.currentRound}, budget=$budget"
        )
    }

    @Test
    fun `game ends with one of the three winner states`() {
        Dice.setSeed(2L)
        val rec = RecordingGameObserver()
        val game = Game(2, 2, 4, 4, 8, rec)
        GameEngine.placeCharactersRandomly(game)
        GameEngine.runGame(game)

        val end = rec.filterIsInstance<GameEvent.GameEnded>().single()
        assertTrue(end.winner in Side.entries)
    }

    @Test
    fun `characters that died are not in the active list at game end`() {
        Dice.setSeed(3L)
        val rec = RecordingGameObserver()
        val game = Game(2, 3, 4, 4, 12, rec)
        GameEngine.placeCharactersRandomly(game)
        GameEngine.runGame(game)

        for (c in game.characters) {
            assertTrue(c.isAlive, "Dead character still in active list: ${c.name}")
        }
        // Every character that received a Died event must have been removed
        // from the active list and have null position.
        for (event in rec.filterIsInstance<GameEvent.Died>()) {
            assertTrue(event.character !in game.characters)
            // Their position has been cleared.
            assertEquals(null, event.character.position)
        }
    }

    @Test
    fun `statistics consistent with the events emitted`() {
        Dice.setSeed(8L)
        val rec = RecordingGameObserver()
        val game = Game(2, 2, 5, 5, 8, rec)
        GameEngine.placeCharactersRandomly(game)
        GameEngine.runGame(game)

        // Every Hero-launched Attacked event corresponds to an increment of
        // heroAttacks; same for Monsters. We can't recover damage from events
        // alone, but the attack counters must equal the count of Attacked
        // events split by attacker side.
        val heroAttacks = rec.filterIsInstance<GameEvent.Attacked>()
            .count { it.attacker is Hero }
        val monsterAttacks = rec.filterIsInstance<GameEvent.Attacked>()
            .count { it.attacker is Monster }
        assertEquals(heroAttacks, game.statistics.heroAttacks)
        assertEquals(monsterAttacks, game.statistics.monsterAttacks)
    }
}
