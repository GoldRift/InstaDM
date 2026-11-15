package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.scrubFraction
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubFractionTest {
    @Test
    fun movesWithTheFinger() {
        assertEquals(0.5f, scrubFraction(0.25f, 50f, 200f), 0.0001f)
        assertEquals(0f, scrubFraction(0.25f, -50f, 200f), 0.0001f)
    }

    @Test
    fun clampsAtBothEnds() {
        assertEquals(1f, scrubFraction(0.9f, 500f, 200f), 0.0001f)
        assertEquals(0f, scrubFraction(0.1f, -500f, 200f), 0.0001f)
        assertEquals(1f, scrubFraction(1.4f, 0f, 200f), 0.0001f)
    }

    @Test
    fun zeroWidthKeepsTheStart() {
        assertEquals(0.3f, scrubFraction(0.3f, 50f, 0f), 0.0001f)
    }
}
