package com.goldrift.instadm.ui.chat

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.goldrift.instadm.R
import com.goldrift.instadm.data.MessageMedia
import java.io.File
import java.util.Locale

/** Downloads one attachment and returns the local file. */
typealias MediaLoader =
    suspend (threadId: String, messageId: String, media: MessageMedia) -> File

/**
 * Inline bubble attachment. Images and videos use the signed CDN preview;
 * GIFs download and autoplay while their row is visible. Tapping any attachment
 * opens the full-screen viewer, which downloads through the Go client.
 */
@Composable
fun MediaThumbnail(
    media: MessageMedia,
    threadId: String,
    messageId: String,
    loadMedia: MediaLoader?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (media.isSticker) {
        StickerImage(
            media = media,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
        )
        return
    }
    val longClickLabel = stringResource(R.string.chat_message_actions)
    Box(
        modifier = modifier
            .widthIn(max = 260.dp)
            .heightIn(max = 320.dp)
            .aspectRatio(media.aspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (media.type == "gif") {
            InlineGif(
                threadId = threadId,
                messageId = messageId,
                media = media,
                loadMedia = loadMedia,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ThumbnailImage(media = media, modifier = Modifier.fillMaxSize())

            if (media.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.36f)),
                            ),
                        ),
                )
                MediaPlayBadge(
                    contentDescription = stringResource(
                        if (media.isReel) R.string.chat_reel_play else R.string.chat_video_play,
                    ),
                )
                if (media.isReel) {
                    MediaOverlayPill(
                        text = stringResource(R.string.chat_reel_badge),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    )
                }
                if (media.durationMs > 0) {
                    MediaOverlayPill(
                        text = formatMediaDuration(media.durationMs),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerImage(
    media: MessageMedia,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val longClickLabel = stringResource(R.string.chat_message_actions)
    val description = media.altText.ifBlank { stringResource(R.string.chat_sticker) }
    Box(
        modifier = modifier
            .sizeIn(maxWidth = 160.dp, maxHeight = 160.dp)
            .aspectRatio(media.aspectRatio)
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(media.stickerUrl)
                // A crossfade restarts the animation from frame zero.
                .crossfade(false)
                .build(),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            loading = {},
            error = { MediaPlaceholder(isLoading = false) },
        )
    }
}

@Composable
private fun ThumbnailImage(
    media: MessageMedia,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (media.thumbnailUrl.isBlank()) {
        // Videos and reels without a preview still get the play card; images
        // show a broken-attachment glyph.
        if (!media.isVideo) MediaPlaceholder(isLoading = false)
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(media.thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.chat_media_open),
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { MediaPlaceholder(isLoading = true) },
            // An expired preview should not cover the play card with a broken
            // image; the viewer can still resolve a fresh URL.
            error = { if (!media.isVideo) MediaPlaceholder(isLoading = false) },
        )
    }
}

/**
 * Downloads a GIF through the Go client once its row is visible, then loops it
 * inline. While the download runs, the static preview stays in place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InlineGif(
    threadId: String,
    messageId: String,
    media: MessageMedia,
    loadMedia: MediaLoader?,
    modifier: Modifier = Modifier,
) {
    var visible by remember(messageId, media.partIndex) { mutableStateOf(false) }
    var file by remember(messageId, media.partIndex) { mutableStateOf<File?>(null) }
    var failed by remember(messageId, media.partIndex) { mutableStateOf(false) }

    LaunchedEffect(messageId, media.partIndex, visible) {
        if (!visible || file != null || failed) return@LaunchedEffect
        val loader = loadMedia ?: return@LaunchedEffect
        runCatching { loader(threadId, messageId, media) }
            .onSuccess { file = it }
            .onFailure { failed = true }
    }

    Box(
        modifier = modifier.onVisibilityChanged(minFractionVisible = 0f) { visible = it },
        contentAlignment = Alignment.Center,
    ) {
        val loaded = file
        if (loaded != null) {
            LoopingVideo(
                file = loaded,
                active = visible,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ThumbnailImage(media = media, modifier = Modifier.fillMaxSize())
            if (!failed) {
                ContainedLoadingIndicator(modifier = Modifier.size(52.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createExoPlayer(
    context: Context,
    preferSoftwareDecoder: Boolean,
    audioAttributes: AudioAttributes,
    handleAudioFocus: Boolean,
): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .apply {
            if (preferSoftwareDecoder) {
                setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
            }
        }
    return ExoPlayer.Builder(context, renderersFactory)
        .setAudioAttributes(audioAttributes, handleAudioFocus)
        .build()
}

internal fun isDecoderFailureCode(errorCode: Int): Boolean =
    errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED

internal fun PlaybackException.isDecoderFailure(): Boolean = isDecoderFailureCode(errorCode)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LoopingVideo(
    file: File,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeState = rememberUpdatedState(active)
    var preferSoftwareDecoder by remember(file) { mutableStateOf(false) }
    val exoPlayer = remember(file, preferSoftwareDecoder) {
        createExoPlayer(
            context = context,
            preferSoftwareDecoder = preferSoftwareDecoder,
            audioAttributes = AudioAttributes.DEFAULT,
            handleAudioFocus = false,
        ).apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }

    LaunchedEffect(exoPlayer, active) {
        exoPlayer.playWhenReady = active
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!preferSoftwareDecoder && error.isDecoderFailure()) {
                    preferSoftwareDecoder = true
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.playWhenReady = false
                Lifecycle.Event.ON_START -> exoPlayer.playWhenReady = activeState.value
                else -> Unit
            }
        }
        exoPlayer.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            exoPlayer.removeListener(playerListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    ContentFrame(
        player = exoPlayer,
        modifier = modifier,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = ContentScale.Crop,
    )
}

@Composable
internal fun MediaPlayBadge(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC141318),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = contentDescription,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
internal fun MediaOverlayPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color(0xB8141318),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaPlaceholder(isLoading: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            LoadingIndicator()
        } else {
            Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = stringResource(R.string.chat_media_unavailable),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Placeholder for Instagram view-once media. The Go core deliberately exposes
 * no URL for it: the one allowed view belongs to the official app, so the
 * bubble explains why the attachment cannot be opened here.
 */
@Composable
fun ViewOnceNotice(
    media: MessageMedia,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    NoticeCard(
        icon = Icons.Rounded.VisibilityOff,
        text = stringResource(viewOnceNoticeText(media)),
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

/**
 * Notice for an expired share (for example a story Instagram no longer has).
 * It uses the same card as the view-once notice: the media cannot be opened,
 * and the text explains why.
 */
@Composable
fun UnavailableShareNotice(
    text: String,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    NoticeCard(
        icon = Icons.Rounded.VisibilityOff,
        text = text,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

@Composable
private fun NoticeCard(
    icon: ImageVector,
    text: String,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val longClickLabel = stringResource(R.string.chat_message_actions)
    Row(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = {},
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Notice copy for a view-once attachment, honouring Instagram's replay policy. */
@StringRes
internal fun viewOnceNoticeText(media: MessageMedia): Int {
    val video = media.type == "video"
    return when {
        media.viewMode == "replayed" && video -> R.string.chat_view_twice_video
        media.viewMode == "replayed" -> R.string.chat_view_twice_photo
        video -> R.string.chat_view_once_video
        else -> R.string.chat_view_once_photo
    }
}

/**
 * Optimistic preview for an attachment that is still uploading. Images load
 * straight from the picked content URI; videos show a play card until the
 * server representation replaces the bubble.
 */
@Composable
fun LocalMediaThumbnail(
    media: PendingMedia,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (media.kind) {
        "image", "video" -> LocalVisualThumbnail(
            media = media,
            onLongClick = onLongClick,
            modifier = modifier,
        )

        else -> LocalFileCard(
            media = media,
            onLongClick = onLongClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun LocalVisualThumbnail(
    media: PendingMedia,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val longClickLabel = stringResource(R.string.chat_message_actions)
    Box(
        modifier = modifier
            .widthIn(max = 260.dp)
            .heightIn(max = 320.dp)
            .aspectRatio(media.aspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (media.kind == "image") {
            SubcomposeAsyncImage(
                model = media.uri.toUri(),
                contentDescription = stringResource(R.string.chat_media_open),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { MediaPlaceholder(isLoading = true) },
                error = { MediaPlaceholder(isLoading = false) },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                MediaPlayBadge(
                    contentDescription = stringResource(R.string.chat_video_play),
                )
                if (media.durationMs > 0) {
                    MediaOverlayPill(
                        text = formatMediaDuration(media.durationMs),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalFileCard(
    media: PendingMedia,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val longClickLabel = stringResource(R.string.chat_message_actions)
    Row(
        modifier = modifier
            .widthIn(min = 170.dp, max = 260.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = {},
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (media.kind == "audio") {
                Icons.Rounded.MusicNote
            } else {
                Icons.Rounded.AttachFile
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = media.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (media.durationMs > 0) {
                Text(
                    text = formatMediaDuration(media.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun formatMediaDuration(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
