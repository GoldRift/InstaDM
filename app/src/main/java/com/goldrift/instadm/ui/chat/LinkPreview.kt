package com.goldrift.instadm.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.goldrift.instadm.R
import com.goldrift.instadm.data.LiveLocation
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.parseLiveLocation
import java.net.URI
import kotlin.math.roundToInt

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
 * `geo:` URI for a live-location pin. The raw Double values are interpolated
 * rather than formatted, so a comma-decimal locale cannot corrupt the pair.
 */
internal fun liveLocationGeoUri(location: LiveLocation): String =
    "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}"

/**
 * Hands a live location to the installed maps app. Returns false when no app
 * can handle the intent, so the bubble can show its failure toast.
 */
internal fun openLocationInMaps(context: Context, location: LiveLocation): Boolean {
    // FLAG_ACTIVITY_NEW_TASK: the Compose context is not always an Activity.
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(liveLocationGeoUri(location)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
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

/**
 * Map snapshot and captions for a live-location share. The whole card opens the
 * pin in the installed maps app; neither Instagram's dead deep link nor the
 * static map URL is ever handed to a browser.
 */
@Composable
fun LocationCard(
    media: MessageMedia,
    senderName: String,
    fromViewer: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val location = parseLiveLocation(media.url.ifBlank { media.targetUrl }) ?: return
    val context = LocalContext.current
    val openLocation = {
        if (!openLocationInMaps(context, location)) {
            Toast.makeText(
                context,
                R.string.chat_location_error,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Column(
        modifier = modifier
            .widthIn(min = 180.dp, max = 260.dp)
            // Rounding all corners matches a preview without a link strip.
            .clip(shareShape(hasLink = false))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onClickLabel = stringResource(R.string.chat_location_open),
                onLongClickLabel = stringResource(R.string.chat_message_actions),
                onLongClick = onLongClick,
                onClick = openLocation,
            ),
    ) {
        if (media.thumbnailUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clampedAspectRatio(media.aspectRatio, MaxPreviewHeight),
            ) {
                SharePreviewImage(media)
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.chat_live_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = when {
                    fromViewer -> stringResource(R.string.chat_location_sharing_you)
                    senderName.isNotBlank() ->
                        stringResource(R.string.chat_live_location_sharing, senderName)
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Notice for a live-location share Instagram has ended: it edits the same
 * message to a placeholder with no media, so the card keeps [LocationCard]'s
 * container but drops the map.
 */
@Composable
fun LocationEndedCard(
    senderName: String,
    fromViewer: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 180.dp, max = 260.dp)
            .clip(shareShape(hasLink = false))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onLongClickLabel = stringResource(R.string.chat_message_actions),
                onLongClick = onLongClick,
                onClick = {},
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.chat_location_expired),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = when {
                    fromViewer -> stringResource(R.string.chat_location_stopped_you)
                    senderName.isNotBlank() ->
                        stringResource(R.string.chat_location_stopped, senderName)
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun clampedPreviewHeightPx(
    widthPx: Int,
    aspectRatio: Float,
    maxHeightPx: Int,
): Int {
    if (maxHeightPx <= 0) return 0
    if (widthPx <= 0 || aspectRatio <= 0f || aspectRatio.isNaN()) return maxHeightPx
    return (widthPx / aspectRatio).roundToInt().coerceIn(0, maxHeightPx)
}

private fun Modifier.clampedAspectRatio(ratio: Float, maxHeight: Dp): Modifier =
    layout { measurable, constraints ->
        val maxHeightPx = maxHeight.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val desiredHeight = clampedPreviewHeightPx(width, ratio, maxHeightPx)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = desiredHeight,
                maxHeight = desiredHeight,
            ),
        )
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clampedAspectRatio(media.aspectRatio, MaxPreviewHeight)
            .clip(shareShape(hasLink))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onClickLabel = stringResource(R.string.chat_link_open),
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onOpen,
            ),
    ) {
        SharePreviewImage(media)
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
            contentDescription = stringResource(R.string.chat_reel_play),
        )
        MediaOverlayPill(
            text = stringResource(R.string.chat_reel_badge),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharePreviewImage(media: MessageMedia) {
    if (media.thumbnailUrl.isBlank()) return
    var loading by remember(media.thumbnailUrl) { mutableStateOf(true) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = media.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            // A signed preview URL can expire; the link text remains.
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading ||
                    state is AsyncImagePainter.State.Empty
            },
        )
        if (loading) {
            LoadingIndicator(
                modifier = Modifier.size(28.dp),
            )
        }
    }
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
                onClickLabel = if (openable) stringResource(R.string.chat_link_open) else null,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onOpen,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Link,
            contentDescription = null,
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
