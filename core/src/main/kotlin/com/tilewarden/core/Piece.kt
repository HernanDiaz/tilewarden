package com.tilewarden.core

/**
 * Anything that can occupy a [Square] on the [Board].
 *
 * The [position] is mutable so the board can move pieces around;
 * the [symbol] is a 1-char ASCII representation for console rendering.
 */
interface Piece {
    var position: XYLocation
    val symbol: Char
}
