package com.goldrift.instadm.ui.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.goldrift.instadm.R
import com.goldrift.instadm.service.VoicePlaybackNotifier
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.ui.components.segmentFrequentTick
import com.goldrift.instadm.ui.components.thresholdActivated
import com.goldrift.instadm.ui.components.toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/** What the mini player bar shows about the note currently on the player. */
data class NowPlaying(
    val threadId: String,
    val messageId: String,
    val title: String,
    val avatarUrl: String,
    val initials: String,
    val waveform: List<Float> = emptyList(),
)

/**
 * Plays one voice note at a time through the speaker. Compose state lets every
 * bubble react to the shared position; starting another note stops the first.
 * The player is process-scoped so a note can keep playing after its chat closes.
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

    /** The note on the player, with what a player bar needs to name it. */
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    /** True once the note has played out, so a finished bar can stay hidden. */
    val finished: Boolean
        get() = activeId != null && !playing && durationMs > 0 &&
            positionMs >= durationMs - COMPLETION_TOLERANCE_MS

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
    }

    /** Starts [file] for [note], or resumes it when it is already active. */
    fun play(
        note: NowPlaying,
        file: File,
        fallbackDurationMs: Int,
    ) {
        val messageId = note.messageId
        val current = player
        if (activeId == messageId && current != null) {
            nowPlaying = note
            resume()
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
                VoicePlaybackNotifier.hide(appContext)
            }
            mediaPlayer.start()
        } catch (error: Exception) {
            runCatching { mediaPlayer.release() }
            abandonFocus()
            return
        }
        player = mediaPlayer
        activeId = messageId
        nowPlaying = note
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
        VoicePlaybackNotifier.hide(appContext)
    }

    /** Play/pause for the bar, which has no bubble to supply the file again. */
    fun toggle() {
        if (playing) pause() else resume()
    }

    private fun resume() {
        val current = player ?: return
        if (playing) return
        if (!requestFocus()) return
        val resumeAt = if (positionMs >= durationMs - COMPLETION_TOLERANCE_MS) 0 else positionMs
        runCatching { current.seekTo(resumeAt) }
        if (runCatching { current.start() }.isSuccess) {
            playing = true
            positionMs = resumeAt
            startTicker()
        }
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

    private fun stopInternal() {
        ticker?.cancel()
        ticker = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        activeId = null
        nowPlaying = null
        playing = false
        VoicePlaybackNotifier.hide(appContext)
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
        const val COMPLETION_TOLERANCE_MS = 50
    }
}

/**
 * Play/pause artwork shared by the bubble, the mini player, and the recording
 * bar, so every voice control changes through the same motion. Callers own the
 * clickable semantics; the glyphs stay undescribed to avoid doubling it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VoicePlayPauseIcon(
    playing: Boolean,
    loading: Boolean,
    failed: Boolean,
    tint: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val state = when {
        loading -> VoiceControlState.Loading
        failed -> VoiceControlState.Failed
        playing -> VoiceControlState.Playing
        else -> VoiceControlState.Paused
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            (scaleIn(initialScale = 0.6f, animationSpec = motion.fastSpatialSpec()) +
                fadeIn(motion.fastEffectsSpec()))
                .togetherWith(
                    scaleOut(targetScale = 0.6f, animationSpec = motion.fastSpatialSpec()) +
                        fadeOut(motion.fastEffectsSpec()),
                )
        },
        contentAlignment = Alignment.Center,
        contentKey = { it },
        label = "voicePlayPause",
    ) { target ->
        when (target) {
            VoiceControlState.Loading -> LoadingIndicator(
                modifier = Modifier.size(24.dp),
                color = tint,
            )

            VoiceControlState.Failed -> Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )

            VoiceControlState.Playing -> Icon(
                imageVector = Icons.Rounded.Pause,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )

            VoiceControlState.Paused -> Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

private enum class VoiceControlState { Loading, Failed, Paused, Playing }

/**
 * Inline voice note: play/pause, waveform with progress, duration, and
 * tap-to-seek. The file is downloaded on first play; outgoing notes play from
 * the local copy the outbox already wrote.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceNoteBubble(
    media: MessageMedia,
    threadId: String,
    messageId: String,
    player: VoicePlayer,
    loadMedia: MediaLoader?,
    onLongClick: (() -> Unit)?,
    noteTitle: String,
    noteAvatarUrl: String,
    noteInitials: String,
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
    // Only the note being played counts down; an untouched one shows its length.
    val remainingMs = if (isActive) {
        (durationMs - player.positionMs).coerceAtLeast(0)
    } else {
        durationMs
    }
    val nowPlaying = NowPlaying(
        threadId = threadId,
        messageId = messageId,
        title = noteTitle,
        avatarUrl = noteAvatarUrl,
        initials = noteInitials,
        waveform = media.waveform,
    )
    val haptic = LocalHapticFeedback.current

    fun togglePlayback() {
        if (player.isPlaying(messageId)) {
            haptic.toggle(false)
            player.pause()
            return
        }
        val ready = file?.takeIf { it.isFile }
        if (ready != null) {
            haptic.toggle(true)
            player.play(nowPlaying, ready, media.durationMs)
            return
        }
        val loader = loadMedia ?: return
        if (loading) return
        haptic.toggle(true)
        loading = true
        failed = false
        scope.launch {
            val downloaded = runCatching {
                loader(threadId, messageId, media)
            }.getOrNull()
            loading = false
            if (downloaded != null) {
                file = downloaded
                player.play(nowPlaying, downloaded, media.durationMs)
            } else {
                failed = true
            }
        }
    }

    val longClickLabel = stringResource(R.string.chat_message_actions)
    val contentColor = LocalContentColor.current
    val motion = MaterialTheme.motionScheme
    // Playing squashes the round control into a squircle and deepens its fill.
    val controlCorner by animateFloatAsState(
        targetValue = if (isPlaying) 30f else 50f,
        animationSpec = motion.fastSpatialSpec(),
        label = "voiceControlCorner",
    )
    val controlFill by animateFloatAsState(
        targetValue = if (isPlaying) 0.26f else 0.16f,
        animationSpec = motion.fastEffectsSpec(),
        label = "voiceControlFill",
    )
    val controlDescription = stringResource(
        when {
            failed -> R.string.chat_voice_error
            isPlaying -> R.string.chat_voice_pause
            else -> R.string.chat_voice_play
        },
    )
    Row(
        modifier = modifier
            .widthIn(min = 200.dp, max = 270.dp)
            .clip(MaterialTheme.shapes.medium)
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
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { togglePlayback() }
                .semantics { contentDescription = controlDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(percent = controlCorner.roundToInt()))
                    .background(contentColor.copy(alpha = controlFill)),
                contentAlignment = Alignment.Center,
            ) {
                VoicePlayPauseIcon(
                    playing = isPlaying,
                    loading = loading,
                    failed = failed,
                    tint = contentColor,
                    iconSize = 22.dp,
                )
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
                // Read the live id: a drag's first event starts the note and the
                // following ones scrub it, without restarting playback.
                if (player.activeId == messageId) {
                    player.seek(fraction)
                } else {
                    togglePlayback()
                }
            },
            onLongClick = onLongClick,
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = formatMediaDuration(remainingMs),
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
    val haptic = LocalHapticFeedback.current
    val waveformLabel = stringResource(R.string.chat_voice_waveform)
    val seek = onSeek
    // Follows the finger so the bars don't trail MediaPlayer while it seeks.
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    // The player reports every 80 ms; a linear tween covers each gap so the
    // playhead glides instead of hopping. Read in the draw lambda, not here, so
    // a tick repaints without recomposing the bubble.
    val animatedProgress = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "waveformProgress",
    )
    // The lambdas are new on every recomposition, so a pointerInput keyed on
    // them would restart mid-drag and drop the gesture to the bubble's
    // swipe-to-reply detector, which is an ancestor.
    val latestSeek by rememberUpdatedState(seek)
    val latestLongClick by rememberUpdatedState(onLongClick)
    val gestureModifier = if (seek != null) {
        Modifier
            .semantics {
                contentDescription = waveformLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress.coerceIn(0f, 1f),
                    range = 0f..1f,
                )
                setProgress { target ->
                    seek(target)
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (size.width > 0) latestSeek?.invoke(offset.x / size.width)
                    },
                    // A slow drag must scrub, not open the message actions.
                    onLongPress = { if (dragProgress == null) latestLongClick?.invoke() },
                )
            }
            .pointerInput(Unit) {
                var fraction = 0f
                var tickStep = 0
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        fraction = scrubFraction(offset.x / size.width, 0f, size.width.toFloat())
                        tickStep = scrubTickStep(fraction)
                        dragProgress = fraction
                        haptic.thresholdActivated()
                        latestSeek?.invoke(fraction)
                    },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        fraction = scrubFraction(fraction, delta, size.width.toFloat())
                        val nextStep = scrubTickStep(fraction)
                        if (nextStep != tickStep) {
                            tickStep = nextStep
                            haptic.segmentFrequentTick()
                        }
                        dragProgress = fraction
                        latestSeek?.invoke(fraction)
                    },
                    onDragEnd = { dragProgress = null },
                    onDragCancel = { dragProgress = null },
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
        val played = (dragProgress ?: animatedProgress.value).coerceIn(0f, 1f)
        for (index in 0 until count) {
            val amplitude = if (amplitudes.isEmpty()) {
                0.4f
            } else {
                amplitudes.getOrElse((index * step).toInt().coerceIn(0, amplitudes.lastIndex)) {
                    0.4f
                }
            }
            val base = (size.height * (0.12f + 0.88f * amplitude.coerceIn(0f, 1f)))
                .coerceIn(3.dp.toPx(), size.height)
            val left = index * (barWidth + gap)
            val from = index.toFloat() / count
            val to = (index + 1).toFloat() / count
            // The bar the playhead is crossing blends between the played and
            // unplayed alphas and grows slightly, so progress advances without
            // a column switching on and off.
            val alpha: Float
            val height: Float
            when {
                to <= played -> {
                    alpha = 1f
                    height = base
                }
                from >= played -> {
                    alpha = 0.32f
                    height = base
                }
                else -> {
                    alpha = 1f - 0.68f * ((played - from) * count)
                    height = (base * 1.1f).coerceAtMost(size.height)
                }
            }
            drawRoundRect(
                color = contentColor.copy(alpha = alpha),
                topLeft = Offset(left, (size.height - height) / 2f),
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

/**
 * Fraction of the waveform under a drag: [startFraction] shifted by [deltaPx]
 * over [widthPx], kept inside 0..1 so a drag past either end stops there.
 */
internal fun scrubFraction(startFraction: Float, deltaPx: Float, widthPx: Float): Float {
    if (widthPx <= 0f) return startFraction.coerceIn(0f, 1f)
    return (startFraction + deltaPx / widthPx).coerceIn(0f, 1f)
}

/** Which of [steps] equal bands [fraction] has reached, for one tick per band. */
internal fun scrubTickStep(fraction: Float, steps: Int = 20): Int {
    if (steps <= 0) return 0
    val scaled = fraction.coerceIn(0f, 1f) * steps
    return scaled.toInt().coerceIn(0, steps - 1)
}
