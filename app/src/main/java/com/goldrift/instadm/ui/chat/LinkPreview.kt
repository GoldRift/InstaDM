package com.goldrift.instadm.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.goldrift.instadm.R
import com.goldrift.instadm.data.MessageMedia
import java.net.URI

/**
 * True for a link the app can hand to another activity. Only http(s) URLs are
 * accepted, so a malformed or non-web target cannot be opened accidentally.
 */
internal fun isOpenableLink(raw: String): Boolean = runCatching {
    val uri = URI(raw.trim())
    (uri.scheme.equals("http", ignoreCase = true) ||
        uri.scheme.equals("https", ignoreCase = true)) &&
        !uri.host.isNullOrBlank()
}.getOrDefault(false)

/**
 * Short label for a shared link. The scheme, `www.`, query (tracking tags), and
 * fragment are dropped, so Instagram shares read `/reel/<shortcode>/`,
 * `/p/<shortcode>/`, or `/stories/<user>/<id>/`, and other pages read
 * `example.com/path`. The untouched URL is still what opens.
 */
internal fun shareLinkLabel(raw: String): String {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return raw
    val host = uri.host.orEmpty().lowercase().removePrefix("www.")
    if (host.isBlank()) return raw
    val path = uri.rawPath.orEmpty()
    val instagram = host == "instagram.com" || host.endsWith(".instagram.com")
    return when {
        path.isBlank() || path == "/" -> host
        instagram -> path
        else -> "$host$path"
    }
}

/**
 * Preview and link for a shared post, story, reel, or external page. The
 * preview (or reel cover) sits on top, with the target link on a tinted strip
 * attached beneath it so every share keeps a readable destination even after a
 * signed preview expires. A reel preview opens the in-app player; its link
 * strip still opens Instagram.
 */
@Composable
fun ShareCard(
    media: MessageMedia,
    onOpenReel: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shareUrl = media.shareUrl
    val hasPreview = media.isReel || media.thumbnailUrl.isNotBlank()
    val hasLink = shareUrl.isNotBlank()
    if (!hasPreview && !hasLink) return

    val openable = isOpenableLink(shareUrl)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val longClickLabel = stringResource(R.string.chat_message_actions)
    val openLink = {
        if (openable) {
            runCatching { uriHandler.openUri(shareUrl) }
                .onFailure {
                    Toast.makeText(
                        context,
                        R.string.chat_link_error,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }

    Column(
        modifier = modifier.then(
            // A reel cover is portrait, so a fixed width keeps the link
            // line from widening the card past the cover.
            if (media.isReel) {
                Modifier.width(180.dp)
            } else {
                Modifier.widthIn(min = 180.dp, max = 260.dp)
            },
        ),
    ) {
        if (hasPreview) {
            if (media.isReel) {
                ReelCover(
                    media = media,
                    hasLink = hasLink,
                    longClickLabel = longClickLabel,
                    onLongClick = onLongClick,
                    onOpen = onOpenReel,
                )
            } else {
                LinkPreview(
                    media = media,
                    hasLink = hasLink,
                    longClickLabel = longClickLabel,
                    onLongClick = onLongClick,
                    onOpen = openLink,
                )
            }
        }

        if (hasLink) {
            LinkTargetRow(
                url = shareUrl,
                openable = openable,
                hasPreview = hasPreview,
                longClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onOpen = openLink,
            )
        }
    }
}

/** Preview image of a shared link, opened as a web page. */
@Composable
private fun LinkPreview(
    media: MessageMedia,
    hasLink: Boolean,
    longClickLabel: String,
    onLongClick: (() -> Unit)?,
    onOpen: () -> Unit,
) {
    // Height comes from the clamped aspect ratio, so a square or portrait
    // preview can never outgrow its box and paint over the link beneath it.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val height = (maxWidth / media.aspectRatio)
            .coerceAtMost(MaxPreviewHeight)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shareShape(hasLink))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .combinedClickable(
                    onLongClickLabel = longClickLabel,
                    onLongClick = onLongClick,
                    onClick = onOpen,
                ),
        ) {
            SharePreviewImage(media)
        }
    }
}

/** Reel cover: the signed preview, a play affordance, and the reel badge. */
@Composable
private fun ReelCover(
    media: MessageMedia,
    hasLink: Boolean,
    longClickLabel: String,
    onLongClick: (() -> Unit)?,
    onOpen: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .aspectRatio(media.aspectRatio)
            .clip(shareShape(hasLink))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onOpen,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SharePreviewImage(media)

        Box(
            modifier = Modifier
                .size(54.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.chat_reel_play),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = stringResource(R.string.chat_reel_badge),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.extraSmall,
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
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
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SharePreviewImage(media: MessageMedia) {
    if (media.thumbnailUrl.isBlank()) return
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(media.thumbnailUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        },
        // A signed preview URL can expire; the link text remains.
        error = {},
    )
}

/** The target URL line under a share, tappable when it is a web link. */
@Composable
private fun LinkTargetRow(
    url: String,
    openable: Boolean,
    hasPreview: Boolean,
    longClickLabel: String,
    onLongClick: (() -> Unit)?,
    onOpen: () -> Unit,
) {
    // A translucent lift of the bubble's own content color keeps the link
    // readable on incoming and outgoing bubbles without turning it into a
    // second card.
    val contentColor = LocalContentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(linkShape(hasPreview))
            .background(contentColor.copy(alpha = 0.14f))
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onOpen,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Link,
            contentDescription = stringResource(R.string.chat_link_open),
            tint = contentColor.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = shareLinkLabel(url),
            style = MaterialTheme.typography.bodyMedium,
            color = if (openable) contentColor else contentColor.copy(alpha = 0.7f),
            textDecoration = if (openable) TextDecoration.Underline else null,
        )
    }
}

/**
 * Concentric with the bubble: its corner radius minus the attachment inset, so
 * the preview and link strip sit inside the bubble without a clashing curve.
 */
private val ShareCorner = BubbleCornerRadius - AttachmentInset

/** Tallest inline preview; portrait covers are clamped to this for links. */
private val MaxPreviewHeight = 200.dp

/** Rounds only the top of a preview that has a link strip beneath it. */
private fun shareShape(hasLink: Boolean): Shape =
    if (hasLink) {
        RoundedCornerShape(topStart = ShareCorner, topEnd = ShareCorner)
    } else {
        RoundedCornerShape(ShareCorner)
    }

/** Rounds only the bottom of a link strip that sits under a preview. */
private fun linkShape(hasPreview: Boolean): Shape =
    if (hasPreview) {
        RoundedCornerShape(bottomStart = ShareCorner, bottomEnd = ShareCorner)
    } else {
        RoundedCornerShape(ShareCorner)
    }
