package com.tilewarden.core

/** Board line orientation for tile-slide operations. */
enum class Axis { ROW, COLUMN }

/** The terrain a floor cell can carry — the kind of tile the Warden holds
 *  in hand and pushes into the dungeon. */
enum class TileType { FLOOR, PIT }
