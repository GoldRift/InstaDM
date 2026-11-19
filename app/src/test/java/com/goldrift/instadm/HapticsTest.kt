package com.goldrift.instadm

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

    @Test
    fun togglePicksTheOnAndOffType() {
        val recorder = Recorder()
        recorder.toggle(true)
        recorder.toggle(false)
        assertEquals(
            listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff),
            recorder.types,
        )
    }
}
