package com.goldrift.instadm.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.ui.chat.NowPlaying
import com.goldrift.instadm.ui.chat.VoicePlayer
import com.goldrift.instadm.ui.chat.WaveformBars
import com.goldrift.instadm.ui.chat.formatMediaDuration
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Where the bar belongs: a note is on the player, the feature is on, the note
 * is still going, and its chat is not the one being read.
 */
internal fun showVoiceMiniPlayer(
    nowPlaying: NowPlaying?,
    visibleThreadId: String?,
    enabled: Boolean,
    finished: Boolean,
): Boolean = enabled && nowPlaying != null && !finished &&
    nowPlaying.threadId != visibleThreadId

/** Height the floating bar takes at the bottom, margin included. */
internal val MiniVoicePlayerInset = 76.dp

/**
 * Player bar for a voice note that outlived its chat. It floats over the app
 * surface the way Material 3 expressive treats a player, but carries the note
 * itself: its waveform and the time still to go rather than a music seekbar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniVoicePlayer(
    player: VoicePlayer,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val note = player.nowPlaying ?: return
    val playing = player.playing
    val duration = player.durationMs
    val progress = if (duration > 0) {
        (player.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remaining = if (duration > 0) (duration - player.positionMs).coerceAtLeast(0) else 0
    val controlLabel = stringResource(
        if (playing) R.string.chat_voice_pause else R.string.chat_voice_play,
    )
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    // The bar rides the finger down and then leaves, rather than blinking out.
    val pull = remember { Animatable(0f) }
    var heightPx by remember { mutableIntStateOf(0) }
    Surface(
        onClick = onOpen,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .offset { IntOffset(0, pull.value.roundToInt()) }
            .graphicsLayer {
                // Gone exactly when it has travelled its own height, so the
                // exit animation ends without a pop.
                alpha = 1f - (pull.value / (heightPx * 1.05f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .onSizeChanged { heightPx = it.height }
            .pointerInput(player) {
                // Pulling down closes the player; pulling up does nothing.
                fun release() = scope.launch { pull.animateTo(0f, motion.defaultSpatialSpec()) }
                detectVerticalDragGestures(
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        scope.launch {
                            pull.snapTo((pull.value + delta).coerceAtLeast(0f))
                        }
                    },
                    onDragEnd = {
                        if (pull.value > heightPx * 0.4f) {
                            scope.launch {
                                pull.animateTo(
                                    targetValue = heightPx * 1.05f,
                                    animationSpec = motion.defaultSpatialSpec(),
                                )
                                player.stop()
                            }
                        } else {
                            release()
                        }
                    },
                    onDragCancel = { release() },
                )
            },
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 44 dp in a 64 dp row leaves 10 dp above and below, matching the
            // 10 dp start padding: the picture is inset evenly on both sides.
            Avatar(
                initials = note.initials,
                size = 44.dp,
                imageUrl = note.avatarUrl,
                colorSeed = note.threadId,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.mini_player_subtitle,
                        formatMediaDuration(remaining),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.waveform.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                WaveformBars(
                    amplitudes = note.waveform,
                    progress = progress,
                    modifier = Modifier
                        .width(64.dp)
                        .height(22.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = player::toggle,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = controlLabel,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
