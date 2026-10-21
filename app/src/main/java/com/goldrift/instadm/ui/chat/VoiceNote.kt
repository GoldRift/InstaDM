package com.goldrift.instadm.ui.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.goldrift.instadm.R
import com.goldrift.instadm.data.MessageMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Plays one voice note at a time through the speaker. Compose state lets every
 * bubble react to the shared position; starting another note stops the first.
 */
@Stable
class VoicePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    var activeId by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
    }

    /** Starts [file] for [messageId], or resumes it when it is already active. */
    fun play(messageId: String, file: File, fallbackDurationMs: Int) {
        val current = player
        if (activeId == messageId && current != null) {
            if (!playing) {
                if (!requestFocus()) return
                val resumeAt = if (positionMs >= durationMs - 50) 0 else positionMs
                runCatching { current.seekTo(resumeAt) }
                if (runCatching { current.start() }.isSuccess) {
                    playing = true
                    positionMs = resumeAt
                    startTicker()
                }
            }
            return
        }

        stopInternal()
        if (!requestFocus()) return
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.setOnCompletionListener {
                playing = false
                positionMs = durationMs
            }
            mediaPlayer.start()
        } catch (error: Exception) {
            runCatching { mediaPlayer.release() }
            abandonFocus()
            return
        }
        player = mediaPlayer
        activeId = messageId
        durationMs = mediaPlayer.duration.takeIf { it > 0 } ?: fallbackDurationMs
        positionMs = 0
        playing = true
        startTicker()
    }

    fun pause() {
        val current = player ?: return
        if (!playing) return
        runCatching { current.pause() }
        positionMs = runCatching { current.currentPosition }.getOrDefault(positionMs)
        playing = false
        ticker?.cancel()
        ticker = null
    }

    /** Seeks the active note to [fraction] (0..1). */
    fun seek(fraction: Float) {
        val current = player ?: return
        val total = durationMs.takeIf { it > 0 } ?: current.duration
        if (total <= 0) return
        val target = (total * fraction.coerceIn(0f, 1f)).roundToInt()
        runCatching { current.seekTo(target) }
        positionMs = target
    }

    fun progressFor(messageId: String): Float {
        if (activeId != messageId || durationMs <= 0) return 0f
        return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun isPlaying(messageId: String): Boolean = activeId == messageId && playing

    fun stop() {
        stopInternal()
    }

    fun release() {
        stopInternal()
        scope.cancel()
    }

    private fun stopInternal() {
        ticker?.cancel()
        ticker = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        activeId = null
        playing = false
        positionMs = 0
        durationMs = 0
        abandonFocus()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val current = player ?: break
                positionMs = runCatching { current.currentPosition }.getOrDefault(positionMs)
                if (!playing) break
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun requestFocus(): Boolean = try {
        val manager = audioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    } catch (error: Exception) {
        false
    }

    private fun abandonFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(focusListener)
            }
        }
        focusRequest = null
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 80L
    }
}

/**
 * Inline voice note: play/pause, waveform with progress, duration, and
 * tap-to-seek. The file is downloaded on first play; outgoing notes play from
 * the local copy the outbox already wrote.
 */
@Composable
fun VoiceNoteBubble(
    media: MessageMedia,
    threadId: String,
    messageId: String,
    player: VoicePlayer,
    loadMedia: MediaLoader?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var file by remember(messageId, media.partIndex, media.localPath) {
        mutableStateOf(localVoiceFile(media))
    }
    var loading by remember { mutableStateOf(false) }
    var failed by remember(messageId, media.partIndex) { mutableStateOf(false) }

    val isActive = player.activeId == messageId
    val isPlaying = player.isPlaying(messageId)
    val progress = player.progressFor(messageId)
    val durationMs = if (isActive && player.durationMs > 0) player.durationMs else media.durationMs

    fun togglePlayback() {
        val ready = file?.takeIf { it.isFile }
        if (ready != null) {
            player.play(messageId, ready, media.durationMs)
            return
        }
        val loader = loadMedia ?: return
        if (loading) return
        loading = true
        failed = false
        scope.launch {
            val downloaded = runCatching {
                loader(threadId, messageId, media.partIndex, media.type)
            }.getOrNull()
            loading = false
            if (downloaded != null) {
                file = downloaded
                player.play(messageId, downloaded, media.durationMs)
            } else {
                failed = true
            }
        }
    }

    val longClickLabel = stringResource(R.string.chat_message_actions)
    val contentColor = LocalContentColor.current
    Row(
        modifier = modifier
            .widthIn(min = 200.dp, max = 270.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(contentColor.copy(alpha = 0.07f))
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = {},
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )

                failed -> Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .pointerInput(messageId, media.partIndex) {
                            detectTapGestures { togglePlayback() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.chat_voice_error),
                        tint = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }

                else -> Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .pointerInput(isPlaying, file) {
                            detectTapGestures { togglePlayback() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.chat_voice_pause else R.string.chat_voice_play,
                        ),
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        WaveformBars(
            amplitudes = media.waveform,
            progress = progress,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            onSeek = { fraction ->
                if (isActive) {
                    player.seek(fraction)
                } else {
                    togglePlayback()
                }
            },
            onLongClick = onLongClick,
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = formatMediaDuration(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

/** Waveform bars; a uniform low bar is drawn when the note has no waveform. */
@Composable
fun WaveformBars(
    amplitudes: List<Float>,
    progress: Float,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val contentColor = LocalContentColor.current
    val gestureModifier = if (onSeek != null) {
        Modifier.pointerInput(amplitudes, onSeek, onLongClick) {
            detectTapGestures(
                onTap = { offset ->
                    if (size.width > 0) onSeek(offset.x / size.width)
                },
                onLongPress = { onLongClick?.invoke() },
            )
        }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.then(gestureModifier)) {
        val barWidth = 2.5.dp.toPx()
        val gap = 2.dp.toPx()
        val count = ((size.width + gap) / (barWidth + gap)).toInt().coerceIn(1, 64)
        val step = if (amplitudes.isEmpty()) 0f else amplitudes.size.toFloat() / count
        val played = progress.coerceIn(0f, 1f)
        for (index in 0 until count) {
            val amplitude = if (amplitudes.isEmpty()) {
                0.4f
            } else {
                amplitudes.getOrElse((index * step).toInt().coerceIn(0, amplitudes.lastIndex)) {
                    0.4f
                }
            }
            val height = (size.height * (0.12f + 0.88f * amplitude.coerceIn(0f, 1f)))
                .coerceIn(3.dp.toPx(), size.height)
            val left = index * (barWidth + gap)
            val top = (size.height - height) / 2f
            val playedBar = (index + 1).toFloat() / count <= played
            drawRoundRect(
                color = contentColor.copy(alpha = if (playedBar) 1f else 0.32f),
                topLeft = Offset(left, top),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/** Live bars while recording, no gestures. */
@Composable
fun RecordingWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.error
    Canvas(modifier = modifier) {
        val barWidth = 2.5.dp.toPx()
        val gap = 2.dp.toPx()
        val count = ((size.width + gap) / (barWidth + gap)).toInt().coerceIn(1, 48)
        val visible = amplitudes.takeLast(count)
        for (index in 0 until count) {
            val amplitude = visible.getOrElse(index) { 0.25f }
            val height = (size.height * (0.14f + 0.86f * amplitude.coerceIn(0f, 1f)))
                .coerceIn(3.dp.toPx(), size.height)
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = contentColor.copy(alpha = if (index < visible.size) 0.95f else 0.25f),
                topLeft = Offset(left, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/** Playable local file for an outgoing or already-downloaded note. */
private fun localVoiceFile(media: MessageMedia): File? {
    if (media.localPath.isNotBlank()) {
        val file = File(media.localPath)
        if (file.isFile) return file
    }
    val url = media.url
    if (url.startsWith("file:")) {
        val path = runCatching { url.toUri().path }.getOrNull() ?: return null
        return File(path).takeIf { it.isFile }
    }
    return null
}
