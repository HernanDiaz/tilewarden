package com.tilewarden.core

/**
 * Anything that can occupy a [Square] on the [Board].
 *
 * The [position] is nullable: a piece that hasn't been placed on the board
 * (or that was removed from it) has `position == null`. The [symbol] is a
 * 1-char ASCII representation for console rendering.
 */
interface Piece {
    var position: XYLocation?
    val symbol: Char
}
