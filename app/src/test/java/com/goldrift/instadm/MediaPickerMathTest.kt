package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.MediaPickerDragState
import com.goldrift.instadm.ui.chat.applyMediaPickerHeaderDrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPickerMathTest {
    @Test
    fun incrementalUpwardDragsAccumulatePastThresholdToExpand() {
        var state = MediaPickerDragState(sheetExpanded = false)
        val steps = listOf(-25f, -25f, -25f)
        var expandedCount = 0

        for (delta in steps) {
            val result = applyMediaPickerHeaderDrag(
                current = state,
                dragDeltaY = delta,
                expandThresholdPx = 64f,
                dismissThresholdPx = 110f,
            )
            state = result.state
            if (result.expandedNow) expandedCount++
        }

        assertEquals(1, expandedCount)
        assertTrue(state.sheetExpanded)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertEquals(0f, state.upwardDragPx, 0.001f)
    }

    @Test
    fun upwardDragFirstCancelsDownwardOffsetBeforeAccumulatingExpansion() {
        var state = MediaPickerDragState(sheetExpanded = false, dragOffsetY = 40f)

        val partial = applyMediaPickerHeaderDrag(
            current = state,
            dragDeltaY = -30f,
            expandThresholdPx = 64f,
            dismissThresholdPx = 110f,
        )
        state = partial.state
        assertFalse(partial.expandedNow)
        assertFalse(state.sheetExpanded)
        assertEquals(10f, state.dragOffsetY, 0.001f)
        assertEquals(0f, state.upwardDragPx, 0.001f)

        val crossZero = applyMediaPickerHeaderDrag(
            current = state,
            dragDeltaY = -30f,
            expandThresholdPx = 64f,
            dismissThresholdPx = 110f,
        )
        state = crossZero.state
        assertFalse(crossZero.expandedNow)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertEquals(20f, state.upwardDragPx, 0.001f)
    }

    @Test
    fun downwardDragArmsDismissOnlyWhenCollapsedAndCrossesThreshold() {
        val collapsed = MediaPickerDragState(sheetExpanded = false)
        val first = applyMediaPickerHeaderDrag(
            current = collapsed,
            dragDeltaY = 120f,
            expandThresholdPx = 64f,
            dismissThresholdPx = 110f,
        )
        assertTrue(first.armedDismissNow)
        assertTrue(first.state.armedDismiss)
        assertEquals(120f, first.state.dragOffsetY, 0.001f)

        val second = applyMediaPickerHeaderDrag(
            current = first.state,
            dragDeltaY = 20f,
            expandThresholdPx = 64f,
            dismissThresholdPx = 110f,
        )
        assertFalse(second.armedDismissNow)
        assertTrue(second.state.armedDismiss)

        val expanded = MediaPickerDragState(sheetExpanded = true)
        val expandedDown = applyMediaPickerHeaderDrag(
            current = expanded,
            dragDeltaY = 120f,
            expandThresholdPx = 64f,
            dismissThresholdPx = 110f,
        )
        assertFalse(expandedDown.armedDismissNow)
        assertFalse(expandedDown.state.armedDismiss)
        assertEquals(120f, expandedDown.state.dragOffsetY, 0.001f)
    }

    @Test
    fun alreadyExpandedSheetIgnoresUpwardDragAtZeroOffset() {
        val expanded = MediaPickerDragState(sheetExpanded = true, dragOffsetY = 0f)
        val result = applyMediaPickerHeaderDrag(
            current = expanded,
            dragDeltaY = -80f,
            expandThresholdPx = 64f,
            dismissThresholdPx = 110f,
        )
        assertFalse(result.expandedNow)
        assertTrue(result.state.sheetExpanded)
        assertEquals(0f, result.state.dragOffsetY, 0.001f)
        assertEquals(0f, result.state.upwardDragPx, 0.001f)
    }
}
