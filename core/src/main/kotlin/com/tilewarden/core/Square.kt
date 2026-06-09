package com.tilewarden.core

/**
 * A single cell on the board.
 *
 * Holds at most one [Piece]. Empty squares render as "-".
 */
class Square {
    var piece: Piece? = null

    val isEmpty: Boolean get() = piece == null

    override fun toString(): String = piece?.symbol?.toString() ?: "-"
}
