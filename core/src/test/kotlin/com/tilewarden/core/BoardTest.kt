package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

/** A trivial Piece implementation just for board tests. */
private class TestPiece(override val symbol: Char = 'P') : Piece {
    override var position: XYLocation? = null
}

class BoardTest {

    @Test
    fun `constructor rejects non-positive dimensions`() {
        assertThrows<IllegalArgumentException> { Board(0, 5) }
        assertThrows<IllegalArgumentException> { Board(5, 0) }
        assertThrows<IllegalArgumentException> { Board(-1, 5) }
    }

    @Test
    fun `fresh board is fully empty`() {
        val board = Board(3, 4)
        for (x in 0 until 3) {
            for (y in 0 until 4) {
                assertTrue(board.isFree(XYLocation(x, y)))
            }
        }
    }

    @Test
    fun `isInBounds rejects out-of-range coordinates`() {
        val board = Board(2, 3)
        assertTrue(board.isInBounds(XYLocation(0, 0)))
        assertTrue(board.isInBounds(XYLocation(1, 2)))
        assertFalse(board.isInBounds(XYLocation(-1, 0)))
        assertFalse(board.isInBounds(XYLocation(2, 0)))   // x == rows is out
        assertFalse(board.isInBounds(XYLocation(0, 3)))   // y == columns is out
    }

    @Test
    fun `movePiece into empty square places it and tracks position`() {
        val board = Board(3, 3)
        val piece = TestPiece()

        assertTrue(board.movePiece(piece, XYLocation(1, 1)))

        assertEquals(XYLocation(1, 1), piece.position)
        assertFalse(board.isFree(XYLocation(1, 1)))
    }

    @Test
    fun `movePiece onto occupied square fails and leaves both pieces untouched`() {
        val board = Board(3, 3)
        val a = TestPiece('A')
        val b = TestPiece('B')

        board.movePiece(a, XYLocation(1, 1))
        assertFalse(board.movePiece(b, XYLocation(1, 1)))

        assertEquals(XYLocation(1, 1), a.position)
        assertNull(b.position)
    }

    @Test
    fun `movePiece out of bounds fails`() {
        val board = Board(3, 3)
        val piece = TestPiece()

        assertFalse(board.movePiece(piece, XYLocation(-1, 0)))
        assertFalse(board.movePiece(piece, XYLocation(3, 0)))
        assertNull(piece.position)
    }

    @Test
    fun `movePiece with Direction goes one square that way`() {
        val board = Board(3, 3)
        val piece = TestPiece()

        board.movePiece(piece, XYLocation(1, 1))
        assertTrue(board.movePiece(piece, Direction.NORTH))
        assertEquals(XYLocation(0, 1), piece.position)

        assertTrue(board.movePiece(piece, Direction.EAST))
        assertEquals(XYLocation(0, 2), piece.position)
    }

    @Test
    fun `movePiece direction off the edge fails`() {
        val board = Board(3, 3)
        val piece = TestPiece()

        board.movePiece(piece, XYLocation(0, 0))
        assertFalse(board.movePiece(piece, Direction.NORTH))
        assertFalse(board.movePiece(piece, Direction.WEST))
        assertEquals(XYLocation(0, 0), piece.position)
    }

    @Test
    fun `movePiece by Direction on off-board piece fails`() {
        val board = Board(3, 3)
        val piece = TestPiece()

        // piece has never been placed, position == null
        assertFalse(board.movePiece(piece, Direction.NORTH))
    }

    @Test
    fun `removePiece clears both the square and the piece's position`() {
        val board = Board(3, 3)
        val piece = TestPiece()

        board.movePiece(piece, XYLocation(2, 2))
        board.removePiece(piece)

        assertNull(piece.position)
        assertTrue(board.isFree(XYLocation(2, 2)))
    }

    @Test
    fun `removePiece on an off-board piece is a no-op`() {
        val board = Board(3, 3)
        val piece = TestPiece()
        board.removePiece(piece)
        assertNull(piece.position)
    }

    @Test
    fun `moving an already placed piece leaves the previous square free`() {
        val board = Board(3, 3)
        val piece = TestPiece()

        board.movePiece(piece, XYLocation(0, 0))
        board.movePiece(piece, XYLocation(2, 2))

        assertTrue(board.isFree(XYLocation(0, 0)))
        assertFalse(board.isFree(XYLocation(2, 2)))
        assertEquals(XYLocation(2, 2), piece.position)
    }

    @Test
    fun `toString renders piece symbols and dashes for empties`() {
        val board = Board(2, 3)
        val piece = TestPiece('X')
        board.movePiece(piece, XYLocation(0, 1))

        assertEquals("-X-\n---\n", board.toString())
    }
}
