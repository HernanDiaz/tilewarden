package com.tilewarden.core

/**
 * The game board: a [rows] x [columns] grid of [Square]s.
 *
 * Pieces occupy at most one square at a time; a piece with `position == null`
 * is "off the board". All move operations validate bounds and emptiness; an
 * invalid move returns `false` and leaves the board untouched.
 */
class Board(val rows: Int, val columns: Int) {

    init {
        require(rows > 0) { "rows must be > 0 (was $rows)" }
        require(columns > 0) { "columns must be > 0 (was $columns)" }
    }

    private val squares: Array<Array<Square>> =
        Array(rows) { Array(columns) { Square() } }

    /** Square at [location]. Assumes the location is already in bounds. */
    private fun squareAt(location: XYLocation): Square =
        squares[location.x][location.y]

    /** `true` if [location] is inside the board. */
    fun isInBounds(location: XYLocation): Boolean =
        location.x in 0 until rows && location.y in 0 until columns

    /** `true` if [location] is in bounds AND empty. */
    fun isFree(location: XYLocation): Boolean =
        isInBounds(location) && squareAt(location).isEmpty

    /**
     * Remove a piece from the board. No-op if the piece was already off-board.
     */
    fun removePiece(piece: Piece) {
        val pos = piece.position ?: return
        squareAt(pos).piece = null
        piece.position = null
    }

    /**
     * Move [piece] to [destination].
     *
     * @return `true` if the move happened, `false` if [destination] is
     *   out of bounds or already occupied. On failure the board is unchanged.
     */
    fun movePiece(piece: Piece, destination: XYLocation): Boolean {
        if (!isFree(destination)) return false
        removePiece(piece)
        piece.position = destination
        squareAt(destination).piece = piece
        return true
    }

    /**
     * Move [piece] one square in [direction] (if the destination is free).
     *
     * @return `true` if the piece moved, `false` if the piece is off-board or
     *   the destination is out of bounds / occupied.
     */
    fun movePiece(piece: Piece, direction: Direction): Boolean {
        val current = piece.position ?: return false
        return movePiece(piece, current + direction)
    }

    /** Multi-line text view of the board. Empty squares render as `-`. */
    override fun toString(): String = buildString {
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                append(squares[row][col])
            }
            append('\n')
        }
    }
}
