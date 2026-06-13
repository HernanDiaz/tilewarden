package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpareTileTest {

    private class Token(override val symbol: Char = 'T') : Piece {
        override var position: XYLocation? = null
    }

    @Test
    fun `holding a pit, a far-edge piece wraps onto the injected pit`() {
        val board = Board(3, 4)
        val a = Token('a')
        board.movePiece(a, XYLocation(1, 3))   // far edge for delta +1

        val ejected = board.slideLineWithSpare(Axis.ROW, 1, +1, TileType.PIT)

        assertEquals(TileType.FLOOR, ejected)             // far edge was plain floor
        assertEquals(XYLocation(1, 0), a.position)        // wrapped to the entry edge
        assertTrue(board.isPit(XYLocation(1, 0)))         // the injected pit is under it
    }

    @Test
    fun `the far-edge terrain is ejected into the hand and that cell becomes floor`() {
        val board = Board(3, 4)
        board.addPit(XYLocation(1, 3))        // a pit sitting at the far edge

        val ejected = board.slideLineWithSpare(Axis.ROW, 1, +1, TileType.FLOOR)

        assertEquals(TileType.PIT, ejected)               // reloaded a pit into the hand
        assertFalse(board.isPit(XYLocation(1, 3)))        // far cell emptied
        assertFalse(board.isPit(XYLocation(1, 0)))        // injected floor, not a pit
    }

    @Test
    fun `interior pits are untouched, so flexible kills still work`() {
        val board = Board(3, 5)
        val a = Token('a')
        board.movePiece(a, XYLocation(1, 1))
        board.addPit(XYLocation(1, 2))        // interior pit, one step ahead

        board.slideLineWithSpare(Axis.ROW, 1, +1, TileType.FLOOR)

        assertEquals(XYLocation(1, 2), a.position)        // rode forward one square
        assertTrue(board.isPit(XYLocation(1, 2)))         // interior pit stayed put
        // -> the piece is now standing on the interior pit (resolvePitFalls kills it)
    }

    @Test
    fun `delta minus one injects at the high edge and wraps the low-edge piece`() {
        val board = Board(3, 4)
        val a = Token('a')
        board.movePiece(a, XYLocation(1, 0))   // far edge for delta -1 is index 0

        val ejected = board.slideLineWithSpare(Axis.ROW, 1, -1, TileType.PIT)

        assertEquals(TileType.FLOOR, ejected)
        assertEquals(XYLocation(1, 3), a.position)        // wrapped to the high edge
        assertTrue(board.isPit(XYLocation(1, 3)))
    }

    @Test
    fun `column slide carries the spare the same way`() {
        val board = Board(4, 3)
        val a = Token('a')
        board.movePiece(a, XYLocation(3, 2))   // bottom of column 2, far edge for +1

        val ejected = board.slideLineWithSpare(Axis.COLUMN, 2, +1, TileType.PIT)

        assertEquals(TileType.FLOOR, ejected)
        assertEquals(XYLocation(0, 2), a.position)        // wrapped to the top
        assertTrue(board.isPit(XYLocation(0, 2)))
    }

    @Test
    fun `invalid arguments leave the board untouched and return null`() {
        val board = Board(3, 3)
        board.addPit(XYLocation(1, 1))

        assertNull(board.slideLineWithSpare(Axis.ROW, -1, +1, TileType.PIT))
        assertNull(board.slideLineWithSpare(Axis.ROW, 3, +1, TileType.PIT))
        assertNull(board.slideLineWithSpare(Axis.ROW, 1, 0, TileType.PIT))
        assertNull(board.slideLineWithSpare(Axis.ROW, 1, 2, TileType.PIT))

        assertTrue(board.isPit(XYLocation(1, 1)))   // unchanged
    }

    @Test
    fun `pushing a held pit under a game character kills it on the next fall resolve`() {
        Dice.setSeed(5L)
        val game = Game(1, 1, 4, 4, 10)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        game.board.movePiece(hero, XYLocation(0, 0))
        game.board.movePiece(monster, XYLocation(2, 3))   // far edge of row 2

        game.board.slideLineWithSpare(Axis.ROW, 2, +1, TileType.PIT)
        assertEquals(XYLocation(2, 0), monster.position)  // wrapped onto the pit
        assertTrue(GameEngine.resolvePitFalls(game))
        assertFalse(monster.isAlive)
        assertTrue(game.characters.none { it is Monster })
    }
}
