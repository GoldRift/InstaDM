package com.goldrift.instadm.ui.chat

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
 * Card for a post, story, or external page shared as an XMA attachment. The
 * target URL is always visible, and tapping the card opens it outside the app.
 * Instagram only sends a preview image, so the card does not try to download
 * the page itself.
 */
@Composable
fun LinkPreviewCard(
    media: MessageMedia,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (media.url.isBlank()) return
    val openable = isOpenableLink(media.url)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val longClickLabel = stringResource(R.string.chat_message_actions)

    Column(
        modifier = modifier
            .widthIn(min = 180.dp, max = 260.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = {
                    if (openable) {
                        runCatching { uriHandler.openUri(media.url) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    R.string.chat_link_error,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                },
            ),
    ) {
        if (media.thumbnailUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(media.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .aspectRatio(media.aspectRatio),
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

        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = stringResource(R.string.chat_link_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = media.url,
                style = MaterialTheme.typography.bodyMedium,
                color = if (openable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textDecoration = if (openable) TextDecoration.Underline else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
