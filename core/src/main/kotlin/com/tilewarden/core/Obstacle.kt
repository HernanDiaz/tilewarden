package com.tilewarden.core

/**
 * Inert dungeon furniture (a crate). Occupies a square — blocking walks
 * and line-of-movement — and rides tile slides like everything else.
 * Sliding one onto a pit plugs the pit: both disappear.
 */
class Obstacle(val name: String) : Piece {
    override var position: XYLocation? = null
    override val symbol: Char = '#'
}
