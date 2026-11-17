package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.moved
import org.junit.Assert.assertEquals
import org.junit.Test

class StagedMediaTest {
    private val items = listOf("a", "b", "c", "d")

    @Test
    fun movesAnItemForward() {
        assertEquals(listOf("b", "c", "a", "d"), items.moved(0, 2))
    }

    @Test
    fun movesAnItemBackward() {
        assertEquals(listOf("a", "d", "b", "c"), items.moved(3, 1))
    }

    @Test
    fun ignoresAMoveThatChangesNothing() {
        assertEquals(items, items.moved(1, 1))
    }

    @Test
    fun ignoresAMoveOutsideTheList() {
        assertEquals(items, items.moved(-1, 2))
        assertEquals(items, items.moved(2, 4))
        assertEquals(items, items.moved(4, 0))
        assertEquals(emptyList<String>(), emptyList<String>().moved(0, 0))
    }
}
