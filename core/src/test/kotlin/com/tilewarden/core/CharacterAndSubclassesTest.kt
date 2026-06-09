package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CharacterAndSubclassesTest {

    @Test
    fun `Barbarian has the expected stats and broadsword`() {
        val b = Barbarian("Conan", "player1")
        assertEquals("Conan", b.name)
        assertEquals("player1", b.playerName)
        assertEquals(Barbarian.MOVES, b.moves)
        // Attack reflects the broadsword (3d), not the unarmed value (1).
        assertEquals(3, b.attack)
        assertEquals(Barbarian.DEFENSE, b.defense)
        assertEquals(Barbarian.BODY, b.body)
        assertEquals('B', b.symbol)
        assertEquals("barbarian", b.spriteId)
        assertEquals(Weapon("Broadsword", 3), b.weapon)
    }

    @Test
    fun `Dwarf has the expected stats and hand axe`() {
        val d = Dwarf("Gimli", "player2")
        assertEquals(Dwarf.MOVES, d.moves)
        assertEquals(2, d.attack)               // Hand axe = 2d
        assertEquals(Dwarf.DEFENSE, d.defense)
        assertEquals(Dwarf.BODY, d.body)
        assertEquals('D', d.symbol)
        assertEquals("dwarf", d.spriteId)
        assertEquals(Weapon("Hand axe", 2), d.weapon)
    }

    @Test
    fun `Goblin has the expected stats`() {
        val g = Goblin("Gob")
        assertEquals(Goblin.MOVES, g.moves)
        assertEquals(Goblin.ATTACK, g.attack)
        assertEquals(Goblin.DEFENSE, g.defense)
        assertEquals(Goblin.BODY, g.body)
        assertEquals('G', g.symbol)
        assertEquals("goblin", g.spriteId)
    }

    @Test
    fun `Mummy has the expected stats`() {
        val m = Mummy("Imhotep")
        assertEquals(Mummy.MOVES, m.moves)
        assertEquals(Mummy.ATTACK, m.attack)
        assertEquals(Mummy.DEFENSE, m.defense)
        assertEquals(Mummy.BODY, m.body)
        assertEquals('M', m.symbol)
        assertEquals("mummy", m.spriteId)
    }

    @Test
    fun `initial stats stay frozen even when current change`() {
        val b = Barbarian("Conan", "p")
        b.body = 3
        assertEquals(3, b.body)
        assertEquals(Barbarian.BODY, b.initialBody)
        assertEquals(Barbarian.MOVES, b.initialMoves)
        assertEquals(Barbarian.DEFENSE, b.initialDefense)
        assertEquals(Barbarian.ATTACK, b.initialAttack)  // 1, NOT the 3 from the broadsword
    }

    @Test
    fun `setting weapon to null reverts attack to initial`() {
        val b = Barbarian("Conan", "p")
        assertEquals(3, b.attack)
        b.weapon = null
        assertEquals(Barbarian.ATTACK, b.attack)
    }

    @Test
    fun `swapping weapon updates attack`() {
        val b = Barbarian("Conan", "p")
        b.weapon = Weapon("Dagger", 1)
        assertEquals(1, b.attack)
        b.weapon = Weapon("Greataxe", 5)
        assertEquals(5, b.attack)
    }

    @Test
    fun `fresh character has no position and is alive`() {
        val g = Goblin("g")
        assertNull(g.position)
        assertTrue(g.isAlive)
    }

    @Test
    fun `body 0 means not alive`() {
        val g = Goblin("g")
        g.body = 0
        assertFalse(g.isAlive)
    }

    @Test
    fun `Hero is enemy of any Monster only`() {
        val hero = Dwarf("d", "p")
        val monster = Goblin("g")
        val anotherHero = Barbarian("b", "p")
        assertTrue(hero.isEnemy(monster))
        assertFalse(hero.isEnemy(anotherHero))
        assertFalse(hero.isEnemy(hero))
    }

    @Test
    fun `Monster is enemy of any Hero only`() {
        val monster = Goblin("g")
        val hero = Barbarian("b", "p")
        val mummy = Mummy("m")
        assertTrue(monster.isEnemy(hero))
        assertFalse(monster.isEnemy(mummy))
        assertFalse(monster.isEnemy(monster))
    }

    @Test
    fun `Hero toString includes weapon`() {
        val b = Barbarian("Conan", "p")
        val text = b.toString()
        assertTrue(text.contains("Conan"))
        assertTrue(text.contains("Broadsword"))
    }

    @Test
    fun `Hero toString without weapon falls back to base form`() {
        val b = Barbarian("Conan", "p")
        b.weapon = null
        val text = b.toString()
        assertTrue(text.contains("Conan"))
        assertFalse(text.contains("wielding"))
    }
}
