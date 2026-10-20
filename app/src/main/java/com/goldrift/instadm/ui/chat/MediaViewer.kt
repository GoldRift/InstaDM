package com.goldrift.instadm.ui.chat

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.goldrift.instadm.R
import com.goldrift.instadm.data.MessageMedia
import java.io.File
import java.util.Locale

/** Which attachment the full-screen viewer is showing. */
data class MediaViewerTarget(
    val messageId: String,
    val media: MessageMedia,
)

/**
 * Inline bubble attachment. The signed CDN URL is only used for the thumbnail;
 * tapping opens the full-screen viewer, which downloads through the Go client.
 */
@Composable
fun MediaThumbnail(
    media: MessageMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .widthIn(max = 260.dp)
            .heightIn(max = 320.dp)
            .aspectRatio(media.aspectRatio)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (media.thumbnailUrl.isBlank()) {
            // Videos without a preview still get the play card; images show a
            // broken-attachment glyph.
            if (!media.isVideo) MediaPlaceholder(isLoading = false)
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(media.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.chat_media_open),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { MediaPlaceholder(isLoading = true) },
                error = { MediaPlaceholder(isLoading = false) },
            )
        }

        if (media.isVideo) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.chat_video_play),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            if (media.durationMs > 0) {
                Text(
                    text = formatMediaDuration(media.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaPlaceholder(isLoading: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
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

@Composable
fun MediaViewerDialog(
    threadId: String,
    target: MediaViewerTarget,
    loadMedia: (suspend (threadId: String, messageId: String, partIndex: Int, mediaType: String) -> File)?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var state by remember(target) { mutableStateOf<MediaLoadState>(MediaLoadState.Loading) }

        LaunchedEffect(target) {
            val loader = loadMedia
            if (loader == null) {
                state = MediaLoadState.Failed
                return@LaunchedEffect
            }
            state = runCatching {
                loader(threadId, target.messageId, target.media.partIndex, target.media.type)
            }.fold(
                onSuccess = { MediaLoadState.Ready(it) },
                onFailure = { MediaLoadState.Failed },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(enabled = state !is MediaLoadState.Ready) { onDismiss() },
        ) {
            when (val current = state) {
                MediaLoadState.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )

                MediaLoadState.Failed -> Text(
                    text = stringResource(R.string.chat_media_error),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )

                is MediaLoadState.Ready -> {
                    if (target.media.isVideo) {
                        VideoPlayer(
                            file = current.file,
                            loop = target.media.type == "gif",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ImageViewer(
                            file = current.file,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.chat_media_close),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ImageViewer(
    file: File,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = file,
        contentDescription = stringResource(R.string.chat_media_open),
        contentScale = ContentScale.Fit,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 48.dp),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.chat_media_error),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        },
    )
}

@Composable
private fun VideoPlayer(
    file: File,
    loop: Boolean,
    modifier: Modifier = Modifier,
) {
    key(file, loop) {
        AndroidView(
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    setVideoURI(Uri.fromFile(file))
                    setMediaController(
                        MediaController(viewContext).also { controller ->
                            controller.setAnchorView(this)
                        },
                    )
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = loop
                        start()
                    }
                    requestFocus()
                }
            },
            modifier = modifier,
            onRelease = { view -> view.stopPlayback() },
        )
    }
}

private sealed interface MediaLoadState {
    data object Loading : MediaLoadState
    data class Ready(val file: File) : MediaLoadState
    data object Failed : MediaLoadState
}

private fun formatMediaDuration(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
