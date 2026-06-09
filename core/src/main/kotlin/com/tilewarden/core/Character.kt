package com.tilewarden.core

/**
 * Base class for everything that occupies a square and takes a turn.
 *
 * Stats split into:
 * - **Initial** (immutable): the value at character creation. Useful to
 *   reset between games, or to compute damage taken vs maximum body.
 * - **Current** (mutable): may change during the game — for example
 *   [body] decreases when wounded, or a [Hero]'s [attack] changes when
 *   the weapon is swapped.
 *
 * Subclasses implement [defend] (different defence dice mechanics for
 * heroes vs monsters) and provide a [symbol] for ASCII rendering and a
 * [spriteId] for graphical rendering.
 */
abstract class Character(
    val name: String,
    moves: Int,
    attack: Int,
    defense: Int,
    body: Int,
) : Piece {

    val initialMoves: Int = moves
    val initialAttack: Int = attack
    val initialDefense: Int = defense
    val initialBody: Int = body

    var moves: Int = moves
        protected set

    var attack: Int = attack
        protected set

    var defense: Int = defense
        protected set

    /**
     * Hit points. Public set lets tests stage scenarios (e.g. wound a
     * monster to 1 body to verify a one-hit kill). The engine itself only
     * mutates body through [defend].
     */
    var body: Int = body

    override var position: XYLocation? = null

    val isAlive: Boolean get() = body > 0

    /** Default ASCII rendering — subclasses override with a letter. */
    override val symbol: Char get() = '?'

    /** Resource identifier the UI maps to its actual asset (no Swing here). */
    abstract val spriteId: String

    /**
     * Roll [attack] d6 and return how many landed (each die above 3 is a hit).
     */
    fun rollAttack(): Int {
        var hits = 0
        repeat(attack) { if (Dice.roll() > 3) hits++ }
        return hits
    }

    /**
     * Defend against [hits] incoming hits. Implementations differ per side
     * (heroes block on 5-6, monsters only on 6).
     *
     * @return number of wounds actually taken (already subtracted from [body]).
     */
    abstract fun defend(hits: Int, game: Game): Int

    /**
     * Default enmity: anything not of the same exact class.
     * [Hero] and [Monster] override to the obvious meanings.
     */
    open fun isEnemy(other: Character): Boolean = this::class != other::class

    /**
     * Attack [target]: resolve the dice, apply wounds, and if the target
     * dies, remove it from the board and from the game.
     */
    open fun combat(target: Character, game: Game) {
        game.notify(GameEvent.Attacked(this, target))
        val hits = rollAttack()
        game.notify(GameEvent.AttackResolved(this, target, hits))
        target.defend(hits, game)
        if (!target.isAlive) {
            game.notify(GameEvent.Died(target))
            game.board.removePiece(target)
            game.removeCharacter(target)
        }
    }

    /**
     * AI: pick a random valid target and attack it.
     * @return `true` if an attack happened.
     */
    fun actionCombat(game: Game): Boolean {
        val targets = GameEngine.validTargets(game, this)
        if (targets.isEmpty()) return false
        val choice = targets[Dice.roll(targets.size) - 1]
        combat(choice, game)
        return true
    }

    /**
     * AI: take up to [maxSquares] random steps. Stops early if blocked.
     * @return squares actually moved.
     */
    open fun actionMove(game: Game, maxSquares: Int = moves): Int {
        var moved = 0
        val budget = minOf(maxSquares, moves)
        while (moved < budget) {
            val options = GameEngine.validPositions(game, this)
            if (options.isEmpty()) break
            val pick = options[Dice.roll(options.size) - 1]
            val from = position ?: break
            if (game.board.movePiece(this, pick)) {
                game.notify(GameEvent.PieceMoved(this, from, pick))
                moved++
            } else break
        }
        if (GameEngine.isBlocked(game, this)) {
            game.notify(GameEvent.PieceBlocked(this))
        }
        return moved
    }

    /** Default turn AI: attack first, then move. Heroes override this. */
    open fun resolveTurn(game: Game) {
        actionCombat(game)
        actionMove(game)
    }

    override fun toString(): String =
        "$name (moves:$moves attack:$attack defense:$defense body:$body/$initialBody)"
}
