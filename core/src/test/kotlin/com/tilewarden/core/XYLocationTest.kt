package com.tilewarden.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class XYLocationTest {

    @Test
    fun `equality is value-based (data class)`() {
        assertEquals(XYLocation(3, 4), XYLocation(3, 4))
        assertNotEquals(XYLocation(3, 4), XYLocation(4, 3))
    }

    @Test
    fun `cardinal neighbours move on the expected axis`() {
        val origin = XYLocation(5, 5)
        assertEquals(XYLocation(4, 5), origin.north())
        assertEquals(XYLocation(6, 5), origin.south())
        assertEquals(XYLocation(5, 6), origin.east())
        assertEquals(XYLocation(5, 4), origin.west())
    }

    @Test
    fun `plus Direction agrees with cardinal helpers`() {
        val origin = XYLocation(2, 7)
        assertEquals(origin.north(), origin + Direction.NORTH)
        assertEquals(origin.south(), origin + Direction.SOUTH)
        assertEquals(origin.east(),  origin + Direction.EAST)
        assertEquals(origin.west(),  origin + Direction.WEST)
    }

    @Test
    fun `toString uses x y parens format`() {
        assertEquals("(3,4)", XYLocation(3, 4).toString())
        assertEquals("(0,0)", XYLocation(0, 0).toString())
    }
}
