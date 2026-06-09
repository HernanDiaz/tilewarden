package com.tilewarden.core

/** Fragile but fast monster. */
class Goblin(name: String) : Monster(
    name = name,
    moves = MOVES,
    attack = ATTACK,
    defense = DEFENSE,
    body = BODY,
) {
    override val symbol: Char = 'G'
    override val spriteId: String = "goblin"

    companion object {
        const val MOVES = 10
        const val ATTACK = 2
        const val DEFENSE = 1
        const val BODY = 1
    }
}
