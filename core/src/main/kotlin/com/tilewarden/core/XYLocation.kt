package com.tilewarden.core

/**
 * A 2D position on the board.
 *
 * `x` is the row index (top-down), `y` is the column index (left-right).
 * Immutable: arithmetic helpers return new instances.
 */
data class XYLocation(val x: Int, val y: Int) {

    fun north(): XYLocation = XYLocation(x - 1, y)
    fun south(): XYLocation = XYLocation(x + 1, y)
    fun east(): XYLocation = XYLocation(x, y + 1)
    fun west(): XYLocation = XYLocation(x, y - 1)

    /** `location + Direction.NORTH` returns the adjacent square in that direction. */
    operator fun plus(direction: Direction): XYLocation = when (direction) {
        Direction.NORTH -> north()
        Direction.SOUTH -> south()
        Direction.EAST -> east()
        Direction.WEST -> west()
    }

    override fun toString(): String = "($x,$y)"
}
