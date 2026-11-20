package com.goldrift.instadm.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.goldrift.instadm.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A finished recording, ready to hand to [Outbox.enqueueVoiceNote]. */
data class RecordedVoice(
    val file: File,
    val fileName: String,
    val mimeType: String,
    val durationMs: Int,
    val waveform: List<Float>,
)

enum class VoiceRecordingState { Idle, Recording, Paused }

/**
 * Records an Instagram-style voice note to an AAC/MPEG-4 file.
 *
 * There is no duration limit: recording continues until the user stops it or
 * the file reaches the same byte cap the uploader enforces. Amplitudes are
 * sampled at [BARS_PER_SECOND] Hz, the rate Instagram expects for voice-note
 * waveforms, and normalized to 0..1.
 */
@Stable
class VoiceRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var sessionGeneration = 0L
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var ticker: Job? = null
    private var segmentStartedAt = 0L
    private var elapsedBeforePause = 0L

    private val _waveform = mutableStateListOf<Float>()

    var state by mutableStateOf(VoiceRecordingState.Idle)
        private set

    /** Active recording time, excluding pauses. */
    var elapsedMs by mutableLongStateOf(0L)
        private set

    /** Amplitudes in 0..1, one per [SAMPLE_INTERVAL_MS] of active recording. */
    val waveform: List<Float> = _waveform

    /** Set when recording stopped on its own, e.g. for the byte cap. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        errorMessage = null
    }

    val isRecording: Boolean
        get() = state != VoiceRecordingState.Idle

    /** Starts recording off the main thread so hardware prepare never drops frames. */
    suspend fun startAsync(): Boolean {
        if (isRecording) return true
        errorMessage = null
        _waveform.clear()
        elapsedMs = 0
        elapsedBeforePause = 0
        val generation = ++sessionGeneration
        state = VoiceRecordingState.Recording

        val prepared = withContext(Dispatchers.IO) { prepareAndStartHardware() }
        if (prepared == null) {
            if (generation == sessionGeneration) {
                state = VoiceRecordingState.Idle
                errorMessage = appContext.getString(R.string.chat_recording_failed)
            }
            return false
        }
        val (active, file) = prepared
        if (generation != sessionGeneration) {
            teardownAsync(active, file, stopRecorder = true)
            return false
        }
        recorder = active
        outputFile = file
        segmentStartedAt = SystemClock.elapsedRealtime()
        startTicker(file)
        return true
    }

    /** Synchronous variant kept for instrumentation tests. */
    fun start(): Boolean {
        if (isRecording) return true
        errorMessage = null
        _waveform.clear()
        elapsedMs = 0
        elapsedBeforePause = 0
        sessionGeneration++

        val prepared = prepareAndStartHardware()
        if (prepared == null) {
            state = VoiceRecordingState.Idle
            errorMessage = appContext.getString(R.string.chat_recording_failed)
            return false
        }
        val (active, file) = prepared
        recorder = active
        outputFile = file
        state = VoiceRecordingState.Recording
        segmentStartedAt = SystemClock.elapsedRealtime()
        startTicker(file)
        return true
    }

    fun pause() {
        if (state != VoiceRecordingState.Recording) return
        val active = recorder ?: return
        runCatching { active.pause() }.onSuccess {
            elapsedBeforePause = activeElapsedMs()
            state = VoiceRecordingState.Paused
        }
    }

    fun resume() {
        if (state != VoiceRecordingState.Paused) return
        val active = recorder ?: return
        runCatching { active.resume() }.onSuccess {
            segmentStartedAt = SystemClock.elapsedRealtime()
            state = VoiceRecordingState.Recording
        }
    }

    /** Stops and finalizes the recording off the main thread. */
    suspend fun stopAsync(): RecordedVoice? {
        if (!isRecording) return null
        sessionGeneration++
        val active = recorder
        val file = outputFile
        val duration = activeElapsedMs().toInt()
        val amplitudes = _waveform.toList()
        resetUiState()
        if (active == null || file == null) return null

        return withContext(Dispatchers.IO) {
            finalizeRecording(active, file, duration, amplitudes)
        }
    }

    /**
     * Stops and returns the recording, or null when it was too short or empty.
     * The caller owns the returned file until the outbox copies it.
     */
    fun stop(): RecordedVoice? {
        if (!isRecording) return null
        sessionGeneration++
        val active = recorder
        val file = outputFile
        val duration = activeElapsedMs().toInt()
        val amplitudes = _waveform.toList()
        resetUiState()
        if (active == null || file == null) return null
        return finalizeRecording(active, file, duration, amplitudes)
    }

    /** Stops and deletes the current recording without blocking the UI thread. */
    fun cancel() {
        sessionGeneration++
        val active = recorder
        val file = outputFile
        val wasRecording = isRecording
        resetUiState()
        if (active != null || file != null) {
            teardownAsync(active, file, stopRecorder = wasRecording)
        }
    }

    /** Releases the recorder when the screen goes away. */
    fun release() {
        cancel()
        scope.cancel()
    }

    private fun resetUiState() {
        recorder = null
        outputFile = null
        ticker?.cancel()
        ticker = null
        state = VoiceRecordingState.Idle
        elapsedMs = 0
        elapsedBeforePause = 0
        segmentStartedAt = 0L
        _waveform.clear()
    }

    @Suppress("DEPRECATION")
    private fun prepareAndStartHardware(): Pair<MediaRecorder, File>? {
        val directory = File(appContext.cacheDir, RECORDING_DIR).apply { mkdirs() }
        prune(directory)
        val file = File(directory, "voice-${UUID.randomUUID()}.m4a")

        val active = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            MediaRecorder()
        }
        return try {
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setAudioEncodingBitRate(AUDIO_BIT_RATE)
            active.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            active.setAudioChannels(1)
            active.setOutputFile(file.absolutePath)
            active.prepare()
            active.start()
            active to file
        } catch (error: Exception) {
            runCatching { active.reset() }
            runCatching { active.release() }
            runCatching { file.delete() }
            null
        }
    }

    private fun finalizeRecording(
        active: MediaRecorder,
        file: File,
        duration: Int,
        amplitudes: List<Float>,
    ): RecordedVoice? {
        val stoppedCleanly = try {
            active.stop()
            true
        } catch (error: RuntimeException) {
            false
        } finally {
            runCatching { active.reset() }
            runCatching { active.release() }
        }

        if (!stoppedCleanly || !file.isFile || file.length() <= 0) {
            runCatching { file.delete() }
            return null
        }
        if (duration < MIN_VOICE_MS) {
            runCatching { file.delete() }
            return null
        }
        return RecordedVoice(
            file = file,
            fileName = file.name.ifBlank { "voice.m4a" },
            mimeType = "audio/mp4",
            durationMs = duration,
            waveform = amplitudes,
        )
    }

    private fun startTicker(file: File) {
        ticker?.cancel()
        val maxBytes = MediaPolicy.MAX_MEDIA_BYTES
        ticker = scope.launch {
            var nextSampleAt = SystemClock.elapsedRealtime() + SAMPLE_INTERVAL_MS
            var ticksSinceSizeCheck = 0
            while (isActive && isRecording) {
                if (state == VoiceRecordingState.Recording) {
                    elapsedMs = activeElapsedMs()
                }
                if (SystemClock.elapsedRealtime() >= nextSampleAt) {
                    nextSampleAt = SystemClock.elapsedRealtime() + SAMPLE_INTERVAL_MS
                    if (state == VoiceRecordingState.Recording) sampleAmplitude()
                }
                if (++ticksSinceSizeCheck >= SIZE_CHECK_TICKS) {
                    ticksSinceSizeCheck = 0
                    val size = withContext(Dispatchers.IO) { file.length() }
                    if (size >= maxBytes) {
                        stopForLimit(file, size)
                        break
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    private fun sampleAmplitude() {
        if (_waveform.size >= MAX_WAVEFORM_SAMPLES) return
        val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        val normalized = (amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)
        _waveform.add(normalized)
    }

    private fun stopForLimit(file: File, size: Long) {
        sessionGeneration++
        val active = recorder
        resetUiState()
        teardownAsync(active, file, stopRecorder = true)
        errorMessage = appContext.getString(
            R.string.chat_media_too_large,
            formatMediaBytes(size),
            formatMediaBytes(MediaPolicy.MAX_MEDIA_BYTES),
        )
    }

    private fun activeElapsedMs(): Long {
        val base = elapsedBeforePause
        if (state == VoiceRecordingState.Recording && segmentStartedAt > 0) {
            return base + (SystemClock.elapsedRealtime() - segmentStartedAt)
        }
        return base
    }

    private fun prune(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_RECORDING_AGE_MS
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private companion object {
        // Detached IO scope so hardware teardown finishes even if the screen's
        // VoiceRecorder scope is cancelled immediately on dispose.
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun teardownAsync(active: MediaRecorder?, file: File?, stopRecorder: Boolean) {
            cleanupScope.launch {
                if (stopRecorder) {
                    runCatching { active?.stop() }
                }
                runCatching { active?.reset() }
                runCatching { active?.release() }
                file?.let { runCatching { it.delete() } }
            }
        }

        const val RECORDING_DIR = "voice"
        const val AUDIO_BIT_RATE = 64_000
        const val AUDIO_SAMPLE_RATE = 44_100
        const val BARS_PER_SECOND = 9
        const val SAMPLE_INTERVAL_MS = 1000L / BARS_PER_SECOND
        const val TICK_MS = 50L
        const val SIZE_CHECK_TICKS = 10
        const val MIN_VOICE_MS = 300
        const val MAX_AMPLITUDE = 32_767f
        const val MAX_WAVEFORM_SAMPLES = 2_000
        const val MAX_RECORDING_AGE_MS = 24L * 60 * 60 * 1000
    }
}
