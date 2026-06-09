package com.tilewarden.core

/** Fast, hard-hitting hero. Comes with a Broadsword (3d). */
class Barbarian(name: String, playerName: String) : Hero(
    name = name,
    moves = MOVES,
    attack = ATTACK,
    defense = DEFENSE,
    body = BODY,
    playerName = playerName,
    initialWeapon = Weapon("Broadsword", 3),
) {
    override val symbol: Char = 'B'
    override val spriteId: String = "barbarian"

    companion object {
        const val MOVES = 7
        const val ATTACK = 1
        const val DEFENSE = 2
        const val BODY = 8
    }
}
