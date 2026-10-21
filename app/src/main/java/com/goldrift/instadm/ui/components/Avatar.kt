package com.goldrift.instadm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.ConversationParticipant

/**
 * Bright backgrounds read well behind initials and a white glyph, so contacts
 * without a photo get one of these fixed accent colors.
 */
private val AvatarPalette = listOf(
    Color(0xFFFA903E),
    Color(0xFFFE63B8),
    Color(0xFFEE675C),
    Color(0xFFC58AF9),
    Color(0xFF8AB4F8),
    Color(0xFF81C995),
    Color(0xFFFDD663),
    Color(0xFF78D9EC),
)

private val OnAvatarPalette = Color(0xFF202124)

private fun avatarColor(seed: String): Color =
    AvatarPalette[Math.floorMod(seed.hashCode(), AvatarPalette.size)]

/**
 * Avatar used by both the inbox rows and the chat header. The initials stay
 * visible underneath the image, so a missing or expired URL degrades to the
 * same colored placeholder.
 */
@Composable
fun Avatar(
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    contentDescription: String? = null,
    colorSeed: String = initials,
) {
    Box(
        modifier = modifier
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(colorSeed), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = if (size >= 40.dp) {
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Normal,
                )
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = OnAvatarPalette,
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Conversation avatar: the custom image (groups) or the other person's profile
 * picture (DMs) when available. A group without a custom photo falls back to a
 * collage of its members, matching what Instagram's own client shows.
 */
@Composable
fun ConversationAvatar(
    contact: Contact,
    isGroup: Boolean,
    participants: List<ConversationParticipant>,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val seed = contact.id.ifBlank { contact.name }
    val memberPhotos = if (isGroup && contact.avatarUrl.isBlank()) {
        participants.map { it.avatarUrl }.filter { it.isNotBlank() }
    } else {
        emptyList()
    }

    if (memberPhotos.size >= 2) {
        AvatarCollage(
            photos = memberPhotos,
            initials = contact.initials,
            size = size,
            colorSeed = seed,
            modifier = modifier,
            contentDescription = contentDescription,
        )
    } else {
        Avatar(
            initials = contact.initials,
            size = size,
            modifier = modifier,
            imageUrl = contact.avatarUrl.ifBlank { memberPhotos.firstOrNull() },
            contentDescription = contentDescription,
            colorSeed = seed,
        )
    }
}

@Composable
private fun AvatarCollage(
    photos: List<String>,
    initials: String,
    size: Dp,
    colorSeed: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val shown = photos.take(4)
    Box(
        modifier = modifier
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(colorSeed), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn first so it shows through if a tile fails and renders nothing.
        Text(
            text = initials,
            style = if (size >= 40.dp) {
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Normal,
                )
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = OnAvatarPalette,
        )
        when (shown.size) {
            2 -> Row(Modifier.matchParentSize()) {
                CollageTile(shown[0], Modifier.weight(1f).fillMaxHeight())
                CollageTile(shown[1], Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.matchParentSize()) {
                CollageTile(shown[0], Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    CollageTile(shown[1], Modifier.weight(1f).fillMaxWidth())
                    CollageTile(shown[2], Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.matchParentSize()) {
                // Four or more photos form a 2x2 grid.
                for (row in 0 until 2) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        for (column in 0 until 2) {
                            CollageTile(
                                url = shown[row * 2 + column],
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageTile(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
