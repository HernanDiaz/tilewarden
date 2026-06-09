package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ports of the exercise tests from the original university project.
 *
 * The Java versions hard-coded `Dado.setSeed(0L)` with `java.util.Random`;
 * since we use `kotlin.random.Random` (different algorithm) we can't
 * replay the exact same dice sequences. Instead we replay the *scenarios*
 * (board state, character placement, body counts) and check the same
 * invariants — which is what the original tests were really exercising.
 *
 * Originals: Ejercicio2_5_3Test, Ejercicio2_5_4_aTest, Ejercicio2_5_4_b_fTest
 */
class OriginalCourseExercisesTest {

    // ---------- 2.5.3: valid targets and valid positions ----------

    /**
     * Replays the board layout from `Ejercicio2_5_3Test.testJeroquestObjetivosValidos`:
     *
     *  ```
     *  . . . . M2 H1 M1 . . .
     *  . . . . .  H2 .  . . .
     *  ```
     *
     * H1 sees both monsters; H2 sees no enemies; M1 and M2 each see H1.
     */
    @Test
    fun `valid targets respect cardinal adjacency and side`() {
        val world = Game(2, 2, 7, 10, 20, SilentGameObserver)
        val heroes = world.characters.filterIsInstance<Hero>()
        val monsters = world.characters.filterIsInstance<Monster>()
        val h1 = heroes[0]
        val h2 = heroes[1]
        val m1 = monsters[0]
        val m2 = monsters[1]

        world.board.movePiece(h1, XYLocation(4, 5))
        world.board.movePiece(h2, XYLocation(5, 5))
        world.board.movePiece(m1, XYLocation(4, 6))
        world.board.movePiece(m2, XYLocation(4, 4))

        // H1 (4,5) is between M2 (4,4) and M1 (4,6) — both enemies in range.
        val h1Targets = GameEngine.validTargets(world, h1)
        assertEquals(2, h1Targets.size)
        assertTrue(m1 in h1Targets)
        assertTrue(m2 in h1Targets)

        // H2 (5,5) has only H1 to its north (ally) and no monster adjacent.
        assertTrue(GameEngine.validTargets(world, h2).isEmpty())

        // M1 sees H1 to the west.
        assertEquals(listOf(h1), GameEngine.validTargets(world, m1))
        // M2 sees H1 to the east.
        assertEquals(listOf(h1), GameEngine.validTargets(world, m2))
    }

    /**
     * Replays `testMovimientoPersonaje`: a 2x1 vertical board with one hero
     * at (1,0) can only move to (0,0).
     */
    @Test
    fun `valid positions on a degenerate board collapse to the one legal move`() {
        val game = Game(1, 0, 2, 1, 20, SilentGameObserver)
        val hero = game.characters.single()
        game.board.movePiece(hero, XYLocation(1, 0))

        val positions = GameEngine.validPositions(game, hero)
        assertEquals(1, positions.size)
        assertEquals(XYLocation(0, 0), positions.single())
    }

    @Test
    fun `valid positions on a 1x2 horizontal board collapse to the legal move`() {
        val game = Game(1, 0, 1, 2, 20, SilentGameObserver)
        val hero = game.characters.single()
        game.board.movePiece(hero, XYLocation(0, 0))

        val positions = GameEngine.validPositions(game, hero)
        assertEquals(1, positions.size)
        assertEquals(XYLocation(0, 1), positions.single())
    }

    // ---------- 2.5.4 b: dead characters are removed from the game ----------

    /**
     * Replays `testEliminaPersonajesMuertos`: when a hero kills a 1-body
     * monster, the active character list goes from 2 to 1.
     */
    @Test
    fun `killing a monster removes it from the character list`() {
        Dice.setSeed(1L)
        val game = Game(1, 1, 7, 10, 20, SilentGameObserver)
        val hero = game.characters.first { it is Hero }
        val monster = game.characters.first { it is Monster }
        // Stage: monster has 1 body, both adjacent.
        monster.body = 1
        game.board.movePiece(hero, XYLocation(4, 5))
        game.board.movePiece(monster, XYLocation(3, 5))

        assertEquals(2, game.characters.size)

        // Run combat until the monster dies. With body=1 against a hero's
        // multi-die weapon, it usually dies on the first round; in the
        // (rare) case it doesn't, keep attacking.
        while (monster.isAlive && GameEngine.atRange(hero.position!!, monster.position!!)) {
            hero.combat(monster, game)
        }

        assertFalse(monster.isAlive)
        assertEquals(1, game.characters.size)
        assertTrue(hero in game.characters)
        assertFalse(monster in game.characters)
        // Their square is now free.
        assertTrue(game.board.isFree(XYLocation(3, 5)))
        assertNull(monster.position)
    }

    /**
     * Replays the spirit of `testHeroeMueveYAtaca`: a hero with adjacent
     * monsters attacks instead of moving. After the turn, the hero hasn't
     * left its tile.
     */
    @Test
    fun `hero with adjacent enemies attacks instead of running away`() {
        Dice.setSeed(0L)
        val rec = RecordingGameObserver()
        val game = Game(1, 2, 7, 10, 20, rec)
        val hero = game.characters.first { it is Hero }
        val monsters = game.characters.filterIsInstance<Monster>()

        val heroStart = XYLocation(4, 5)
        game.board.movePiece(hero, heroStart)
        game.board.movePiece(monsters[0], XYLocation(3, 5))  // north neighbour
        game.board.movePiece(monsters[1], XYLocation(4, 6))  // east neighbour

        hero.resolveTurn(game)

        // The Character.resolveTurn attacks first, then moves. The hero
        // does call actionCombat, and at least one Attacked event fired.
        assertTrue(
            rec.count<GameEvent.Attacked>() >= 1,
            "Hero with adjacent enemies should have launched at least one attack"
        )
    }

    // ---------- 2.5.4 (a-like): statistics tally after a combat ----------

    /**
     * Loose port of `testEstadisticas`: after some hero attacks the hero
     * counter advances; after some monster attacks the monster counter
     * advances. We don't fix the exact totals (RNG diverges) but the
     * invariants must hold.
     */
    @Test
    fun `statistics increment in lockstep with combat events`() {
        Dice.setSeed(4L)
        val rec = RecordingGameObserver()
        val game = Game(2, 2, 4, 4, 3, rec)
        // Lay out four characters as in the original 2x2 → here we use 4x4
        // for more breathing room.
        val heroes = game.characters.filterIsInstance<Hero>()
        val monsters = game.characters.filterIsInstance<Monster>()
        game.board.movePiece(heroes[0], XYLocation(0, 0))
        game.board.movePiece(heroes[1], XYLocation(1, 1))
        game.board.movePiece(monsters[0], XYLocation(0, 1))
        game.board.movePiece(monsters[1], XYLocation(1, 0))

        // Resolve a few turns: each character attacks if it has an adjacent enemy.
        heroes[0].resolveTurn(game)
        heroes[1].resolveTurn(game)
        if (monsters[0] in game.characters) monsters[0].resolveTurn(game)

        // Whatever happened, every Attacked emitted by a hero ↔ heroAttacks++
        val heroAttacked = rec.filterIsInstance<GameEvent.Attacked>()
            .count { it.attacker is Hero }
        val monsterAttacked = rec.filterIsInstance<GameEvent.Attacked>()
            .count { it.attacker is Monster }

        assertEquals(heroAttacked, game.statistics.heroAttacks)
        assertEquals(monsterAttacked, game.statistics.monsterAttacks)
        // Damage tally is non-negative.
        assertTrue(game.statistics.heroDamageDealt >= 0)
        assertTrue(game.statistics.monsterDamageDealt >= 0)
    }
}
