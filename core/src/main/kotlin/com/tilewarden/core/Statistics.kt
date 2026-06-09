package com.tilewarden.core

/**
 * Per-game tally of attacks and damage dealt by each side.
 *
 * Counters are write-protected: only the bundled `record*` methods mutate them.
 */
class Statistics {
    var heroAttacks: Int = 0
        private set
    var monsterAttacks: Int = 0
        private set
    var heroDamageDealt: Int = 0
        private set
    var monsterDamageDealt: Int = 0
        private set

    fun recordHeroAttack() { heroAttacks++ }
    fun recordMonsterAttack() { monsterAttacks++ }
    fun recordHeroDamage(amount: Int) { heroDamageDealt += amount }
    fun recordMonsterDamage(amount: Int) { monsterDamageDealt += amount }

    override fun toString(): String =
        "Hero attacks: $heroAttacks, Hero damage: $heroDamageDealt, " +
        "Monster attacks: $monsterAttacks, Monster damage: $monsterDamageDealt"
}
