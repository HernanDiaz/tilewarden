package com.tilewarden.core

/**
 * Base class for the player-controlled heroes.
 *
 * Heroes wield a [Weapon] which dictates their effective [attack]; equipping
 * `null` reverts to the unarmed base [initialAttack]. They defend with the
 * standard 5-or-6 rule.
 */
abstract class Hero(
    name: String,
    moves: Int,
    attack: Int,
    defense: Int,
    body: Int,
    val playerName: String,
    initialWeapon: Weapon?,
) : Character(name, moves, attack, defense, body) {

    /**
     * Currently equipped weapon. Re-assigning updates [attack] automatically.
     */
    var weapon: Weapon? = null
        set(value) {
            field = value
            attack = value?.attackDice ?: initialAttack
        }

    init {
        // Force the setter to run so [attack] reflects [initialWeapon].
        weapon = initialWeapon
    }

    /** Heroes block a hit on a 5 or 6. */
    override fun defend(hits: Int, game: Game): Int {
        var hitsLeft = hits
        var dice = defense
        while (hitsLeft > 0 && dice > 0) {
            if (Dice.roll() > 4) hitsLeft--
            dice--
        }
        if (hitsLeft == 0) {
            if (hits > 0) game.notify(GameEvent.AttackBlocked(this))
            return 0
        }
        val wounds = minOf(body, hitsLeft)
        body -= wounds
        game.notify(GameEvent.Damaged(this, wounds))
        return wounds
    }

    override fun isEnemy(other: Character): Boolean = other is Monster

    /** Records the attack in [Game.statistics] after the parent resolves it. */
    override fun combat(target: Character, game: Game) {
        val before = target.body
        super.combat(target, game)
        game.statistics.recordHeroAttack()
        game.statistics.recordHeroDamage(before - target.body)
    }

    override fun toString(): String =
        weapon?.let { "${super.toString()} wielding $it" } ?: super.toString()
}
