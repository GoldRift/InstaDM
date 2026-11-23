package com.goldrift.instadm

import com.goldrift.instadm.data.coveredReadWatermark
import org.junit.Assert.assertEquals
import org.junit.Test

class CoveredReadWatermarkTest {

    @Test
    fun noWatermarksStayAtZero() {
        assertEquals(0L, coveredReadWatermark(0L, 0L))
    }

    @Test
    fun anActivityWatermarkCoversAVisibleRead() {
        assertEquals(5_000L, coveredReadWatermark(0L, 5_000L))
    }

    @Test
    fun aVisibleWatermarkStandsWhenThereIsNoActivity() {
        assertEquals(3_000L, coveredReadWatermark(3_000L, 0L))
    }

    @Test
    fun theNewerOfTwoWatermarksWins() {
        assertEquals(5_000L, coveredReadWatermark(3_000L, 5_000L))
        assertEquals(5_000L, coveredReadWatermark(5_000L, 3_000L))
    }
}
