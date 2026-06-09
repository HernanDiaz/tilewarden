package com.tilewarden.core

/**
 * A weapon wielded by a hero.
 *
 * @property name display name (e.g. "Broadsword")
 * @property attackDice number of attack dice the weapon rolls
 */
data class Weapon(val name: String, val attackDice: Int) {
    override fun toString(): String = "$name ($attackDice dice)"
}
