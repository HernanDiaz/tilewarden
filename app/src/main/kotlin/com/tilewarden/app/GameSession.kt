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
import com.tilewarden.core.Hero
import com.tilewarden.core.Monster
import com.tilewarden.core.Side
import com.tilewarden.core.XYLocation
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

/** Pause after a manual hero step so the slide animation has time to play. */
private const val MANUAL_STEP_DELAY_MS = 350L

/** A floating "-N" damage label hovering over the wounded piece. */
@Immutable
data class DamageBubble(
    val id: Long,
    val row: Int,
    val column: Int,
    val amount: Int,
)

/**
 * Compose state holder that drives a match.
 *
 * Two ways the game advances:
 * - **Auto / Next round**: [nextRound] runs a full engine round and
 *   replays its [GameEvent]s with delays. Heroes the player already
 *   touched this round are skipped — the AI only animates the others.
 * - **Manual interaction**: [selectHero] / [manualMove] / [manualAttack]
 *   let the player drive their heroes directly, one step at a time.
 *   Each manual action marks the hero as "acted this round", so the
 *   following [nextRound] won't drive them automatically.
 *
 * Manual moves bypass the buffered-replay system: the visible state is
 * mutated immediately and Compose animates the difference. The engine
 * also publishes events to the observer so the log keeps track.
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

    /** Currently selected hero — null when no one is picked. */
    var selectedHero: String? by mutableStateOf(null)
        private set

    val pieces:           SnapshotStateList<PieceRender>  = mutableStateListOf()
    val damageBubbles:    SnapshotStateList<DamageBubble> = mutableStateListOf()
    val attackingPieces:  SnapshotStateList<String>       = mutableStateListOf()
    val dyingPieces:      SnapshotStateList<String>       = mutableStateListOf()

    /** Heroes who took a manual action this round; AI skips them in nextRound. */
    val actedThisRound: SnapshotStateList<String> = mutableStateListOf()

    val log: SnapshotStateList<String> = mutableStateListOf()

    /** Sum of initialBody across all heroes at game start. Constant per game. */
    var initialHeroBody: Int by mutableStateOf(0)
        private set

    /** Sum of initialBody across all monsters at game start. Constant per game. */
    var initialMonsterBody: Int by mutableStateOf(0)
        private set

    val heroesAlive: Int get() = pieces.count { it.isHero && it.name !in dyingPieces }
    val monstersAlive: Int get() = pieces.count { !it.isHero && it.name !in dyingPieces }

    private val buffer: ArrayDeque<GameEvent> = ArrayDeque()
    private val observer = object : GameObserver {
        override fun onEvent(event: GameEvent) { buffer.addLast(event) }
    }
    private var game: Game = buildFreshGame()
    private var nextBubbleId: Long = 0

    /** Per-hero counter of remaining steps this round (depleted by manual moves). */
    private val movesLeft = HashMap<String, Int>()

    init {
        buffer.clear()
        rebuildPiecesFromGame()
        resetMovesLeft()
        log.add("=== GAME START ===")
    }

    // ---- AI round ----

    suspend fun nextRound() {
        if (isOver || isAnimating) return
        selectedHero = null
        isAnimating = true
        try {
            buffer.clear()
            if (!GameEngine.isOver(game)) {
                GameEngine.resolveRound(game, skipNames = actedThisRound.toSet())
                GameEngine.advanceRound(game)
            }
            if (GameEngine.isOver(game)) {
                observer.onEvent(GameEvent.GameEnded(GameEngine.winner(game)))
            }
            replayBuffered()
            round = game.currentRound
            actedThisRound.clear()
            resetMovesLeft()
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
        actedThisRound.clear()
        selectedHero = null
        game = buildFreshGame()
        buffer.clear()
        rebuildPiecesFromGame()
        resetMovesLeft()
        round = game.currentRound
        log.add("=== GAME START ===")
    }

    // ---- Manual interaction ----

    /** Toggle selection of a hero. No-op while animating or for off-limits heroes. */
    fun selectHero(name: String?) {
        if (isAnimating || isOver) return
        if (name == null) { selectedHero = null; return }
        val piece = pieces.find { it.name == name } ?: return
        if (!piece.isHero) return
        if (name in actedThisRound) return
        if ((movesLeft[name] ?: 0) <= 0) return
        selectedHero = if (selectedHero == name) null else name
    }

    /** Empty squares adjacent to the selected hero. Empty set if nothing selected. */
    fun validMoveTargets(): Set<XYLocation> {
        val name = selectedHero ?: return emptySet()
        val c = game.characters.find { it.name == name } ?: return emptySet()
        return GameEngine.validPositions(game, c).toSet()
    }

    /** Adjacent enemy names targetable by the selected hero. */
    fun validAttackTargets(): Set<String> {
        val name = selectedHero ?: return emptySet()
        val c = game.characters.find { it.name == name } ?: return emptySet()
        return GameEngine.validTargets(game, c).map { it.name }.toSet()
    }

    /**
     * Manually move the selected hero one square to [target].
     * @return `true` if the move happened.
     */
    fun manualMove(target: XYLocation): Boolean {
        if (isAnimating || isOver) return false
        val name = selectedHero ?: return false
        if ((movesLeft[name] ?: 0) <= 0) return false
        val hero = game.characters.find { it.name == name } ?: return false
        val from = hero.position ?: return false
        if (!game.board.isFree(target)) return false
        if (!GameEngine.atRange(from, target)) return false  // 4-neighbour only

        // Mutate game state
        if (!game.board.movePiece(hero, target)) return false

        // Mirror to visible state immediately so Compose animates the slide
        val idx = pieces.indexOfFirst { it.name == name }
        if (idx >= 0) {
            pieces[idx] = pieces[idx].copy(row = target.x, column = target.y)
        }

        // Record action + spend movement
        if (name !in actedThisRound) actedThisRound.add(name)
        movesLeft[name] = (movesLeft[name] ?: 0) - 1
        log.add("$name${from} -> ${target}")

        // Auto-deselect when out of moves
        if ((movesLeft[name] ?: 0) <= 0) selectedHero = null
        return true
    }

    /**
     * Manually have the selected hero attack [targetName].
     * The attack uses [com.tilewarden.core.Character.combat] so all the
     * dice rolls, damage, stats and events flow through the same path
     * as an AI-driven attack. The hero's remaining moves are consumed
     * (matches the AI semantics).
     */
    suspend fun manualAttack(targetName: String) {
        if (isAnimating || isOver) return
        val name = selectedHero ?: return
        val attacker = game.characters.find { it.name == name } ?: return
        val defender = game.characters.find { it.name == targetName } ?: return
        if (!attacker.isAlive || !defender.isAlive) return
        if (!attacker.isEnemy(defender)) return
        val ap = attacker.position; val dp = defender.position
        if (ap == null || dp == null || !GameEngine.atRange(ap, dp)) return

        // Mark and clear UI selection BEFORE running combat so the user
        // gets immediate visual feedback.
        if (name !in actedThisRound) actedThisRound.add(name)
        movesLeft[name] = 0
        selectedHero = null

        // Combat publishes events; replay them.
        isAnimating = true
        try {
            buffer.clear()
            attacker.combat(defender, game)
            replayBuffered()
        } finally {
            isAnimating = false
        }
    }

    // ---- Internals ----

    private fun resetMovesLeft() {
        movesLeft.clear()
        for (c in game.characters) {
            if (c is Hero && c.isAlive) movesLeft[c.name] = c.moves
        }
    }

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
        var heroSum = 0
        var monsterSum = 0
        for (c in game.characters) {
            renderOf(c)?.let { pieces.add(it) }
            when (c) {
                is Hero    -> heroSum    += c.initialBody
                is Monster -> monsterSum += c.initialBody
                else       -> { /* neutral */ }
            }
        }
        initialHeroBody    = heroSum
        initialMonsterBody = monsterSum
    }

    private suspend fun replayBuffered() = coroutineScope {
        while (buffer.isNotEmpty()) {
            val event = buffer.removeFirst()
            describe(event).takeIf { it.isNotEmpty() }?.let { log.add(it) }
            applyEventToPieces(event)
            delay(durationFor(event))
        }
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
                    delay(DEATH_MS)
                    pieces.removeAll { it.name == name }
                    dyingPieces.remove(name)
                }
            }
            is GameEvent.GameEnded -> {
                isOver = true
                winner = event.winner
            }
            else -> { }
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

