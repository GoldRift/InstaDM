package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.moved
import com.goldrift.instadm.ui.chat.stagedDragTarget
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

    @Test
    fun aHalfStepKeepsTheItemInPlace() {
        assertEquals(1, stagedDragTarget(index = 1, offsetPx = 30f, stepPx = 64f, lastIndex = 3))
        assertEquals(1, stagedDragTarget(index = 1, offsetPx = -30f, stepPx = 64f, lastIndex = 3))
    }

    @Test
    fun aFullStepMovesOneSlot() {
        assertEquals(2, stagedDragTarget(index = 1, offsetPx = 64f, stepPx = 64f, lastIndex = 3))
        assertEquals(0, stagedDragTarget(index = 1, offsetPx = -64f, stepPx = 64f, lastIndex = 3))
        assertEquals(3, stagedDragTarget(index = 1, offsetPx = 160f, stepPx = 64f, lastIndex = 3))
    }

    @Test
    fun clampsAtBothEnds() {
        assertEquals(0, stagedDragTarget(index = 0, offsetPx = -500f, stepPx = 64f, lastIndex = 3))
        assertEquals(3, stagedDragTarget(index = 3, offsetPx = 500f, stepPx = 64f, lastIndex = 3))
    }

    @Test
    fun keepsTheIndexWithoutAMeasuredStep() {
        assertEquals(2, stagedDragTarget(index = 2, offsetPx = 500f, stepPx = 0f, lastIndex = 3))
    }
}
