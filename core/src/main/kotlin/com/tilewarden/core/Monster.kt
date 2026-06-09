package com.tilewarden.core

/**
 * Base class for the AI-controlled monsters.
 *
 * Monsters defend with the harder 6-only rule, and are enemies of all
 * heroes.
 */
abstract class Monster(
    name: String,
    moves: Int,
    attack: Int,
    defense: Int,
    body: Int,
) : Character(name, moves, attack, defense, body) {

    /** Monsters only block a hit on a 6. */
    override fun defend(hits: Int, game: Game): Int {
        var hitsLeft = hits
        var dice = defense
        while (hitsLeft > 0 && dice > 0) {
            if (Dice.roll() == 6) hitsLeft--
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

    override fun isEnemy(other: Character): Boolean = other is Hero

    override fun combat(target: Character, game: Game) {
        val before = target.body
        super.combat(target, game)
        game.statistics.recordMonsterAttack()
        game.statistics.recordMonsterDamage(before - target.body)
    }
}
