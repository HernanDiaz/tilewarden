package com.tilewarden.core

/**
 * The state of a single match.
 *
 * Owns the [board], the list of live [characters], the [statistics] tally,
 * and the [observer] that the engine notifies as things happen.
 *
 * The constructor randomly chooses concrete hero / monster subclasses
 * (Barbarian/Dwarf, Mummy/Goblin) using [Dice]; set the seed beforehand
 * if you need a reproducible composition.
 */
class Game(
    numHeroes: Int,
    numMonsters: Int,
    boardRows: Int,
    boardColumns: Int,
    val totalRounds: Int,
    val observer: GameObserver = SilentGameObserver,
) {
    val board: Board = Board(boardRows, boardColumns)
    val statistics: Statistics = Statistics()

    var currentRound: Int = 1
        internal set

    private val _characters: MutableList<Character> = mutableListOf()

    /** Live (un-killed) characters in turn order. */
    val characters: List<Character> get() = _characters

    init {
        require(numHeroes >= 0) { "numHeroes must be non-negative" }
        require(numMonsters >= 0) { "numMonsters must be non-negative" }
        require(totalRounds >= 1) { "totalRounds must be at least 1" }

        repeat(numHeroes) { idx ->
            val hero: Hero = if (Dice.roll() % 2 == 0)
                Barbarian("Barbarian $idx", NO_PLAYER)
            else
                Dwarf("Dwarf $idx", NO_PLAYER)
            _characters.add(hero)
        }
        repeat(numMonsters) { idx ->
            val monster: Monster = if (Dice.roll() % 2 == 0)
                Mummy("Mummy $idx")
            else
                Goblin("Goblin $idx")
            _characters.add(monster)
        }
    }

    /** Push an event to the registered observer. */
    fun notify(event: GameEvent) {
        observer.onEvent(event)
    }

    /**
     * Remove [character] from the active roster. Called by the engine
     * after a character dies and is taken off the board.
     */
    fun removeCharacter(character: Character) {
        _characters.remove(character)
    }

    override fun toString(): String = buildString {
        for (c in characters) appendLine(c)
        append(board)
        append('\n')
        append(statistics)
    }

    companion object {
        const val NO_PLAYER: String = "<no player>"
    }
}
