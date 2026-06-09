package com.tilewarden.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Per-event timings (ms) — the cadence of the replay. */
private const val MOVE_MS    = 400L
private const val ATTACK_MS  = 220L
private const val RESOLVE_MS = 220L
private const val BLOCK_MS   = 280L
private const val DAMAGE_MS  = 300L
private const val DEATH_MS   = 450L
private const val MISC_MS    =  60L

/** Visual-effect lifetimes (ms). */
internal const val BUBBLE_LIFETIME_MS = 700
internal const val ATTACK_FLASH_MS    = 220L
internal const val DEATH_FADE_MS      = DEATH_MS.toInt()

/** A floating "-N" damage label hovering over the wounded piece. */
@Immutable
data class DamageBubble(
    val id: Long,
    val row: Int,
    val column: Int,
    val amount: Int,
)

/**
 * Compose state holder that drives a match AND replays its events
 * one-by-one so the UI sees the action unfold piece-by-piece.
 *
 * In addition to the [pieces] list, the session exposes three short-lived
 * effect collections for the [BoardCanvas] to render on top of the board:
 *
 * - [damageBubbles] — floating "-N" labels above wounded pieces.
 * - [attackingPieces] — names currently mid-attack (red border flash).
 * - [dyingPieces]    — names that took the killing blow but are still
 *   visible while they fade out. They leave [pieces] after [DEATH_MS].
 */
class GameSession(
    private val seed: Long,
    private val numHeroes: Int = 3,
    private val numMonsters: Int = 4,
    val boardRows: Int = 7,
    val boardColumns: Int = 10,
    val totalRounds: Int = 20,
) {
    var round: Int by mutableStateOf(1)
        private set

    var isOver: Boolean by mutableStateOf(false)
        private set

    var winner: Side? by mutableStateOf(null)
        private set

    var isAnimating: Boolean by mutableStateOf(false)
        private set

    val pieces:           SnapshotStateList<PieceRender>  = mutableStateListOf()
    val damageBubbles:    SnapshotStateList<DamageBubble> = mutableStateListOf()
    val attackingPieces:  SnapshotStateList<String>       = mutableStateListOf()
    val dyingPieces:      SnapshotStateList<String>       = mutableStateListOf()

    val log: SnapshotStateList<String> = mutableStateListOf()

    val heroesAlive: Int get() = pieces.count { it.isHero && it.name !in dyingPieces }
    val monstersAlive: Int get() = pieces.count { !it.isHero && it.name !in dyingPieces }

    private val buffer: ArrayDeque<GameEvent> = ArrayDeque()
    private val observer = object : GameObserver {
        override fun onEvent(event: GameEvent) { buffer.addLast(event) }
    }
    private var game: Game = buildFreshGame()
    private var nextBubbleId: Long = 0

    init {
        buffer.clear()
        rebuildPiecesFromGame()
        log.add("=== GAME START ===")
    }

    suspend fun nextRound() {
        if (isOver || isAnimating) return
        isAnimating = true
        try {
            buffer.clear()
            if (!GameEngine.isOver(game)) {
                GameEngine.resolveRound(game)
                GameEngine.advanceRound(game)
            }
            if (GameEngine.isOver(game)) {
                observer.onEvent(GameEvent.GameEnded(GameEngine.winner(game)))
            }
            replayBuffered()
            round = game.currentRound
        } finally {
            isAnimating = false
        }
    }

    fun reset() {
        buffer.clear()
        log.clear()
        isOver = false
        winner = null
        damageBubbles.clear()
        attackingPieces.clear()
        dyingPieces.clear()
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

    private suspend fun replayBuffered() = coroutineScope {
        while (buffer.isNotEmpty()) {
            val event = buffer.removeFirst()
            describe(event).takeIf { it.isNotEmpty() }?.let { log.add(it) }
            applyEventToPieces(event)
            delay(durationFor(event))
        }
        // The enclosing coroutineScope waits for fire-and-forget effect jobs
        // (bubble cleanup, attack flash decay, dying piece removal) before
        // returning — so a fresh nextRound never starts on top of stale fx.
    }

    private fun CoroutineScope.applyEventToPieces(event: GameEvent) {
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
            is GameEvent.Attacked -> {
                val name = event.attacker.name
                if (name !in attackingPieces) attackingPieces.add(name)
                launch {
                    delay(ATTACK_FLASH_MS)
                    attackingPieces.remove(name)
                }
            }
            is GameEvent.Damaged -> {
                val idx = pieces.indexOfFirst { it.name == event.character.name }
                if (idx >= 0) {
                    val cur = pieces[idx]
                    pieces[idx] = cur.copy(
                        body = (cur.body - event.wounds).coerceAtLeast(0),
                    )
                    val bubble = DamageBubble(
                        id = ++nextBubbleId,
                        row = cur.row,
                        column = cur.column,
                        amount = event.wounds,
                    )
                    damageBubbles.add(bubble)
                    launch {
                        delay(BUBBLE_LIFETIME_MS.toLong())
                        damageBubbles.remove(bubble)
                    }
                }
            }
            is GameEvent.Died -> {
                val name = event.character.name
                if (name !in dyingPieces) dyingPieces.add(name)
                launch {
                    // Wait for the fade animation to play out, then remove
                    // the piece for good. The dyingPieces flag stays on the
                    // way out so the alpha animation continues to target 0.
                    delay(DEATH_MS)
                    pieces.removeAll { it.name == name }
                    dyingPieces.remove(name)
                }
            }
            is GameEvent.GameEnded -> {
                isOver = true
                winner = event.winner
            }
            else -> { /* RoundStarted, RoundEnded, AttackResolved, AttackBlocked, GameStarted, PieceBlocked */ }
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
