package com.goldrift.instadm.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var ticker: Job? = null
    private var segmentStartedAt = 0L
    private var elapsedBeforePause = 0L

    var state by mutableStateOf(VoiceRecordingState.Idle)
        private set

    /** Active recording time, excluding pauses. */
    var elapsedMs by mutableLongStateOf(0L)
        private set

    /** Amplitudes in 0..1, one per [SAMPLE_INTERVAL_MS] of active recording. */
    var waveform by mutableStateOf<List<Float>>(emptyList())
        private set

    /** Set when recording stopped on its own, e.g. for the byte cap. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        errorMessage = null
    }

    val isRecording: Boolean
        get() = state != VoiceRecordingState.Idle

    @Suppress("DEPRECATION")
    fun start(): Boolean {
        if (isRecording) return true
        errorMessage = null
        waveform = emptyList()
        elapsedMs = 0
        elapsedBeforePause = 0

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
            recorder = active
            outputFile = file
            state = VoiceRecordingState.Recording
            segmentStartedAt = SystemClock.elapsedRealtime()
            startTicker(file)
            true
        } catch (error: Exception) {
            runCatching { active.reset() }
            runCatching { active.release() }
            runCatching { file.delete() }
            recorder = null
            outputFile = null
            state = VoiceRecordingState.Idle
            errorMessage = appContext.getString(R.string.chat_recording_failed)
            false
        }
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

    /**
     * Stops and returns the recording, or null when it was too short or empty.
     * The caller owns the returned file until the outbox copies it.
     */
    fun stop(): RecordedVoice? {
        if (!isRecording) return null
        val active = recorder ?: return null
        val file = outputFile
        val duration = activeElapsedMs().toInt()
        val amplitudes = waveform
        val mimeType = "audio/mp4"
        val fileName = file?.name ?: "voice.m4a"

        recorder = null
        outputFile = null
        ticker?.cancel()
        ticker = null
        state = VoiceRecordingState.Idle
        elapsedMs = 0
        waveform = emptyList()

        val stoppedCleanly = try {
            active.stop()
            true
        } catch (error: RuntimeException) {
            false
        } finally {
            runCatching { active.reset() }
            runCatching { active.release() }
        }

        if (!stoppedCleanly || file == null || !file.isFile || file.length() <= 0) {
            file?.let { runCatching { it.delete() } }
            return null
        }
        if (duration < MIN_VOICE_MS) {
            runCatching { file.delete() }
            return null
        }
        return RecordedVoice(
            file = file,
            fileName = fileName,
            mimeType = mimeType,
            durationMs = duration,
            waveform = amplitudes,
        )
    }

    /** Stops and deletes the current recording. */
    fun cancel() {
        val active = recorder
        val file = outputFile
        val wasRecording = isRecording
        recorder = null
        outputFile = null
        ticker?.cancel()
        ticker = null
        state = VoiceRecordingState.Idle
        elapsedMs = 0
        waveform = emptyList()
        if (wasRecording) {
            runCatching { active?.stop() }
        }
        runCatching { active?.reset() }
        runCatching { active?.release() }
        file?.let { runCatching { it.delete() } }
    }

    /** Releases the recorder when the screen goes away. */
    fun release() {
        cancel()
        scope.cancel()
    }

    private fun startTicker(file: File) {
        ticker?.cancel()
        val maxBytes = MediaPolicy.MAX_MEDIA_BYTES
        ticker = scope.launch {
            var nextSampleAt = SystemClock.elapsedRealtime() + SAMPLE_INTERVAL_MS
            while (isActive && isRecording) {
                if (state == VoiceRecordingState.Recording) {
                    elapsedMs = activeElapsedMs()
                }
                if (SystemClock.elapsedRealtime() >= nextSampleAt) {
                    nextSampleAt = SystemClock.elapsedRealtime() + SAMPLE_INTERVAL_MS
                    if (state == VoiceRecordingState.Recording) sampleAmplitude()
                }
                if (file.length() >= maxBytes) {
                    stopForLimit(file)
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    private fun sampleAmplitude() {
        if (waveform.size >= MAX_WAVEFORM_SAMPLES) return
        val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        val normalized = (amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)
        waveform = waveform + normalized
    }

    private fun stopForLimit(file: File) {
        val active = recorder
        val size = file.length()
        recorder = null
        outputFile = null
        ticker?.cancel()
        ticker = null
        state = VoiceRecordingState.Idle
        elapsedMs = 0
        waveform = emptyList()
        runCatching { active?.stop() }
        runCatching { active?.reset() }
        runCatching { active?.release() }
        runCatching { file.delete() }
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
        const val RECORDING_DIR = "voice"
        const val AUDIO_BIT_RATE = 64_000
        const val AUDIO_SAMPLE_RATE = 44_100
        const val BARS_PER_SECOND = 9
        const val SAMPLE_INTERVAL_MS = 1000L / BARS_PER_SECOND
        const val TICK_MS = 50L
        const val MIN_VOICE_MS = 300
        const val MAX_AMPLITUDE = 32_767f
        const val MAX_WAVEFORM_SAMPLES = 2_000
        const val MAX_RECORDING_AGE_MS = 24L * 60 * 60 * 1000
    }
}
