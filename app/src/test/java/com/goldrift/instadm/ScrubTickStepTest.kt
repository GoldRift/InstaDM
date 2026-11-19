package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.scrubTickStep
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubTickStepTest {
    @Test
    fun startsInTheFirstBand() {
        assertEquals(0, scrubTickStep(0f))
        assertEquals(0, scrubTickStep(0.049f))
        assertEquals(1, scrubTickStep(0.051f))
    }

    @Test
    fun changesBandAtEveryTwentieth() {
        for (step in 1 until 20) {
            val boundary = step / 20f
            assertEquals(step - 1, scrubTickStep(boundary - 0.001f))
            assertEquals(step, scrubTickStep(boundary + 0.001f))
        }
    }

    @Test
    fun theEndStaysInTheLastBand() {
        assertEquals(19, scrubTickStep(1f))
    }

    @Test
    fun clampsInputOutsideTheBar() {
        assertEquals(0, scrubTickStep(-0.4f))
        assertEquals(19, scrubTickStep(1.4f))
        assertEquals(0, scrubTickStep(Float.NaN))
    }

    @Test
    fun honoursACustomBandCount() {
        assertEquals(0, scrubTickStep(0.49f, steps = 2))
        assertEquals(1, scrubTickStep(0.51f, steps = 2))
        assertEquals(0, scrubTickStep(1f, steps = 0))
    }
}
