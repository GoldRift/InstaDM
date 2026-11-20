package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.waveformBarAlpha
import com.goldrift.instadm.ui.chat.waveformBarHeightScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformBarStyleTest {
    @Test
    fun playedAndUnplayedBarsUseEndpointAlphas() {
        val count = 10
        assertEquals(1f, waveformBarAlpha(index = 0, count = count, played = 0.35f), 0.0001f)
        assertEquals(1f, waveformBarAlpha(index = 2, count = count, played = 0.35f), 0.0001f)
        assertEquals(0.32f, waveformBarAlpha(index = 4, count = count, played = 0.35f), 0.0001f)
        assertEquals(0.32f, waveformBarAlpha(index = 9, count = count, played = 0.35f), 0.0001f)
    }

    @Test
    fun crossingBarRampsAlphaUpAcrossPlayhead() {
        val count = 10
        // Bar 3 spans [0.3, 0.4).
        val early = waveformBarAlpha(index = 3, count = count, played = 0.31f)
        val middle = waveformBarAlpha(index = 3, count = count, played = 0.35f)
        val late = waveformBarAlpha(index = 3, count = count, played = 0.39f)

        assertTrue("early ($early) should be above 0.32f", early > 0.32f)
        assertEquals(0.66f, middle, 0.001f)
        assertTrue("late ($late) should exceed middle ($middle)", late > middle)
        assertTrue("late ($late) should stay below 1f", late < 1f)
    }

    @Test
    fun alphaClampsBoundaryAndInvalidInputs() {
        for (i in 0 until 8) {
            assertEquals(0.32f, waveformBarAlpha(i, count = 8, played = 0f), 0.0001f)
            assertEquals(1f, waveformBarAlpha(i, count = 8, played = 1f), 0.0001f)
            assertEquals(0.32f, waveformBarAlpha(i, count = 8, played = -0.5f), 0.0001f)
            assertEquals(1f, waveformBarAlpha(i, count = 8, played = 1.5f), 0.0001f)
        }
        assertEquals(0.32f, waveformBarAlpha(0, count = 0, played = 0.5f), 0.0001f)
        assertEquals(0.32f, waveformBarAlpha(0, count = -3, played = 0.5f), 0.0001f)
        assertEquals(0.32f, waveformBarAlpha(0, count = 8, played = Float.NaN), 0.0001f)
    }

    @Test
    fun idlePlaybackKeepsAllBarsAtBaseHeight() {
        for (i in 0 until 10) {
            assertEquals(1f, waveformBarHeightScale(i, count = 10, played = 0f, scrubbing = false), 0.0001f)
            assertEquals(1f, waveformBarHeightScale(i, count = 10, played = 1f, scrubbing = false), 0.0001f)
        }
    }

    @Test
    fun activePlaybackEmphasizesOnlyTheCurrentBar() {
        val count = 10
        assertEquals(1.1f, waveformBarHeightScale(2, count, played = 0.2f, scrubbing = false), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(1, count, played = 0.2f, scrubbing = false), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(3, count, played = 0.2f, scrubbing = false), 0.0001f)

        assertEquals(1.1f, waveformBarHeightScale(4, count, played = 0.47f, scrubbing = false), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(5, count, played = 0.47f, scrubbing = false), 0.0001f)
    }

    @Test
    fun scrubbingEmphasizesActiveBarAndImmediateNeighbors() {
        val count = 10
        assertEquals(1.22f, waveformBarHeightScale(5, count, played = 0.55f, scrubbing = true), 0.0001f)
        assertEquals(1.1f, waveformBarHeightScale(4, count, played = 0.55f, scrubbing = true), 0.0001f)
        assertEquals(1.1f, waveformBarHeightScale(6, count, played = 0.55f, scrubbing = true), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(3, count, played = 0.55f, scrubbing = true), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(7, count, played = 0.55f, scrubbing = true), 0.0001f)

        // Endpoints still emphasize when actively scrubbing.
        assertEquals(1.22f, waveformBarHeightScale(0, count, played = 0f, scrubbing = true), 0.0001f)
        assertEquals(1.1f, waveformBarHeightScale(1, count, played = 0f, scrubbing = true), 0.0001f)
        assertEquals(1.22f, waveformBarHeightScale(9, count, played = 1f, scrubbing = true), 0.0001f)
        assertEquals(1.1f, waveformBarHeightScale(8, count, played = 1f, scrubbing = true), 0.0001f)
    }

    @Test
    fun heightScaleHandlesInvalidInputsSafely() {
        assertEquals(1f, waveformBarHeightScale(0, count = 0, played = 0.5f, scrubbing = true), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(0, count = -1, played = 0.5f, scrubbing = false), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(0, count = 10, played = Float.NaN, scrubbing = true), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(-1, count = 10, played = 0.5f, scrubbing = true), 0.0001f)
        assertEquals(1f, waveformBarHeightScale(10, count = 10, played = 0.5f, scrubbing = true), 0.0001f)
    }
}
