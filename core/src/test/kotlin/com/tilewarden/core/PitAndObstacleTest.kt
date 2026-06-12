package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PitAndObstacleTest {

    private class Token(override val symbol: Char = 'T') : Piece {
        override var position: XYLocation? = null
    }

    @Test
    fun `addPit only works on free squares and not twice`() {
        val board = Board(3, 3)
        val t = Token()
        board.movePiece(t, XYLocation(0, 0))

        assertFalse(board.addPit(XYLocation(0, 0)))   // occupied
        assertFalse(board.addPit(XYLocation(3, 3)))   // out of bounds
        assertTrue(board.addPit(XYLocation(1, 1)))
        assertFalse(board.addPit(XYLocation(1, 1)))   // already a pit
        assertTrue(board.isPit(XYLocation(1, 1)))
    }

    @Test
    fun `voluntary movement refuses pits but slides land on them`() {
        val board = Board(3, 3)
        val t = Token()
        board.movePiece(t, XYLocation(1, 0))
        board.addPit(XYLocation(1, 1))

        // Walking onto the pit is refused.
        assertFalse(board.movePiece(t, XYLocation(1, 1)))
        assertEquals(XYLocation(1, 0), t.position)
        assertFalse(board.isWalkable(XYLocation(1, 1)))

        // A slide shoves the token onto the pit just fine.
        assertTrue(board.slideLine(Axis.ROW, 1, +1))
        assertEquals(XYLocation(1, 1), t.position)
    }

    @Test
    fun `character pushed onto a pit dies via resolvePitFalls`() {
        Dice.setSeed(7L)
        val recorder = RecordingGameObserver()
        val game = Game(1, 1, 4, 4, 10, recorder)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        game.board.movePiece(hero, XYLocation(0, 0))
        game.board.movePiece(monster, XYLocation(2, 0))
        game.board.addPit(XYLocation(2, 1))

        // Slide the monster's row right: it lands on the pit.
        assertTrue(game.board.slideLine(Axis.ROW, 2, +1))
        assertTrue(GameEngine.resolvePitFalls(game))

        assertEquals(1, recorder.count<GameEvent.FellInPit>())
        assertFalse(monster.isAlive)
        assertFalse(game.characters.contains(monster))
        assertTrue(game.board.isFree(XYLocation(2, 1)))   // square reusable...
        assertTrue(game.board.isPit(XYLocation(2, 1)))    // ...but still a pit
    }

    @Test
    fun `crate pushed onto a pit plugs it and both disappear`() {
        Dice.setSeed(7L)
        val game = Game(0, 1, 4, 4, 10, SilentGameObserver, numObstacles = 1)
        val crate = game.obstacles.first()
        game.board.movePiece(crate, XYLocation(1, 0))
        game.board.addPit(XYLocation(1, 1))

        assertTrue(game.board.slideLine(Axis.ROW, 1, +1))
        assertTrue(GameEngine.resolvePitFalls(game))

        assertTrue(game.obstacles.isEmpty())
        assertFalse(game.board.isPit(XYLocation(1, 1)))       // plugged
        assertTrue(game.board.isWalkable(XYLocation(1, 1)))   // walkable again
    }

    @Test
    fun `validPositions excludes pit squares`() {
        Dice.setSeed(7L)
        val game = Game(1, 1, 3, 3, 10)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        game.board.movePiece(hero, XYLocation(1, 1))
        game.board.movePiece(monster, XYLocation(0, 0))
        game.board.addPit(XYLocation(1, 2))

        val options = GameEngine.validPositions(game, hero)
        assertFalse(XYLocation(1, 2) in options)
        assertTrue(XYLocation(0, 1) in options)
    }

    @Test
    fun `placeCharactersRandomly seats crates pits and characters without overlap`() {
        Dice.setSeed(42L)
        val game = Game(2, 2, 5, 5, 10, SilentGameObserver, numObstacles = 3, numPits = 2)
        GameEngine.placeCharactersRandomly(game)

        val occupied = buildList {
            game.characters.forEach { add(it.position!!) }
            game.obstacles.forEach { add(it.position!!) }
        }
        assertEquals(occupied.size, occupied.toSet().size)   // all distinct
        assertEquals(2, game.board.pits.size)
        // No character or crate starts on a pit.
        for (loc in occupied) assertFalse(game.board.isPit(loc))
    }
}
