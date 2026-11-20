package com.goldrift.instadm.data

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val DEFAULT_WAVEFORM_BARS = 48

private const val TIMEOUT_US = 10_000L
private const val MAX_OUTPUT_BUFFERS = 20_000
private const val MAX_EMPTY_DRAIN_CYCLES = 50
private const val SILENCE_THRESHOLD = 1e-4f
private const val PERCEPTUAL_EXPONENT = 0.65

// Serialize decoder use so opening a voice-heavy thread never exhausts hardware codecs.
private val decodeMutex = Mutex()

/**
 * Decodes an audio file to PCM and summarizes its envelope into normalized
 * `0..1` bars suitable for [com.goldrift.instadm.ui.chat.WaveformBars].
 */
suspend fun extractAudioWaveform(
    file: File,
    targetBars: Int = DEFAULT_WAVEFORM_BARS,
): List<Float> = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() <= 0L || targetBars <= 0) return@withContext emptyList()
    decodeMutex.withLock {
        val coroutineCtx = kotlin.coroutines.coroutineContext
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var codecStarted = false
        try {
            extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = index
                    trackFormat = format
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) return@withLock emptyList()
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return@withLock emptyList()
            extractor.selectTrack(trackIndex)

            val sampleRate = trackFormat.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
                .coerceAtLeast(8_000)
            val channels = trackFormat.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1)
                .coerceIn(1, 8)
            val windowSamples = (sampleRate * channels / 60).coerceIn(128, 2048)
            var pcmEncoding = trackFormat.intOrDefault(
                MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(trackFormat, null, null, 0)
                start()
            }
            codecStarted = true

            val collector = WindowedRmsCollector(windowSamples = windowSamples)
            val bufferInfo = MediaCodec.BufferInfo()
            var shortScratch = ShortArray(2048)
            var floatScratch = FloatArray(1024)
            var inputDone = false
            var outputDone = false
            var outputBuffersSeen = 0
            var emptyDrainCycles = 0

            while (!outputDone && outputBuffersSeen < MAX_OUTPUT_BUFFERS) {
                coroutineCtx.ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = if (inputBuffer != null) {
                            extractor.readSampleData(inputBuffer, 0)
                        } else {
                            -1
                        }
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        pcmEncoding = codec.outputFormat.intOrDefault(
                            MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT,
                        )
                    }

                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone && ++emptyDrainCycles >= MAX_EMPTY_DRAIN_CYCLES) {
                            outputDone = true
                        }
                    }

                    outputIndex >= 0 -> {
                        emptyDrainCycles = 0
                        outputBuffersSeen++
                        val isCodecConfig =
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isCodecConfig && bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null) {
                                val start = bufferInfo.offset.coerceIn(0, outputBuffer.capacity())
                                val end = (start + bufferInfo.size).coerceIn(start, outputBuffer.capacity())
                                if (end > start) {
                                    val slice = outputBuffer.duplicate().apply {
                                        position(start)
                                        limit(end)
                                        order(ByteOrder.LITTLE_ENDIAN)
                                    }
                                    if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                        val floats = slice.asFloatBuffer()
                                        val count = floats.remaining()
                                        if (count > 0) {
                                            if (floatScratch.size < count) {
                                                floatScratch = FloatArray(count)
                                            }
                                            floats.get(floatScratch, 0, count)
                                            collector.addPcmFloat(floatScratch, count)
                                        }
                                    } else {
                                        val shorts = slice.asShortBuffer()
                                        val count = shorts.remaining()
                                        if (count > 0) {
                                            if (shortScratch.size < count) {
                                                shortScratch = ShortArray(count)
                                            }
                                            shorts.get(shortScratch, 0, count)
                                            collector.addPcm16(shortScratch, count)
                                        }
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            buildWaveformBars(collector.finish(), targetBars)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            emptyList()
        } finally {
            if (codecStarted) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }
}

private fun MediaFormat.intOrDefault(key: String, fallback: Int): Int =
    if (containsKey(key)) getInteger(key) else fallback

/**
 * Accumulates decoded PCM samples into fixed-size RMS chunks without retaining
 * the full uncompressed audio buffer in memory.
 */
internal class WindowedRmsCollector(
    windowSamples: Int,
    private val maxChunks: Int = 30_000,
) {
    private val windowSize = windowSamples.coerceAtLeast(1)
    private val chunks = ArrayList<Float>()
    private var sumSquares = 0.0
    private var sampleCount = 0

    fun addPcm16(samples: ShortArray, count: Int = samples.size) {
        val limit = count.coerceIn(0, samples.size)
        for (index in 0 until limit) {
            if (chunks.size >= maxChunks) return
            // Divide in Double so Short.MIN_VALUE (-32768) does not overflow.
            val normalized = samples[index].toDouble() / 32768.0
            sumSquares += normalized * normalized
            sampleCount++
            if (sampleCount >= windowSize) {
                flushWindow()
            }
        }
    }

    fun addPcmFloat(samples: FloatArray, count: Int = samples.size) {
        val limit = count.coerceIn(0, samples.size)
        for (index in 0 until limit) {
            if (chunks.size >= maxChunks) return
            val raw = samples[index]
            val normalized = if (raw.isFinite()) raw.toDouble().coerceIn(-1.0, 1.0) else 0.0
            sumSquares += normalized * normalized
            sampleCount++
            if (sampleCount >= windowSize) {
                flushWindow()
            }
        }
    }

    fun finish(): List<Float> {
        if (sampleCount > 0 && chunks.size < maxChunks) {
            flushWindow()
        }
        return chunks.toList()
    }

    private fun flushWindow() {
        val rms = sqrt(sumSquares / sampleCount).toFloat().coerceIn(0f, 1f)
        chunks += if (rms.isFinite()) rms else 0f
        sumSquares = 0.0
        sampleCount = 0
    }
}

/**
 * Downsamples [chunkLevels] into at most [targetBars] peak-per-bucket bars and
 * normalizes against the 95th percentile so isolated spikes do not crush speech.
 */
internal fun buildWaveformBars(
    chunkLevels: List<Float>,
    targetBars: Int = DEFAULT_WAVEFORM_BARS,
): List<Float> {
    if (chunkLevels.isEmpty() || targetBars <= 0) return emptyList()

    val buckets = if (chunkLevels.size <= targetBars) {
        chunkLevels.map { sanitizeLevel(it) }
    } else {
        ArrayList<Float>(targetBars).apply {
            val total = chunkLevels.size
            for (bar in 0 until targetBars) {
                val start = ((bar.toLong() * total) / targetBars).toInt()
                val end = (((bar + 1).toLong() * total) / targetBars).toInt()
                    .coerceAtLeast(start + 1)
                    .coerceAtMost(total)
                var bucketPeak = 0f
                for (index in start until end) {
                    val value = sanitizeLevel(chunkLevels[index])
                    if (value > bucketPeak) bucketPeak = value
                }
                add(bucketPeak)
            }
        }
    }

    val peak = buckets.maxOrNull() ?: 0f
    if (!peak.isFinite() || peak <= SILENCE_THRESHOLD) {
        return List(buckets.size) { 0f }
    }

    val sorted = buckets.sorted()
    val percentileIndex = (sorted.lastIndex * 0.95f).roundToInt().coerceIn(0, sorted.lastIndex)
    val reference = maxOf(sorted[percentileIndex], peak * 0.5f)
    if (reference <= 0f) return List(buckets.size) { 0f }

    return buckets.map { level ->
        val scaled = (level / reference).coerceIn(0f, 1f)
        scaled.toDouble().pow(PERCEPTUAL_EXPONENT).toFloat().coerceIn(0f, 1f)
    }
}

private fun sanitizeLevel(value: Float): Float =
    if (value.isFinite() && value > 0f) value else 0f
