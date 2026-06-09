package com.tilewarden.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tilewarden.core.Dice
import com.tilewarden.core.Game
import com.tilewarden.core.GameEngine
import com.tilewarden.core.GameEvent
import com.tilewarden.core.GameObserver
import com.tilewarden.core.Hero
import com.tilewarden.core.Monster
import com.tilewarden.core.Side

/**
 * Compose state holder that drives a single match.
 *
 * Owns the underlying [Game], advances it one round at a time on demand,
 * and exposes everything the UI cares about as Compose-observable state
 * (`mutableStateOf`, `mutableStateListOf`).
 *
 * Lifetime: created via [rememberGameSession] inside a Composable so it
 * survives recomposition but is dropped when the parent leaves composition.
 */
class GameSession(
    private val seed: Long,
    private val numHeroes: Int = 3,
    private val numMonsters: Int = 4,
    private val rows: Int = 7,
    private val columns: Int = 10,
    private val totalRoundsCfg: Int = 20,
) {
    /** Current round number (1-based). Observable. */
    var round: Int by mutableStateOf(1)
        private set

    /** `true` after a [GameEvent.GameEnded] has been observed. */
    var isOver: Boolean by mutableStateOf(false)
        private set

    /** Winner side, populated once the game ends. */
    var winner: Side? by mutableStateOf(null)
        private set

    /** Single state-version counter to force recomposition on engine ticks. */
    private var tick: Int by mutableStateOf(0)

    /** Event log lines, mutated in place via [mutableStateListOf]. */
    val log = mutableStateListOf<String>()

    private val observer = object : GameObserver {
        override fun onEvent(event: GameEvent) {
            log += describe(event)
            if (event is GameEvent.GameEnded) {
                isOver = true
                winner = event.winner
            }
        }
    }

    private var game: Game = buildFreshGame()

    /** ASCII rendering of the board. Re-read each recomposition. */
    val boardText: String
        get() {
            tick  // touch to subscribe to invalidations
            return game.board.toString()
        }

    /** Multi-line listing of all live characters with their current stats. */
    val charactersText: String
        get() {
            tick
            return game.characters.joinToString("\n") { it.toString() }
        }

    /** Statistics tally line. */
    val statisticsText: String
        get() {
            tick
            return game.statistics.toString()
        }

    val totalRounds: Int get() = game.totalRounds

    /** Counts of live characters by side. */
    val heroesAlive: Int
        get() {
            tick
            return game.characters.count { it is Hero }
        }

    val monstersAlive: Int
        get() {
            tick
            return game.characters.count { it is Monster }
        }

    /** Step the game one round forward, no-op if [isOver]. */
    fun nextRound() {
        if (isOver) return
        if (GameEngine.isOver(game)) {
            finalize()
            return
        }
        GameEngine.resolveRound(game)
        GameEngine.advanceRound(game)
        round = game.currentRound
        tick++
        if (GameEngine.isOver(game)) finalize()
    }

    /** Throw away the current game and rebuild a fresh one with the same seed. */
    fun reset() {
        log.clear()
        isOver = false
        winner = null
        game = buildFreshGame()
        round = game.currentRound
        tick++
    }

    private fun finalize() {
        observer.onEvent(GameEvent.GameEnded(GameEngine.winner(game)))
    }

    private fun buildFreshGame(): Game {
        Dice.setSeed(seed)
        val g = Game(
            numHeroes = numHeroes,
            numMonsters = numMonsters,
            boardRows = rows,
            boardColumns = columns,
            totalRounds = totalRoundsCfg,
            observer = observer,
        )
        GameEngine.placeCharactersRandomly(g)
        observer.onEvent(GameEvent.GameStarted(g))
        return g
    }

    private fun describe(event: GameEvent): String = when (event) {
        is GameEvent.GameStarted    -> "=== GAME START ==="
        is GameEvent.RoundStarted   -> "--- Round ${event.round} ---"
        is GameEvent.RoundEnded     -> ""
        is GameEvent.PieceMoved     ->
            "${event.character.name}${event.from} -> ${event.to}"
        is GameEvent.PieceBlocked   -> "${event.character.name} is BLOCKED"
        is GameEvent.Attacked       ->
            "${event.attacker.name}${event.attacker.position} " +
                "attacks ${event.target.name}${event.target.position}"
        is GameEvent.AttackResolved ->
            "  ${event.attacker.name} scores ${event.hits} hit(s) on ${event.target.name}"
        is GameEvent.AttackBlocked  -> "  ${event.defender.name} blocks the attack"
        is GameEvent.Damaged        -> "  ${event.character.name} takes ${event.wounds} wound(s)"
        is GameEvent.Died           -> "  ${event.character.name} DIES"
        is GameEvent.GameEnded      -> "=== GAME END — winner: ${event.winner} ==="
    }.also { /* keep when() exhaustive */ }
}

/** Composable factory: one [GameSession] per (seed) per parent composition. */
@Composable
fun rememberGameSession(seed: Long = 2026L): GameSession =
    remember(seed) { GameSession(seed) }
