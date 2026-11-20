package com.goldrift.instadm

import com.goldrift.instadm.ui.inbox.PullThresholdStep
import com.goldrift.instadm.ui.inbox.stepPullRefreshThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRefreshThresholdTest {
    @Test
    fun pullingBelowThresholdStaysDisarmedWithoutHaptic() {
        assertEquals(
            PullThresholdStep(armed = false, fireHaptic = false),
            stepPullRefreshThreshold(armed = false, distanceFraction = 0f, refreshing = false),
        )
        assertEquals(
            PullThresholdStep(armed = false, fireHaptic = false),
            stepPullRefreshThreshold(armed = false, distanceFraction = 0.99f, refreshing = false),
        )
    }

    @Test
    fun firstCrossingArmsAndFiresHapticOnce() {
        val crossed = stepPullRefreshThreshold(
            armed = false,
            distanceFraction = 1f,
            refreshing = false,
        )
        assertTrue(crossed.armed)
        assertTrue(crossed.fireHaptic)

        // Continued pulling beyond 1f stays armed without firing again.
        val overshot = stepPullRefreshThreshold(
            armed = crossed.armed,
            distanceFraction = 1.2f,
            refreshing = false,
        )
        assertTrue(overshot.armed)
        assertFalse(overshot.fireHaptic)
    }

    @Test
    fun hysteresisSuppressesBoundaryJitterAndReArmsAfterReset() {
        var step = stepPullRefreshThreshold(armed = false, distanceFraction = 1f, refreshing = false)
        assertTrue(step.fireHaptic)

        // Slight wobble below 1f but above 0.85f keeps it armed and quiet.
        step = stepPullRefreshThreshold(armed = step.armed, distanceFraction = 0.92f, refreshing = false)
        assertTrue(step.armed)
        assertFalse(step.fireHaptic)

        step = stepPullRefreshThreshold(armed = step.armed, distanceFraction = 1.04f, refreshing = false)
        assertTrue(step.armed)
        assertFalse(step.fireHaptic)

        // Dropping to resetFraction disarms without firing, so the next pull fires again.
        step = stepPullRefreshThreshold(armed = step.armed, distanceFraction = 0.85f, refreshing = false)
        assertFalse(step.armed)
        assertFalse(step.fireHaptic)

        step = stepPullRefreshThreshold(armed = step.armed, distanceFraction = 1.02f, refreshing = false)
        assertTrue(step.armed)
        assertTrue(step.fireHaptic)
    }

    @Test
    fun settlingAfterRefreshNeverPhantomFires() {
        var step = stepPullRefreshThreshold(armed = false, distanceFraction = 0f, refreshing = true)
        assertEquals(PullThresholdStep(armed = true, fireHaptic = false), step)

        for (fraction in listOf(1.0f, 0.9f, 0.85f, 0f)) {
            step = stepPullRefreshThreshold(
                armed = step.armed,
                distanceFraction = fraction,
                refreshing = false,
            )
            assertFalse("fraction=$fraction should not phantom-fire", step.fireHaptic)
        }
        assertFalse(step.armed)
    }

    @Test
    fun animatingIndicatorNeverFires() {
        assertEquals(
            PullThresholdStep(armed = false, fireHaptic = false),
            stepPullRefreshThreshold(
                armed = false,
                distanceFraction = 1.1f,
                refreshing = false,
                isAnimating = true,
            ),
        )
        assertEquals(
            PullThresholdStep(armed = true, fireHaptic = false),
            stepPullRefreshThreshold(
                armed = true,
                distanceFraction = 0.95f,
                refreshing = false,
                isAnimating = true,
            ),
        )
        assertEquals(
            PullThresholdStep(armed = false, fireHaptic = false),
            stepPullRefreshThreshold(
                armed = true,
                distanceFraction = 0.8f,
                refreshing = false,
                isAnimating = true,
            ),
        )
    }

    @Test
    fun nanPreservesArmedStateWithoutFiring() {
        assertEquals(
            PullThresholdStep(armed = false, fireHaptic = false),
            stepPullRefreshThreshold(armed = false, distanceFraction = Float.NaN, refreshing = false),
        )
        assertEquals(
            PullThresholdStep(armed = true, fireHaptic = false),
            stepPullRefreshThreshold(armed = true, distanceFraction = Float.NaN, refreshing = false),
        )
    }
}
