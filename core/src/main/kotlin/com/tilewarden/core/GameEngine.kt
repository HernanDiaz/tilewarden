package com.tilewarden.core

import kotlin.math.abs

/**
 * Static rules of the game: helpers that read state and the top-level
 * loop that runs a [Game] from start to finish.
 *
 * Modelled as an `object` instead of free functions to keep all rule
 * queries grouped under a single name (the equivalent of the original
 * `Jeroquest` class's static methods).
 */
object GameEngine {

    /**
     * Walkable squares immediately N/S/E/W of [character]'s current
     * position (in bounds, empty, and not an open pit — nothing steps
     * into a pit on purpose). Returns empty if the character is off-board.
     */
    fun validPositions(game: Game, character: Character): List<XYLocation> {
        val current = character.position ?: return emptyList()
        // South / West / East / North — original iteration order preserved.
        return listOf(Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.NORTH)
            .map { current + it }
            .filter { game.board.isWalkable(it) }
    }

    /**
     * Live, adjacent enemies of [character].
     */
    fun validTargets(game: Game, character: Character): List<Character> {
        val pos = character.position ?: return emptyList()
        return game.characters.filter { other ->
            other.isAlive &&
                character.isEnemy(other) &&
                other.position?.let { atRange(pos, it) } == true
        }
    }

    /** Two locations are at melee range (orthogonally adjacent, no diagonals). */
    fun atRange(a: XYLocation, b: XYLocation): Boolean {
        val dx = abs(a.x - b.x)
        val dy = abs(a.y - b.y)
        return (dx == 1 && dy == 0) || (dx == 0 && dy == 1)
    }

    /** A character is blocked when no neighbour square is walkable. */
    fun isBlocked(game: Game, character: Character): Boolean {
        val pos = character.position ?: return true
        return Direction.entries.none { game.board.isWalkable(pos + it) }
    }

    /** A random cardinal direction. */
    fun randomDirection(): Direction =
        Direction.entries[Dice.roll(Direction.entries.size) - 1]

    /**
     * Place the dungeon furniture and every character of [game] on
     * randomly chosen squares: crates first, then pits (on squares that
     * are still empty), then the characters (who can never start on a
     * pit — [Board.movePiece] refuses pit squares). Loops until each
     * finds a valid spot, so the caller must ensure the board has enough
     * free squares.
     */
    fun placeCharactersRandomly(game: Game) {
        val rows = game.board.rows
        val cols = game.board.columns
        for (o in game.obstacles) {
            do {
                val x = Dice.roll(rows) - 1
                val y = Dice.roll(cols) - 1
            } while (!game.board.movePiece(o, XYLocation(x, y)))
        }
        repeat(game.numPits) {
            do {
                val x = Dice.roll(rows) - 1
                val y = Dice.roll(cols) - 1
            } while (!game.board.addPit(XYLocation(x, y)))
        }
        for (c in game.characters) {
            do {
                val x = Dice.roll(rows) - 1
                val y = Dice.roll(cols) - 1
            } while (!game.board.movePiece(c, XYLocation(x, y)))
        }
    }

    /**
     * Run [game] from round 1 to end. The end condition is either
     * exhausting [Game.totalRounds] or having no opponents left.
     */
    fun runGame(game: Game) {
        game.notify(GameEvent.GameStarted(game))
        while (notEndOfGame(game)) {
            resolveRound(game)
            advanceRound(game)
        }
        game.notify(GameEvent.GameEnded(winner(game)))
    }

    /**
     * Iterate every live character once, in current order. Snapshot the
     * list first because characters can be removed mid-round when they die.
     *
     * @param skipNames characters whose AI turn should be skipped this round
     *   (because the player already acted with them manually). Empty by
     *   default — fully autonomous behaviour, identical to the original.
     */
    fun resolveRound(game: Game, skipNames: Set<String> = emptySet()) {
        game.notify(GameEvent.RoundStarted(game.currentRound))

        // Monster Warden Action: once per round, and only when it kills.
        // Resolved before individual turns so a freshly-opened gap can't
        // be walked out of.
        if (game.characters.any { it is Monster && it.isAlive }) {
            bestMonsterSlide(game)?.let { s ->
                game.board.slideLine(s.axis, s.index, s.delta)
                game.notify(GameEvent.TilesSlid(s.axis, s.index, s.delta))
                resolvePitFalls(game)
            }
        }

        val turnOrder = game.characters.toList()
        for (c in turnOrder) {
            if (!opponentsLeft(game)) break
            if (!c.isAlive) continue
            if (c.name in skipNames) continue
            c.resolveTurn(game)
        }
        game.notify(GameEvent.RoundEnded(game.currentRound))
    }

    /** Move [game] to the next round. Useful when driving the loop manually. */
    fun advanceRound(game: Game) {
        game.currentRound++
    }

    /** A candidate Warden slide with its evaluated benefit for monsters. */
    data class SlideChoice(val axis: Axis, val index: Int, val delta: Int, val score: Int)

    /**
     * Monster-side Warden AI: evaluate all 2*(rows+columns) possible
     * slides analytically (no board mutation) and return the highest
     * scoring one, or null when nothing is worth doing.
     *
     * Scoring per piece that would land on an open pit:
     * - hero falls:    +10  (the whole point)
     * - monster falls: -12  (never worth trading one-for-one)
     * - crate falls:    -1  (plugs a pit — losing a kill tool, mildly bad)
     *
     * Only choices with score > 0 are considered; ties go to the first
     * found (deterministic given the same board state).
     */
    fun bestMonsterSlide(game: Game): SlideChoice? {
        if (game.board.pits.isEmpty()) return null
        var best: SlideChoice? = null
        for (axis in Axis.entries) {
            val lineCount  = if (axis == Axis.ROW) game.board.rows    else game.board.columns
            val lineLength = if (axis == Axis.ROW) game.board.columns else game.board.rows
            for (index in 0 until lineCount) {
                for (delta in intArrayOf(1, -1)) {
                    var score = 0
                    fun destOf(pos: XYLocation): XYLocation? {
                        val onLine = if (axis == Axis.ROW) pos.x == index else pos.y == index
                        if (!onLine) return null
                        val coord = if (axis == Axis.ROW) pos.y else pos.x
                        val wrapped = ((coord + delta) % lineLength + lineLength) % lineLength
                        return if (axis == Axis.ROW) XYLocation(index, wrapped)
                               else                  XYLocation(wrapped, index)
                    }
                    for (c in game.characters) {
                        val dest = c.position?.let(::destOf) ?: continue
                        if (game.board.isPit(dest)) {
                            score += if (c is Hero) 10 else -12
                        }
                    }
                    for (o in game.obstacles) {
                        val dest = o.position?.let(::destOf) ?: continue
                        if (game.board.isPit(dest)) score -= 1
                    }
                    if (score > 0 && score > (best?.score ?: 0)) {
                        best = SlideChoice(axis, index, delta, score)
                    }
                }
            }
        }
        return best
    }

    /**
     * Resolve everything that ended up on an open pit after a tile slide:
     * - A character falls in and dies ([GameEvent.FellInPit] is published;
     *   the character is removed from board and game).
     * - A crate falls in and PLUGS the pit — both disappear.
     *
     * @return `true` if anything fell.
     */
    fun resolvePitFalls(game: Game): Boolean {
        var anything = false
        for (o in game.obstacles.toList()) {
            val pos = o.position ?: continue
            if (game.board.isPit(pos)) {
                game.board.removePiece(o)
                game.removeObstacle(o)
                game.board.removePit(pos)
                anything = true
            }
        }
        for (c in game.characters.toList()) {
            val pos = c.position ?: continue
            if (game.board.isPit(pos)) {
                game.notify(GameEvent.FellInPit(c))
                c.body = 0
                game.board.removePiece(c)
                game.removeCharacter(c)
                anything = true
            }
        }
        return anything
    }

    /**
     * `true` once the game cannot continue: round budget exhausted, or one
     * side wiped out. The mirror of the internal loop condition.
     */
    fun isOver(game: Game): Boolean = !notEndOfGame(game)

    private fun notEndOfGame(game: Game): Boolean =
        game.currentRound <= game.totalRounds && opponentsLeft(game)

    /** At least one live hero AND at least one live monster. */
    fun opponentsLeft(game: Game): Boolean {
        var heroes = false
        var monsters = false
        for (c in game.characters) {
            if (!c.isAlive) continue
            when (c) {
                is Hero    -> heroes = true
                is Monster -> monsters = true
                else -> {} // neutral characters could exist later
            }
            if (heroes && monsters) return true
        }
        return false
    }

    /** Side with the most total body points left (ties = DRAW). */
    fun winner(game: Game): Side {
        var heroBody = 0
        var monsterBody = 0
        for (c in game.characters) {
            when (c) {
                is Hero    -> heroBody += c.body
                is Monster -> monsterBody += c.body
                else -> {}
            }
        }
        return when {
            heroBody > monsterBody -> Side.HEROES
            monsterBody > heroBody -> Side.MONSTERS
            else -> Side.DRAW
        }
    }
}
