package com.tilewarden.core

/**
 * Everything observable that happens during a [Game].
 *
 * The game engine never talks to the UI directly; instead it publishes
 * events to a [GameObserver]. The same model drives a console log, a
 * test recorder, an Android Compose UI, or a desktop renderer — none of
 * which the core knows about.
 */
sealed class GameEvent {

    /** The game has started; useful to render the initial state. */
    data class GameStarted(val game: Game) : GameEvent()

    /** A new round is about to start. */
    data class RoundStarted(val round: Int) : GameEvent()

    /** A round has finished resolving every character's turn. */
    data class RoundEnded(val round: Int) : GameEvent()

    /** [character] moved one square from [from] to [to]. */
    data class PieceMoved(
        val character: Character,
        val from: XYLocation,
        val to: XYLocation,
    ) : GameEvent()

    /** [character] cannot move in any direction. */
    data class PieceBlocked(val character: Character) : GameEvent()

    /** [attacker] initiates an attack against [target]. */
    data class Attacked(
        val attacker: Character,
        val target: Character,
    ) : GameEvent()

    /** Attack roll resolved with [hits] potential wounds. */
    data class AttackResolved(
        val attacker: Character,
        val target: Character,
        val hits: Int,
    ) : GameEvent()

    /** [defender] blocked the incoming attack completely. */
    data class AttackBlocked(val defender: Character) : GameEvent()

    /** [character] took [wounds] points of damage. */
    data class Damaged(val character: Character, val wounds: Int) : GameEvent()

    /** [character] reached zero body and is removed from the board. */
    data class Died(val character: Character) : GameEvent()

    /** The game has ended; [winner] indicates the side with most body left. */
    data class GameEnded(val winner: Side) : GameEvent()
}

/** Which side won, if any. */
enum class Side { HEROES, MONSTERS, DRAW }

/**
 * Anything that wants to be told about [GameEvent]s.
 *
 * Implementations:
 * - [ConsoleGameObserver] — prints a one-line summary to stdout.
 * - [SilentGameObserver] — discards everything (use for headless runs).
 * - [RecordingGameObserver] — keeps every event in memory (use in tests).
 */
interface GameObserver {
    fun onEvent(event: GameEvent)
}

/** Default observer. Prints a one-line human-readable summary per event. */
object ConsoleGameObserver : GameObserver {
    override fun onEvent(event: GameEvent) {
        val text = when (event) {
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
        if (text.isNotEmpty()) println(text)
    }
}

/** Discards every event. Useful for tests and headless runs. */
object SilentGameObserver : GameObserver {
    override fun onEvent(event: GameEvent) = Unit
}

/** Captures every event in order for inspection by tests. */
class RecordingGameObserver : GameObserver {
    private val _events: MutableList<GameEvent> = mutableListOf()
    val events: List<GameEvent> get() = _events
    override fun onEvent(event: GameEvent) { _events.add(event) }
    fun clear() { _events.clear() }
    inline fun <reified T : GameEvent> count(): Int = events.count { it is T }
    inline fun <reified T : GameEvent> filterIsInstance(): List<T> =
        events.filterIsInstance<T>()
}
