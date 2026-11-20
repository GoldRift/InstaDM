package com.goldrift.instadm

import com.goldrift.instadm.data.DEFAULT_WAVEFORM_BARS
import com.goldrift.instadm.data.WindowedRmsCollector
import com.goldrift.instadm.data.buildWaveformBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AudioWaveformExtractorTest {
    @Test
    fun rmsCollectorReturnsZeroForSilenceAndEmptyInput() {
        val empty = WindowedRmsCollector(windowSamples = 256)
        assertTrue(empty.finish().isEmpty())

        val silence = WindowedRmsCollector(windowSamples = 256)
        silence.addPcm16(ShortArray(512))
        val chunks = silence.finish()
        assertEquals(2, chunks.size)
        chunks.forEach { assertEquals(0f, it, 0.0001f) }
    }

    @Test
    fun rmsCollectorHandlesFullScalePcm16WithoutMinValueOverflow() {
        val collector = WindowedRmsCollector(windowSamples = 4)
        collector.addPcm16(
            shortArrayOf(
                Short.MIN_VALUE,
                Short.MIN_VALUE,
                Short.MIN_VALUE,
                Short.MIN_VALUE,
                Short.MAX_VALUE,
                Short.MAX_VALUE,
                Short.MAX_VALUE,
                Short.MAX_VALUE,
            ),
        )
        val chunks = collector.finish()
        assertEquals(2, chunks.size)
        assertEquals(1f, chunks[0], 0.0001f)
        assertEquals(1f, chunks[1], 0.001f)
    }

    @Test
    fun rmsCollectorFlushesTrailingPartialWindowAndRespectsCount() {
        val collector = WindowedRmsCollector(windowSamples = 4)
        val buffer = shortArrayOf(16_384, 16_384, 16_384, 16_384, 32_767, 0, 32_767, 32_767)
        // Only consume the first 5 samples: one full window of 16384 and one trailing sample of 32767.
        collector.addPcm16(buffer, count = 5)
        val chunks = collector.finish()
        assertEquals(2, chunks.size)
        assertEquals(0.5f, chunks[0], 0.001f)
        assertEquals(1f, chunks[1], 0.001f)
    }

    @Test
    fun rmsCollectorClampsFloatSamplesAndIgnoresNonFinite() {
        val collector = WindowedRmsCollector(windowSamples = 4)
        collector.addPcmFloat(floatArrayOf(2.5f, -3.0f, Float.NaN, Float.POSITIVE_INFINITY))
        val chunks = collector.finish()
        assertEquals(1, chunks.size)
        // Clamped to [1, -1, 0, 0] -> sqrt(2 / 4) = sqrt(0.5)
        assertEquals(kotlin.math.sqrt(0.5).toFloat(), chunks.single(), 0.001f)
    }

    @Test
    fun rmsCollectorRespectsMaxChunksCap() {
        val collector = WindowedRmsCollector(windowSamples = 2, maxChunks = 3)
        collector.addPcm16(ShortArray(12) { 16_384 })
        val chunks = collector.finish()
        assertEquals(3, chunks.size)
    }

    @Test
    fun buildWaveformBarsHandlesEmptyAndNonPositiveTargets() {
        assertTrue(buildWaveformBars(emptyList(), targetBars = DEFAULT_WAVEFORM_BARS).isEmpty())
        assertTrue(buildWaveformBars(listOf(0.5f, 0.8f), targetBars = 0).isEmpty())
        assertTrue(buildWaveformBars(listOf(0.5f, 0.8f), targetBars = -4).isEmpty())
    }

    @Test
    fun buildWaveformBarsPreservesShortClipsAndZeroesSilence() {
        val shortClip = listOf(0.1f, 0.4f, 0.2f)
        val shortBars = buildWaveformBars(shortClip, targetBars = 10)
        assertEquals(3, shortBars.size)
        assertEquals(1f, shortBars[1], 0.001f)
        assertTrue(shortBars[1] > shortBars[2] && shortBars[2] > shortBars[0])

        val silent = buildWaveformBars(listOf(0f, 1e-5f, 5e-5f), targetBars = 8)
        assertEquals(3, silent.size)
        silent.forEach { assertEquals(0f, it, 0.0001f) }
    }

    @Test
    fun buildWaveformBarsDownsamplesWithBucketPeak() {
        val chunks = MutableList(100) { 0.2f }
        // Place one speech peak inside bucket 2 (indices 5..7 when mapping 100 -> 40 bars).
        chunks[6] = 0.8f
        val bars = buildWaveformBars(chunks, targetBars = 40)
        assertEquals(40, bars.size)
        assertEquals(1f, bars[2], 0.001f)
        assertTrue(bars[0] < bars[2])
    }

    @Test
    fun buildWaveformBarsResistsSingleSpikeAndKeepsQuietContrast() {
        val chunks = buildList {
            repeat(8) { add(0.05f) } // quiet pause
            repeat(39) { add(0.4f) } // normal speech
            add(1.0f) // single transient spike
        }
        val bars = buildWaveformBars(chunks, targetBars = 48)
        assertEquals(48, bars.size)
        val quiet = bars[0]
        val speech = bars[10]
        val spike = bars[47]

        // 95th percentile is 0.4f, floor is peak * 0.5 = 0.5f -> speech = (0.4 / 0.5)^0.65.
        assertEquals((0.4f / 0.5f).pow(0.65f), speech, 0.01f)
        assertEquals(1f, spike, 0.0001f)
        assertTrue("speech ($speech) should stay prominent above 0.8", speech > 0.8f)
        assertTrue("quiet ($quiet) should stay well below speech ($speech)", quiet < speech * 0.4f)
    }

    @Test
    fun buildWaveformBarsSanitizesNaNAndNegativeInputs() {
        val bars = buildWaveformBars(
            chunkLevels = listOf(Float.NaN, -0.4f, 0.3f, Float.POSITIVE_INFINITY, 0.6f),
            targetBars = 10,
        )
        assertEquals(5, bars.size)
        bars.forEach { value ->
            assertFalse(value.isNaN())
            assertTrue(value in 0f..1f)
        }
        assertEquals(0f, bars[0], 0.0001f)
        assertEquals(0f, bars[1], 0.0001f)
        assertEquals(1f, bars[4], 0.001f)
    }
}
