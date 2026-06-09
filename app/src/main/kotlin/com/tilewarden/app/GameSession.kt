package com.tilewarden.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.tilewarden.core.Dice
import com.tilewarden.core.Game
import com.tilewarden.core.GameEngine
import com.tilewarden.core.GameEvent
import com.tilewarden.core.GameObserver
import com.tilewarden.core.Side
import kotlinx.coroutines.delay

/** Per-event animation/visibility timings used during replay (in ms). */
private const val MOVE_MS    = 400L
private const val ATTACK_MS  = 220L
private const val RESOLVE_MS = 220L
private const val BLOCK_MS   = 280L
private const val DAMAGE_MS  = 300L
private const val DEATH_MS   = 450L
private const val MISC_MS    =  60L

/**
 * Compose state holder that drives a match AND replays its events
 * one-by-one so the UI sees the action unfold piece-by-piece.
 *
 * Flow per call to [nextRound]:
 * 1. Run one full engine round, capturing every emitted [GameEvent] in
 *    an internal buffer (the observer doesn't update the UI directly).
 * 2. Replay the buffer with delays. Each event:
 *      - appends a line to [log] if it has a description,
 *      - mutates exactly the affected [PieceRender] in [pieces]
 *        (move, lose body, or disappear on death),
 *      - waits a duration matched to the visual effect.
 * 3. While replaying, [isAnimating] is true and the controls are disabled.
 *
 * The setup events emitted while [GameEngine.placeCharactersRandomly]
 * runs are intentionally discarded — the initial board is shown directly
 * from a snapshot of [Game.characters].
 */
class GameSession(
    private val seed: Long,
    private val numHeroes: Int = 3,
    private val numMonsters: Int = 4,
    val boardRows: Int = 7,
    val boardColumns: Int = 10,
    val totalRounds: Int = 20,
) {
    /** Current round number (1-based). */
    var round: Int by mutableStateOf(1)
        private set

    /** `true` once the game has ended. */
    var isOver: Boolean by mutableStateOf(false)
        private set

    /** Winner side once the game ends. */
    var winner: Side? by mutableStateOf(null)
        private set

    /** True while a round's events are being replayed; controls should disable. */
    var isAnimating: Boolean by mutableStateOf(false)
        private set

    /** Live, observable list of characters as the UI sees them. */
    val pieces: SnapshotStateList<PieceRender> = mutableStateListOf()

    /** Append-only event log (only the lines worth showing). */
    val log: SnapshotStateList<String> = mutableStateListOf()

    val heroesAlive: Int get() = pieces.count { it.isHero }
    val monstersAlive: Int get() = pieces.count { !it.isHero }

    private val buffer: ArrayDeque<GameEvent> = ArrayDeque()

    private val observer = object : GameObserver {
        override fun onEvent(event: GameEvent) { buffer.addLast(event) }
    }

    private var game: Game = buildFreshGame()

    init {
        // The placement above pushed nothing to the buffer (movePiece doesn't
        // emit events), but clear defensively and show the starting state.
        buffer.clear()
        rebuildPiecesFromGame()
        log.add("=== GAME START ===")
    }

    /** Advance one round and replay its events with delays. */
    suspend fun nextRound() {
        if (isOver || isAnimating) return
        isAnimating = true
        try {
            buffer.clear()
            if (!GameEngine.isOver(game)) {
                GameEngine.resolveRound(game)
                GameEngine.advanceRound(game)
            }
            // The engine's runGame would publish GameEnded; we drive the loop
            // manually, so we have to emit it ourselves.
            if (GameEngine.isOver(game)) {
                observer.onEvent(GameEvent.GameEnded(GameEngine.winner(game)))
            }
            replayBuffered()
            round = game.currentRound
        } finally {
            isAnimating = false
        }
    }

    /** Tear down the current game and start a fresh one with the same seed. */
    fun reset() {
        buffer.clear()
        log.clear()
        isOver = false
        winner = null
        game = buildFreshGame()
        buffer.clear()
        rebuildPiecesFromGame()
        round = game.currentRound
        log.add("=== GAME START ===")
    }

    // ----- Internals -----

    private fun buildFreshGame(): Game {
        Dice.setSeed(seed)
        val g = Game(
            numHeroes = numHeroes,
            numMonsters = numMonsters,
            boardRows = boardRows,
            boardColumns = boardColumns,
            totalRounds = totalRounds,
            observer = observer,
        )
        GameEngine.placeCharactersRandomly(g)
        return g
    }

    private fun rebuildPiecesFromGame() {
        pieces.clear()
        for (c in game.characters) {
            renderOf(c)?.let { pieces.add(it) }
        }
    }

    private suspend fun replayBuffered() {
        while (buffer.isNotEmpty()) {
            val event = buffer.removeFirst()
            describe(event).takeIf { it.isNotEmpty() }?.let { log.add(it) }
            applyEventToPieces(event)
            delay(durationFor(event))
        }
    }

    /** Mutate the [pieces] list in place to reflect a single event. */
    private fun applyEventToPieces(event: GameEvent) {
        when (event) {
            is GameEvent.PieceMoved -> {
                val idx = pieces.indexOfFirst { it.name == event.character.name }
                if (idx >= 0) {
                    pieces[idx] = pieces[idx].copy(
                        row = event.to.x,
                        column = event.to.y,
                    )
                }
            }
            is GameEvent.Damaged -> {
                val idx = pieces.indexOfFirst { it.name == event.character.name }
                if (idx >= 0) {
                    val cur = pieces[idx]
                    pieces[idx] = cur.copy(
                        body = (cur.body - event.wounds).coerceAtLeast(0),
                    )
                }
            }
            is GameEvent.Died -> {
                pieces.removeAll { it.name == event.character.name }
            }
            is GameEvent.GameEnded -> {
                isOver = true
                winner = event.winner
            }
            else -> { /* nothing visual */ }
        }
    }

    private fun durationFor(event: GameEvent): Long = when (event) {
        is GameEvent.PieceMoved     -> MOVE_MS
        is GameEvent.Attacked       -> ATTACK_MS
        is GameEvent.AttackResolved -> RESOLVE_MS
        is GameEvent.AttackBlocked  -> BLOCK_MS
        is GameEvent.Damaged        -> DAMAGE_MS
        is GameEvent.Died           -> DEATH_MS
        else                        -> MISC_MS
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
    }
}

/** Composable factory: one [GameSession] per (seed) per parent composition. */
@Composable
fun rememberGameSession(seed: Long = 2026L): GameSession =
    remember(seed) { GameSession(seed) }
