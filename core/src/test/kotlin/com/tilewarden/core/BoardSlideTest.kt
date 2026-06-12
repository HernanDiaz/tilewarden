package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardSlideTest {

    /** Minimal piece for board-only tests. */
    private class Token(override val symbol: Char = 'T') : Piece {
        override var position: XYLocation? = null
    }

    private fun place(board: Board, piece: Piece, row: Int, col: Int) {
        assertTrue(board.movePiece(piece, XYLocation(row, col)))
    }

    @Test
    fun `slide row moves every rider one square right`() {
        val board = Board(3, 4)
        val a = Token('a'); val b = Token('b')
        place(board, a, 1, 0)
        place(board, b, 1, 2)

        assertTrue(board.slideLine(Axis.ROW, 1, +1))

        assertEquals(XYLocation(1, 1), a.position)
        assertEquals(XYLocation(1, 3), b.position)
        assertTrue(board.isFree(XYLocation(1, 0)))
        assertTrue(board.isFree(XYLocation(1, 2)))
    }

    @Test
    fun `slide row wraps the piece on the last square to the first`() {
        val board = Board(3, 4)
        val a = Token('a')
        place(board, a, 1, 3)

        assertTrue(board.slideLine(Axis.ROW, 1, +1))

        assertEquals(XYLocation(1, 0), a.position)
        assertFalse(board.isFree(XYLocation(1, 0)))
        assertTrue(board.isFree(XYLocation(1, 3)))
    }

    @Test
    fun `slide row left wraps the piece on the first square to the last`() {
        val board = Board(3, 4)
        val a = Token('a')
        place(board, a, 1, 0)

        assertTrue(board.slideLine(Axis.ROW, 1, -1))

        assertEquals(XYLocation(1, 3), a.position)
    }

    @Test
    fun `slide column moves riders down and wraps`() {
        val board = Board(4, 3)
        val a = Token('a'); val b = Token('b')
        place(board, a, 0, 2)
        place(board, b, 3, 2)

        assertTrue(board.slideLine(Axis.COLUMN, 2, +1))

        assertEquals(XYLocation(1, 2), a.position)
        assertEquals(XYLocation(0, 2), b.position)  // wrapped from the bottom
    }

    @Test
    fun `a full line slides without losing any piece`() {
        val board = Board(2, 3)
        val tokens = List(3) { Token('0' + it) }
        tokens.forEachIndexed { col, t -> place(board, t, 0, col) }

        assertTrue(board.slideLine(Axis.ROW, 0, +1))

        assertEquals(XYLocation(0, 1), tokens[0].position)
        assertEquals(XYLocation(0, 2), tokens[1].position)
        assertEquals(XYLocation(0, 0), tokens[2].position)
    }

    @Test
    fun `pieces off the slid line do not move`() {
        val board = Board(3, 3)
        val bystander = Token('x')
        place(board, bystander, 0, 0)

        assertTrue(board.slideLine(Axis.ROW, 1, +1))

        assertEquals(XYLocation(0, 0), bystander.position)
    }

    @Test
    fun `invalid index or delta is rejected and leaves the board unchanged`() {
        val board = Board(3, 3)
        val a = Token('a')
        place(board, a, 1, 1)

        assertFalse(board.slideLine(Axis.ROW, -1, +1))
        assertFalse(board.slideLine(Axis.ROW, 3, +1))
        assertFalse(board.slideLine(Axis.COLUMN, 3, +1))
        assertFalse(board.slideLine(Axis.ROW, 1, 0))
        assertFalse(board.slideLine(Axis.ROW, 1, 2))

        assertEquals(XYLocation(1, 1), a.position)
    }

    @Test
    fun `empty line slides successfully as a no-op`() {
        val board = Board(3, 3)
        assertTrue(board.slideLine(Axis.ROW, 0, +1))
        for (c in 0 until 3) assertTrue(board.isFree(XYLocation(0, c)))
    }

    @Test
    fun `slide keeps piece and square views consistent`() {
        val board = Board(3, 3)
        val a = Token('a')
        place(board, a, 2, 1)

        assertTrue(board.slideLine(Axis.COLUMN, 1, -1))

        // Piece thinks it's at (1,1); the board must agree.
        assertEquals(XYLocation(1, 1), a.position)
        assertFalse(board.isFree(XYLocation(1, 1)))
        assertTrue(board.isFree(XYLocation(2, 1)))
        // And moving it again through the normal API still works.
        assertTrue(board.movePiece(a, XYLocation(1, 2)))
        assertTrue(board.isFree(XYLocation(1, 1)))
    }
}
