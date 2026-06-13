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

    private val pitSet = LinkedHashSet<XYLocation>()

    /** Open pits in the dungeon floor. Pits never move — they are holes in
     *  the world itself, not tiles — so a slide can push a piece onto one. */
    val pits: Set<XYLocation> get() = pitSet

    /** Square at [location]. Assumes the location is already in bounds. */
    private fun squareAt(location: XYLocation): Square =
        squares[location.x][location.y]

    /** `true` if [location] is inside the board. */
    fun isInBounds(location: XYLocation): Boolean =
        location.x in 0 until rows && location.y in 0 until columns

    /** `true` if [location] is in bounds AND empty. */
    fun isFree(location: XYLocation): Boolean =
        isInBounds(location) && squareAt(location).isEmpty

    /** `true` if a piece may voluntarily step onto [location]:
     *  in bounds, empty, and not an open pit. */
    fun isWalkable(location: XYLocation): Boolean =
        isFree(location) && location !in pitSet

    /** `true` if there is an open pit at [location]. */
    fun isPit(location: XYLocation): Boolean = location in pitSet

    /**
     * Open a pit at [location]. Fails (returns `false`) out of bounds, on
     * an occupied square, or where a pit already is.
     */
    fun addPit(location: XYLocation): Boolean {
        if (!isFree(location) || location in pitSet) return false
        pitSet.add(location)
        return true
    }

    /** Close the pit at [location] (e.g. plugged by a crate). */
    fun removePit(location: XYLocation) {
        pitSet.remove(location)
    }

    /** Terrain type of [location] (out-of-bounds counts as FLOOR). */
    fun terrainAt(location: XYLocation): TileType =
        if (location in pitSet) TileType.PIT else TileType.FLOOR

    /** Force the terrain at [location] — no occupancy/duplication checks.
     *  Used by the spare-tile slide, which deliberately drops a pit under
     *  whatever wrapped onto the entry edge. */
    private fun forceTerrain(location: XYLocation, type: TileType) {
        if (type == TileType.PIT) pitSet.add(location) else pitSet.remove(location)
    }

    /**
     * Remove a piece from the board. No-op if the piece was already off-board.
     */
    fun removePiece(piece: Piece) {
        val pos = piece.position ?: return
        squareAt(pos).piece = null
        piece.position = null
    }

    /**
     * Move [piece] to [destination]. This is VOLUNTARY movement — nothing
     * walks into an open pit on purpose, so pit squares are refused. Only
     * [slideLine] (forced movement) can land a piece on a pit.
     *
     * @return `true` if the move happened, `false` if [destination] is
     *   out of bounds, occupied, or an open pit. On failure the board is
     *   unchanged.
     */
    fun movePiece(piece: Piece, destination: XYLocation): Boolean {
        if (!isWalkable(destination)) return false
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

    /**
     * Slide an entire line of tiles one square along its axis, wrapping
     * around at the edges. Every piece standing on the line rides along;
     * the piece pushed past the last square re-enters at the first.
     *
     * This is the Warden Action at the heart of the game: the board's
     * geometry is a weapon. Because the whole line moves as one, pieces
     * can never collide — each destination square is vacated by the same
     * slide that fills it.
     *
     * @param axis  [Axis.ROW] shifts row [index] horizontally,
     *              [Axis.COLUMN] shifts column [index] vertically.
     * @param index which row / column to slide.
     * @param delta +1 (right / down) or -1 (left / up).
     * @return `true` if the slide happened, `false` for an out-of-range
     *   [index] or a [delta] other than ±1. On failure the board is
     *   unchanged.
     */
    fun slideLine(axis: Axis, index: Int, delta: Int): Boolean {
        if (delta != 1 && delta != -1) return false
        val lineLength = when (axis) {
            Axis.ROW    -> { if (index !in 0 until rows) return false; columns }
            Axis.COLUMN -> { if (index !in 0 until columns) return false; rows }
        }
        fun locAt(i: Int): XYLocation = when (axis) {
            Axis.ROW    -> XYLocation(index, i)
            Axis.COLUMN -> XYLocation(i, index)
        }
        val riders = ArrayList<Pair<Piece, XYLocation>>(lineLength)
        for (i in 0 until lineLength) {
            squareAt(locAt(i)).piece?.let { piece ->
                val wrapped = ((i + delta) % lineLength + lineLength) % lineLength
                riders.add(piece to locAt(wrapped))
            }
        }
        for ((piece, _) in riders) squareAt(piece.position!!).piece = null
        for ((piece, destination) in riders) {
            piece.position = destination
            squareAt(destination).piece = piece
        }
        return true
    }

    /**
     * The Warden's push: like [slideLine], but the Warden injects the held
     * tile [spareIn] at the entry edge and the terrain at the far edge is
     * ejected into the Warden's hand (the returned value, the new spare).
     *
     * Pieces ride and wrap exactly as in [slideLine]; **interior terrain is
     * untouched**, so pre-existing pits keep killing flexibly (a piece that
     * rides onto one still falls). Only the two edge cells exchange terrain
     * with the hand. The piece that wraps from the far edge lands on the
     * entry edge — so injecting a [TileType.PIT] there drops it onto that
     * wrapped piece (resolve the fall afterwards).
     *
     * @param delta +1 means the entry edge is index 0 (push from the low
     *   side, everything shifts toward the high side); -1 mirrors it.
     * @return the ejected terrain (new spare), or `null` if the arguments
     *   are invalid (board untouched).
     */
    fun slideLineWithSpare(axis: Axis, index: Int, delta: Int, spareIn: TileType): TileType? {
        if (delta != 1 && delta != -1) return null
        val lineLength = when (axis) {
            Axis.ROW    -> { if (index !in 0 until rows) return null; columns }
            Axis.COLUMN -> { if (index !in 0 until columns) return null; rows }
        }
        fun locAt(i: Int): XYLocation =
            if (axis == Axis.ROW) XYLocation(index, i) else XYLocation(i, index)

        val entry = if (delta > 0) 0 else lineLength - 1
        val far   = if (delta > 0) lineLength - 1 else 0

        val ejected = terrainAt(locAt(far))
        forceTerrain(locAt(far), TileType.FLOOR)   // far tile leaves to hand
        slideLine(axis, index, delta)              // pieces & crates ride + wrap
        forceTerrain(locAt(entry), spareIn)        // held tile drops at the entry edge
        return ejected
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
