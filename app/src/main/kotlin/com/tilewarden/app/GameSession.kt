package com.tilewarden.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.tilewarden.core.Axis
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

/** A crate on the board — blocks movement, rides tile slides. */
@Immutable
data class ObstacleRender(
    val name: String,
    val row: Int,
    val column: Int,
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
    private val audio: AudioEngine? = null,
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

    /** Crates on the board. They ride slides; one can plug a pit. */
    val obstacles: SnapshotStateList<ObstacleRender> = mutableStateListOf()

    /** Open pits. Static — pits never ride a slide. */
    val pits: SnapshotStateList<XYLocation> = mutableStateListOf()

    /** Heroes the player has acted with at least once this round. The AI
     * skips them on Next round so it doesn't move them again. */
    val touchedThisRound: SnapshotStateList<String> = mutableStateListOf()

    /** Heroes who have FULLY completed their turn this round (ran out of
     * moves or used their attack). The UI fades these out. */
    val actedThisRound: SnapshotStateList<String> = mutableStateListOf()

    /** Whether the once-per-round Warden Action (tile slide) is spent. */
    var wardenUsedThisRound: Boolean by mutableStateOf(false)
        private set

    /** Initial snapshot of every character that started the game.
     * Used by the end-of-game summary to compute the MVP even after death. */
    val initialPieces: SnapshotStateList<PieceRender> = mutableStateListOf()

    /** Per-character total attack count across the whole game. */
    val attacksByName: SnapshotStateMap<String, Int> = mutableStateMapOf()

    /** Per-character total damage dealt across the whole game. */
    val damageByName: SnapshotStateMap<String, Int> = mutableStateMapOf()

    /** Most recent attacker — used to attribute the next Damaged event. */
    private var lastAttackerName: String? = null

    /** Read-only view of the engine's running tally for the current match. */
    val statistics: com.tilewarden.core.Statistics
        get() = game.statistics

    /**
     * Per-character facing flag. Sprites are drawn looking right by default;
     * when a piece's last horizontal move was westwards, its entry is true
     * and the renderer flips the sprite horizontally.
     */
    val facingLeft: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

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
        refreshFurniture()
        resetMovesLeft()
        log.add("=== GAME START ===")
    }

    // ---- AI round ----

    suspend fun nextRound() {
        android.util.Log.d("Tilewarden", "nextRound() called. isOver=$isOver isAnimating=$isAnimating round=${game.currentRound} touched=${touchedThisRound.toList()}")
        if (isOver || isAnimating) {
            android.util.Log.d("Tilewarden", "nextRound() early return")
            return
        }
        selectedHero = null
        isAnimating = true
        try {
            buffer.clear()
            if (!GameEngine.isOver(game)) {
                GameEngine.resolveRound(game, skipNames = touchedThisRound.toSet())
                GameEngine.advanceRound(game)
            }
            android.util.Log.d("Tilewarden", "after resolveRound, buffer size=${buffer.size}")
            if (GameEngine.isOver(game)) {
                observer.onEvent(GameEvent.GameEnded(GameEngine.winner(game)))
            }
            replayBuffered()
            round = game.currentRound
            touchedThisRound.clear()
            actedThisRound.clear()
            wardenUsedThisRound = false
            resetMovesLeft()
            android.util.Log.d("Tilewarden", "nextRound() finished. new round=$round")
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
        touchedThisRound.clear()
        actedThisRound.clear()
        wardenUsedThisRound = false
        facingLeft.clear()
        attacksByName.clear()
        damageByName.clear()
        lastAttackerName = null
        selectedHero = null
        game = buildFreshGame()
        buffer.clear()
        rebuildPiecesFromGame()
        refreshFurniture()
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
        if (!game.board.isWalkable(target)) return false  // occupied or pit
        if (!GameEngine.atRange(from, target)) return false  // 4-neighbour only

        // Mutate game state
        if (!game.board.movePiece(hero, target)) return false

        // Mirror to visible state immediately so Compose animates the slide
        val idx = pieces.indexOfFirst { it.name == name }
        if (idx >= 0) {
            pieces[idx] = pieces[idx].copy(row = target.x, column = target.y)
        }
        updateFacing(name, from.y, target.y)

        // Record action + spend movement
        if (name !in touchedThisRound) touchedThisRound.add(name)
        movesLeft[name] = (movesLeft[name] ?: 0) - 1
        log.add("$name${from} -> ${target}")
        audio?.play(SoundId.STEP)

        // Out of moves: fully done, fade out and deselect.
        if ((movesLeft[name] ?: 0) <= 0) {
            if (name !in actedThisRound) actedThisRound.add(name)
            selectedHero = null
        }
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
        // gets immediate visual feedback. Attacks consume the whole turn
        // (matches the AI semantics), so the hero is also fully done.
        if (name !in touchedThisRound) touchedThisRound.add(name)
        if (name !in actedThisRound)   actedThisRound.add(name)
        movesLeft[name] = 0
        selectedHero = null

        // Combat publishes events; replay them.
        isAnimating = true
        try {
            buffer.clear()
            attacker.combat(defender, game)
            replayBuffered()
            // If this attack just ended the game (last enemy down, or both
            // sides wiped), emit GameEnded right away. Otherwise the user
            // would have to tap Next round for nothing to happen except the
            // verdict finally appearing.
            if (GameEngine.isOver(game)) {
                buffer.clear()
                observer.onEvent(GameEvent.GameEnded(GameEngine.winner(game)))
                replayBuffered()
            }
        } finally {
            isAnimating = false
        }
    }

    /**
     * The Warden Action: slide a full row or column of the dungeon one
     * square (wrapping at the edges), carrying every piece on it. Once
     * per round; shares the round with normal hero moves and attacks.
     *
     * Anything pushed onto an open pit falls: characters die
     * ([GameEvent.FellInPit] replays with the usual death effects),
     * crates plug the pit and both disappear.
     *
     * @return `true` if the slide happened.
     */
    suspend fun wardenSlide(axis: Axis, index: Int, delta: Int): Boolean {
        if (isAnimating || isOver || wardenUsedThisRound) return false
        if (!game.board.slideLine(axis, index, delta)) return false
        wardenUsedThisRound = true

        // Mirror every character's new board position into the visible
        // state; Compose animates the slide for us. Crates too.
        for (i in pieces.indices) {
            val c = game.characters.find { it.name == pieces[i].name } ?: continue
            val pos = c.position ?: continue
            if (pieces[i].row != pos.x || pieces[i].column != pos.y) {
                pieces[i] = pieces[i].copy(row = pos.x, column = pos.y)
            }
        }
        for (i in obstacles.indices) {
            val o = game.obstacles.find { it.name == obstacles[i].name } ?: continue
            val pos = o.position ?: continue
            if (obstacles[i].row != pos.x || obstacles[i].column != pos.y) {
                obstacles[i] = obstacles[i].copy(row = pos.x, column = pos.y)
            }
        }
        // Selection survives but its legal moves just changed under it;
        // deselect to avoid stale highlights.
        selectedHero = null

        val axisName = if (axis == Axis.ROW) "row" else "column"
        val dirName = when {
            axis == Axis.ROW && delta > 0    -> "right"
            axis == Axis.ROW                 -> "left"
            delta > 0                        -> "down"
            else                             -> "up"
        }
        log.add("Warden slides $axisName $index $dirName")
        safePlay(SoundId.ATTACK_SWING)

        // Resolve pit falls AFTER the slide animation has time to play,
        // so the victim visibly arrives over the hole before vanishing.
        isAnimating = true
        try {
            delay(450)
            buffer.clear()
            val crateCountBefore = game.obstacles.size
            if (GameEngine.resolvePitFalls(game)) {
                if (game.obstacles.size < crateCountBefore) {
                    log.add("  a crate plugs the pit")
                }
                replayBuffered()
                refreshFurniture()
                if (GameEngine.isOver(game)) {
                    buffer.clear()
                    observer.onEvent(GameEvent.GameEnded(GameEngine.winner(game)))
                    replayBuffered()
                }
            }
        } finally {
            isAnimating = false
        }
        return true
    }

    /**
     * Best-performing character of [side] by total damage dealt across the
     * game. Returns null if no one on that side scored any damage. Uses
     * [initialPieces] so the MVP can be someone who died during the game.
     */
    fun mvpFor(side: Side): PieceRender? {
        val candidates = when (side) {
            Side.HEROES   -> initialPieces.filter { it.isHero }
            Side.MONSTERS -> initialPieces.filter { !it.isHero }
            Side.DRAW     -> initialPieces.toList()
        }
        return candidates
            .filter { (damageByName[it.name] ?: 0) > 0 }
            .maxByOrNull { damageByName[it.name] ?: 0 }
    }

    // ---- Internals ----

    /** Update [facingLeft] based on a horizontal move from [fromY] to [toY]. */
    private fun updateFacing(name: String, fromY: Int, toY: Int) {
        when {
            toY < fromY -> facingLeft[name] = true
            toY > fromY -> facingLeft[name] = false
            // purely vertical move — keep last facing
        }
    }

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
            // Density tuned for a 7x10 board: 5 crates, 3 pits. Scales
            // with area for other board sizes.
            numObstacles = (boardRows * boardColumns) / 14,
            numPits = (boardRows * boardColumns) / 23,
        )
        GameEngine.placeCharactersRandomly(g)
        return g
    }

    /** Mirror crate positions and open pits from the engine into the
     *  Compose-observable lists. */
    private fun refreshFurniture() {
        obstacles.clear()
        for (o in game.obstacles) {
            val pos = o.position ?: continue
            obstacles.add(ObstacleRender(o.name, pos.x, pos.y))
        }
        pits.clear()
        pits.addAll(game.board.pits)
        android.util.Log.d("Tilewarden", "furniture: pits=${game.board.pits} crates=${obstacles.toList()}")
    }

    private fun rebuildPiecesFromGame() {
        pieces.clear()
        initialPieces.clear()
        var heroSum = 0
        var monsterSum = 0
        for (c in game.characters) {
            renderOf(c)?.let {
                pieces.add(it)
                initialPieces.add(it)
            }
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
        android.util.Log.d("Tilewarden", "replayBuffered START size=${buffer.size}")
        var processed = 0
        while (buffer.isNotEmpty()) {
            val event = buffer.removeFirst()
            android.util.Log.d("Tilewarden", "  event[$processed] = ${event::class.simpleName}")
            describe(event).takeIf { it.isNotEmpty() }?.let { log.add(it) }
            applyEventToPieces(event)
            delay(durationFor(event))
            processed++
        }
        android.util.Log.d("Tilewarden", "replayBuffered END processed=$processed")
    }

    private fun CoroutineScope.applyEventToPieces(event: GameEvent) {
        // Game-state mutations happen first; audio is played LAST so that
        // even a catastrophic audio failure can never block a piece's
        // body / position from updating. The audio call is also wrapped in
        // an extra try/catch on top of the one inside AudioEngine.play(),
        // so absolutely nothing the audio subsystem does can throw out of
        // this function.
        when (event) {
            is GameEvent.PieceMoved -> {
                val idx = pieces.indexOfFirst { it.name == event.character.name }
                if (idx >= 0) {
                    pieces[idx] = pieces[idx].copy(
                        row = event.to.x,
                        column = event.to.y,
                    )
                }
                updateFacing(event.character.name, event.from.y, event.to.y)
                safePlay(SoundId.STEP)
            }
            is GameEvent.Attacked -> {
                val name = event.attacker.name
                lastAttackerName = name
                attacksByName[name] = (attacksByName[name] ?: 0) + 1
                if (name !in attackingPieces) attackingPieces.add(name)
                launch {
                    delay(ATTACK_FLASH_MS)
                    attackingPieces.remove(name)
                }
                safePlay(SoundId.ATTACK_SWING)
            }
            is GameEvent.AttackBlocked -> {
                safePlay(SoundId.BLOCK)
            }
            is GameEvent.Damaged -> {
                lastAttackerName?.let { attacker ->
                    damageByName[attacker] = (damageByName[attacker] ?: 0) + event.wounds
                }
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
                safePlay(SoundId.HIT)
            }
            is GameEvent.Died -> {
                val name = event.character.name
                if (name !in dyingPieces) dyingPieces.add(name)
                launch {
                    delay(DEATH_MS)
                    pieces.removeAll { it.name == name }
                    dyingPieces.remove(name)
                }
                safePlay(SoundId.DEATH)
            }
            is GameEvent.FellInPit -> {
                val name = event.character.name
                if (name !in dyingPieces) dyingPieces.add(name)
                launch {
                    delay(DEATH_MS)
                    pieces.removeAll { it.name == name }
                    dyingPieces.remove(name)
                }
                safePlay(SoundId.DEATH)
            }
            is GameEvent.GameEnded -> {
                isOver = true
                winner = event.winner
                safePlay(
                    if (event.winner == Side.HEROES) SoundId.VICTORY
                    else                              SoundId.DEFEAT,
                )
            }
            else -> { }
        }
    }

    /** Play a SFX with an extra try/catch on top of [AudioEngine.play]'s own,
     *  so a freak audio crash absolutely cannot propagate up the replay loop. */
    private fun safePlay(id: SoundId) {
        try { audio?.play(id) } catch (t: Throwable) {
            android.util.Log.w("Tilewarden", "audio.play($id) threw", t)
        }
    }

    private fun durationFor(event: GameEvent): Long = when (event) {
        is GameEvent.PieceMoved     -> MOVE_MS
        is GameEvent.Attacked       -> ATTACK_MS
        is GameEvent.AttackResolved -> RESOLVE_MS
        is GameEvent.AttackBlocked  -> BLOCK_MS
        is GameEvent.Damaged        -> DAMAGE_MS
        is GameEvent.Died           -> DEATH_MS
        is GameEvent.FellInPit      -> DEATH_MS
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
        is GameEvent.FellInPit      -> "  ${event.character.name} falls into a pit!"
        is GameEvent.GameEnded      -> "=== GAME END — winner: ${event.winner} ==="
    }
}

