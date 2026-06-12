package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonsterSlideAiTest {

    /** Build a game and hand-place its single hero and monster. */
    private fun stage(
        heroAt: XYLocation,
        monsterAt: XYLocation,
        observer: GameObserver = SilentGameObserver,
    ): Game {
        Dice.setSeed(3L)
        val game = Game(1, 1, 5, 5, 10, observer)
        game.board.movePiece(game.characters.first { it is Hero }, heroAt)
        game.board.movePiece(game.characters.first { it is Monster }, monsterAt)
        return game
    }

    @Test
    fun `no pits means no slide`() {
        val game = stage(XYLocation(2, 2), XYLocation(0, 0))
        assertNull(GameEngine.bestMonsterSlide(game))
    }

    @Test
    fun `finds the slide that drops a hero onto a pit`() {
        val game = stage(XYLocation(2, 2), XYLocation(0, 0))
        game.board.addPit(XYLocation(2, 3))

        val choice = GameEngine.bestMonsterSlide(game)
        assertNotNull(choice)
        assertEquals(Axis.ROW, choice!!.axis)
        assertEquals(2, choice.index)
        assertEquals(+1, choice.delta)
        assertEquals(10, choice.score)
    }

    @Test
    fun `will not slide its own monster into a pit for a hero kill on the same line`() {
        // Hero (2,2), monster (2,4), pits (2,3) and (2,0).
        // Row 2 right: hero -> (2,3) pit +10, monster wraps -> (2,0) pit -12.
        // Row 2 left:  hero -> (2,1) safe, monster -> (2,3) pit -12.
        // No column slide reaches a pit. Best option is negative => null.
        val game = stage(XYLocation(2, 2), XYLocation(2, 4))
        game.board.addPit(XYLocation(2, 3))
        game.board.addPit(XYLocation(2, 0))

        assertNull(GameEngine.bestMonsterSlide(game))
    }

    @Test
    fun `prefers the line that kills more heroes`() {
        Dice.setSeed(3L)
        val game = Game(2, 1, 5, 5, 10)
        val heroes = game.characters.filterIsInstance<Hero>()
        val monster = game.characters.first { it is Monster }
        // Both heroes on column 1, pits below each of them.
        game.board.movePiece(heroes[0], XYLocation(0, 1))
        game.board.movePiece(heroes[1], XYLocation(2, 1))
        game.board.movePiece(monster, XYLocation(4, 4))
        game.board.addPit(XYLocation(1, 1))
        game.board.addPit(XYLocation(3, 1))

        val choice = GameEngine.bestMonsterSlide(game)
        assertNotNull(choice)
        assertEquals(Axis.COLUMN, choice!!.axis)
        assertEquals(1, choice.index)
        assertEquals(+1, choice.delta)
        assertEquals(20, choice.score)   // double kill
    }

    @Test
    fun `resolveRound executes the monster slide and the hero falls`() {
        val recorder = RecordingGameObserver()
        val game = stage(XYLocation(2, 2), XYLocation(0, 0), recorder)
        game.board.addPit(XYLocation(2, 3))
        val hero = game.characters.first { it is Hero }

        GameEngine.resolveRound(game)

        assertEquals(1, recorder.count<GameEvent.TilesSlid>())
        assertEquals(1, recorder.count<GameEvent.FellInPit>())
        assertFalse(hero.isAlive)
        assertTrue(game.characters.none { it is Hero })
        // TilesSlid must come before FellInPit in the stream.
        val slidIdx = recorder.events.indexOfFirst { it is GameEvent.TilesSlid }
        val fellIdx = recorder.events.indexOfFirst { it is GameEvent.FellInPit }
        assertTrue(slidIdx < fellIdx)
    }

    @Test
    fun `monsters do not slide when every option is harmless`() {
        // A pit exists but nobody is adjacent to it along any line-slide.
        val game = stage(XYLocation(0, 0), XYLocation(4, 4))
        game.board.addPit(XYLocation(2, 2))

        assertNull(GameEngine.bestMonsterSlide(game))
    }
}
