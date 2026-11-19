package com.goldrift.instadm

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.goldrift.instadm.ui.components.confirm
import com.goldrift.instadm.ui.components.contextClick
import com.goldrift.instadm.ui.components.longPress
import com.goldrift.instadm.ui.components.reject
import com.goldrift.instadm.ui.components.segmentFrequentTick
import com.goldrift.instadm.ui.components.segmentTick
import com.goldrift.instadm.ui.components.thresholdActivated
import com.goldrift.instadm.ui.components.toggle
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticsTest {
    private class Recorder : HapticFeedback {
        val types = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            types += hapticFeedbackType
        }
    }

    private class Case(
        val helper: String,
        val expected: HapticFeedbackType,
        val call: HapticFeedback.() -> Unit,
    )

    private fun cases() = listOf(
        Case("confirm()", HapticFeedbackType.Confirm) { confirm() },
        Case("reject()", HapticFeedbackType.Reject) { reject() },
        Case("toggle(true)", HapticFeedbackType.ToggleOn) { toggle(true) },
        Case("toggle(false)", HapticFeedbackType.ToggleOff) { toggle(false) },
        Case("thresholdActivated()", HapticFeedbackType.GestureThresholdActivate) {
            thresholdActivated()
        },
        Case("segmentTick()", HapticFeedbackType.SegmentTick) { segmentTick() },
        Case("segmentFrequentTick()", HapticFeedbackType.SegmentFrequentTick) {
            segmentFrequentTick()
        },
        Case("longPress()", HapticFeedbackType.LongPress) { longPress() },
        Case("contextClick()", HapticFeedbackType.ContextClick) { contextClick() },
    )

    @Test
    fun everyHelperRequestsItsPlatformType() {
        for (case in cases()) {
            val recorder = Recorder()
            case.call(recorder)
            assertEquals(case.helper, listOf(case.expected), recorder.types)
        }
    }
}
