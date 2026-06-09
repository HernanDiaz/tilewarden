package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class GameTest {

    @Test
    fun `constructor rejects negative or invalid counts`() {
        assertThrows<IllegalArgumentException> { Game(-1, 1, 5, 5, 5) }
        assertThrows<IllegalArgumentException> { Game(1, -1, 5, 5, 5) }
        assertThrows<IllegalArgumentException> { Game(1, 1, 5, 5, 0) }
    }

    @Test
    fun `constructor creates the requested number of heroes and monsters`() {
        Dice.setSeed(1L)
        val game = Game(numHeroes = 3, numMonsters = 4, boardRows = 5, boardColumns = 5,
            totalRounds = 10)
        assertEquals(7, game.characters.size)
        assertEquals(3, game.characters.count { it is Hero })
        assertEquals(4, game.characters.count { it is Monster })
    }

    @Test
    fun `board has the requested dimensions`() {
        val game = Game(1, 1, 4, 8, 5)
        assertEquals(4, game.board.rows)
        assertEquals(8, game.board.columns)
    }

    @Test
    fun `currentRound starts at 1 and totalRounds is preserved`() {
        val game = Game(1, 1, 5, 5, 20)
        assertEquals(1, game.currentRound)
        assertEquals(20, game.totalRounds)
    }

    @Test
    fun `removeCharacter takes the character out of the active list`() {
        val game = Game(2, 2, 5, 5, 5)
        val victim = game.characters.first()
        game.removeCharacter(victim)
        assertEquals(3, game.characters.size)
        assertTrue(victim !in game.characters)
    }

    @Test
    fun `removeCharacter on someone already gone is a no-op`() {
        val game = Game(1, 1, 5, 5, 5)
        val outsider = Mummy("ghost")
        val before = game.characters.size
        game.removeCharacter(outsider)
        assertEquals(before, game.characters.size)
    }

    @Test
    fun `default observer is silent`() {
        val game = Game(1, 1, 5, 5, 5)
        assertSame(SilentGameObserver, game.observer)
    }

    @Test
    fun `custom observer receives events via notify`() {
        val recorder = RecordingGameObserver()
        val game = Game(1, 1, 5, 5, 5, recorder)
        game.notify(GameEvent.RoundStarted(3))
        assertEquals(1, recorder.events.size)
        assertEquals(GameEvent.RoundStarted(3), recorder.events.first())
    }

    @Test
    fun `toString includes all characters and the board grid`() {
        val game = Game(1, 1, 3, 3, 5)
        // Place each character at a known spot
        val all = game.characters
        game.board.movePiece(all[0], XYLocation(0, 0))
        game.board.movePiece(all[1], XYLocation(2, 2))
        val text = game.toString()
        assertTrue(text.contains(all[0].name))
        assertTrue(text.contains(all[1].name))
        assertTrue(text.contains(all[0].symbol.toString()))
        assertTrue(text.contains(all[1].symbol.toString()))
    }

    @Test
    fun `statistics start at zero and survive being passed around`() {
        val game = Game(1, 1, 5, 5, 5)
        assertEquals(0, game.statistics.heroAttacks)
        assertEquals(0, game.statistics.monsterAttacks)
        assertEquals(0, game.statistics.heroDamageDealt)
        assertEquals(0, game.statistics.monsterDamageDealt)
    }
}
