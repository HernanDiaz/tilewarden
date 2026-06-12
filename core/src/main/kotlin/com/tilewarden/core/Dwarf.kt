package com.tilewarden.core

/** Balanced hero. Comes with a Hand axe (2d). */
class Dwarf(name: String, playerName: String) : Hero(
    name = name,
    moves = MOVES,
    attack = ATTACK,
    defense = DEFENSE,
    body = BODY,
    playerName = playerName,
    initialWeapon = Weapon("Hand axe", 2),
) {
    override val symbol: Char = 'D'
    override val spriteId: String = "dwarf"

    companion object {
        const val MOVES = 2
        const val ATTACK = 1
        const val DEFENSE = 2
        const val BODY = 7
    }
}
