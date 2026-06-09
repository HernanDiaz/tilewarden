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
     * Empty squares immediately N/S/E/W of [character]'s current position.
     * Returns empty if the character is off-board.
     */
    fun validPositions(game: Game, character: Character): List<XYLocation> {
        val current = character.position ?: return emptyList()
        // South / West / East / North — original iteration order preserved.
        return listOf(Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.NORTH)
            .map { current + it }
            .filter { game.board.isFree(it) }
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

    /** A character is blocked when no neighbour square is free. */
    fun isBlocked(game: Game, character: Character): Boolean {
        val pos = character.position ?: return true
        return Direction.entries.none { game.board.isFree(pos + it) }
    }

    /** A random cardinal direction. */
    fun randomDirection(): Direction =
        Direction.entries[Dice.roll(Direction.entries.size) - 1]

    /**
     * Place every character of [game] on a randomly chosen empty square.
     * Loops until each finds a valid spot, so the caller must ensure the
     * board has enough free squares.
     */
    fun placeCharactersRandomly(game: Game) {
        val rows = game.board.rows
        val cols = game.board.columns
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
