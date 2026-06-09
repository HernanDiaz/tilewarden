package com.tilewarden.core

/** Slow but tough monster with the harder defence. */
class Mummy(name: String) : Monster(
    name = name,
    moves = MOVES,
    attack = ATTACK,
    defense = DEFENSE,
    body = BODY,
) {
    override val symbol: Char = 'M'
    override val spriteId: String = "mummy"

    companion object {
        const val MOVES = 4
        const val ATTACK = 3
        const val DEFENSE = 4
        const val BODY = 2
    }
}
