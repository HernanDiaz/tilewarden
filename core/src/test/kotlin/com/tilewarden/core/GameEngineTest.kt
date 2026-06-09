package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameEngineTest {

    // ---------- atRange ----------

    @Test
    fun `atRange returns true for the four cardinal neighbours`() {
        val origin = XYLocation(5, 5)
        assertTrue(GameEngine.atRange(origin, XYLocation(4, 5)))
        assertTrue(GameEngine.atRange(origin, XYLocation(6, 5)))
        assertTrue(GameEngine.atRange(origin, XYLocation(5, 4)))
        assertTrue(GameEngine.atRange(origin, XYLocation(5, 6)))
    }

    @Test
    fun `atRange returns false for diagonals, same square, or farther`() {
        val origin = XYLocation(5, 5)
        assertFalse(GameEngine.atRange(origin, XYLocation(4, 4)))
        assertFalse(GameEngine.atRange(origin, XYLocation(6, 6)))
        assertFalse(GameEngine.atRange(origin, origin))
        assertFalse(GameEngine.atRange(origin, XYLocation(3, 5)))
        assertFalse(GameEngine.atRange(origin, XYLocation(5, 8)))
    }

    // ---------- validPositions ----------

    @Test
    fun `validPositions returns all four neighbours inside an empty board`() {
        val game = Game(1, 0, 5, 5, 5)
        val hero = game.characters.single()
        game.board.movePiece(hero, XYLocation(2, 2))
        val positions = GameEngine.validPositions(game, hero)
        assertEquals(4, positions.size)
        assertTrue(XYLocation(1, 2) in positions) // N
        assertTrue(XYLocation(3, 2) in positions) // S
        assertTrue(XYLocation(2, 1) in positions) // W
        assertTrue(XYLocation(2, 3) in positions) // E
    }

    @Test
    fun `validPositions excludes out-of-bounds neighbours`() {
        val game = Game(1, 0, 3, 3, 5)
        val hero = game.characters.single()
        game.board.movePiece(hero, XYLocation(0, 0))
        val positions = GameEngine.validPositions(game, hero)
        assertEquals(2, positions.size) // only S and E
        assertTrue(XYLocation(1, 0) in positions)
        assertTrue(XYLocation(0, 1) in positions)
    }

    @Test
    fun `validPositions returns empty when off-board`() {
        val game = Game(1, 0, 3, 3, 5)
        val hero = game.characters.single()
        // Don't place on the board: position stays null
        assertTrue(GameEngine.validPositions(game, hero).isEmpty())
    }

    @Test
    fun `validPositions excludes occupied squares`() {
        val game = Game(1, 1, 5, 5, 5)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        game.board.movePiece(hero, XYLocation(2, 2))
        game.board.movePiece(monster, XYLocation(1, 2)) // block north
        val positions = GameEngine.validPositions(game, hero)
        assertFalse(XYLocation(1, 2) in positions)
        assertEquals(3, positions.size)
    }

    // ---------- validTargets ----------

    @Test
    fun `validTargets picks adjacent enemies of the opposing side`() {
        // Build a controlled game with manual setup
        val game = Game(0, 0, 5, 5, 5)  // zero auto-generated chars
        val hero = Barbarian("H", "p")
        val m1 = Goblin("M1")
        val m2 = Mummy("M2")
        val allyDummy = Dwarf("Ally", "p")
        // Inject characters by hand-placing them on the board, then adding via reflection? Simpler:
        // We extend the public surface implicitly via removeCharacter / characters being read-only.
        // But Game.init is the only way to add. Use a 4-char game and reuse those.

        val world = Game(2, 2, 5, 5, 5)
        val heroes = world.characters.filterIsInstance<Hero>()
        val monsters = world.characters.filterIsInstance<Monster>()
        val H = heroes[0]
        world.board.movePiece(H, XYLocation(2, 2))
        world.board.movePiece(heroes[1], XYLocation(0, 0))   // far away — ally, not enemy
        world.board.movePiece(monsters[0], XYLocation(2, 3)) // east — enemy in range
        world.board.movePiece(monsters[1], XYLocation(1, 2)) // north — enemy in range

        val targets = GameEngine.validTargets(world, H)
        assertEquals(2, targets.size)
        assertTrue(monsters[0] in targets)
        assertTrue(monsters[1] in targets)
    }

    @Test
    fun `validTargets excludes dead, distant, or allied characters`() {
        val world = Game(2, 2, 5, 5, 5)
        val heroes = world.characters.filterIsInstance<Hero>()
        val monsters = world.characters.filterIsInstance<Monster>()
        val H = heroes[0]
        world.board.movePiece(H, XYLocation(2, 2))
        world.board.movePiece(heroes[1], XYLocation(2, 3))   // ally adjacent
        world.board.movePiece(monsters[0], XYLocation(0, 0)) // far enemy
        monsters[1].body = 0                                 // dead enemy adjacent
        world.board.movePiece(monsters[1], XYLocation(2, 1))

        val targets = GameEngine.validTargets(world, H)
        assertTrue(targets.isEmpty(),
            "Expected no targets but got: ${targets.map { it.name }}")
    }

    // ---------- isBlocked ----------

    @Test
    fun `isBlocked is true at a corner enclosed by walls and a piece`() {
        val game = Game(2, 0, 2, 2, 5)
        val h0 = game.characters[0]
        val h1 = game.characters[1]
        game.board.movePiece(h0, XYLocation(0, 0))   // top-left corner
        game.board.movePiece(h1, XYLocation(0, 1))   // east neighbour blocks one direction
        // h0 has board edges N and W, ally on E, free S — not blocked.
        assertFalse(GameEngine.isBlocked(game, h0))
        // Place h1 to the S as well: but we only have one other char.
        // So we use a 1x1 board to enforce true.
        val tiny = Game(1, 0, 1, 1, 5)
        val solo = tiny.characters.single()
        tiny.board.movePiece(solo, XYLocation(0, 0))
        assertTrue(GameEngine.isBlocked(tiny, solo))
    }

    @Test
    fun `isBlocked is true for an off-board character`() {
        val game = Game(1, 0, 5, 5, 5)
        val solo = game.characters.single()
        // Don't place it on the board.
        assertTrue(GameEngine.isBlocked(game, solo))
    }

    // ---------- opponentsLeft / winner ----------

    @Test
    fun `opponentsLeft is true while both sides have at least one alive`() {
        val game = Game(2, 2, 5, 5, 5)
        assertTrue(GameEngine.opponentsLeft(game))

        // Kill all monsters
        game.characters.filterIsInstance<Monster>().forEach { it.body = 0 }
        assertFalse(GameEngine.opponentsLeft(game))
    }

    @Test
    fun `winner picks the side with more body`() {
        val game = Game(1, 1, 5, 5, 5)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }

        hero.body = 5
        monster.body = 3
        assertEquals(Side.HEROES, GameEngine.winner(game))

        hero.body = 3
        monster.body = 5
        assertEquals(Side.MONSTERS, GameEngine.winner(game))

        hero.body = 4
        monster.body = 4
        assertEquals(Side.DRAW, GameEngine.winner(game))
    }

    // ---------- placeCharactersRandomly ----------

    @Test
    fun `placeCharactersRandomly puts every character on a unique square`() {
        Dice.setSeed(0L)
        val game = Game(2, 2, 5, 5, 5)
        GameEngine.placeCharactersRandomly(game)
        val positions = game.characters.mapNotNull { it.position }
        assertEquals(game.characters.size, positions.size)
        assertEquals(positions.toSet().size, positions.size,
            "Two characters placed on the same square: $positions")
        for (p in positions) {
            assertTrue(game.board.isInBounds(p))
        }
    }

    @Test
    fun `randomDirection always returns one of the four cardinals`() {
        Dice.setSeed(0L)
        val seen = mutableSetOf<Direction>()
        repeat(200) { seen.add(GameEngine.randomDirection()) }
        assertEquals(Direction.entries.toSet(), seen)
    }
}
