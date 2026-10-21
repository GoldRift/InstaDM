package com.goldrift.instadm.ui.chat

import androidx.annotation.StringRes
import com.goldrift.instadm.R

/**
 * The parts of the newest message an inbox row needs. Both the mailbox stub
 * (`ThreadEntity.last_message_*`) and a loaded thread map into this shape, so
 * the row renders the same preview either way.
 */
data class ConversationPreview(
    /** Message kind (`text`, `image`, `video`, `audio`, `sticker`, ...). */
    val kind: String = "",
    /** Caption or message body; wins over the attachment label when set. */
    val text: String = "",
    val fromViewer: Boolean = false,
    /** Incoming sender in a group thread; blank for direct threads. */
    val senderName: String = "",
)

/**
 * A preview is literal text (a message body or sender name) or a string
 * resource with nested parts. Keeping the copy as resources here lets the inbox
 * row and the notification service resolve the same structure with their own
 * lookup, without leaking a Context into the mappers.
 */
sealed interface PreviewPart {
    data class Text(val value: String) : PreviewPart
    data class Res(
        @param:StringRes val id: Int,
        val args: List<PreviewPart> = emptyList(),
    ) : PreviewPart
}

fun PreviewPart.render(resolve: (id: Int, args: List<String>) -> String): String = when (this) {
    is PreviewPart.Text -> value
    is PreviewPart.Res -> resolve(id, args.map { it.render(resolve) })
}

/**
 * Chooses the one-line inbox copy: a caption wins over the attachment label,
 * the viewer and group senders get their own phrasing, and `null` means the
 * row has nothing to show yet.
 */
fun ConversationPreview.label(isGroup: Boolean): PreviewPart? {
    val body = text.trim()
    if (body.isNotEmpty()) {
        return when {
            fromViewer -> PreviewPart.Res(
                R.string.inbox_you_prefix,
                listOf(PreviewPart.Text(body)),
            )
            isGroup && senderName.isNotBlank() -> PreviewPart.Res(
                R.string.inbox_sender_prefix,
                listOf(PreviewPart.Text(senderName), PreviewPart.Text(body)),
            )
            else -> PreviewPart.Text(body)
        }
    }

    val noun = mediaNoun(kind) ?: return null
    val media = PreviewPart.Res(noun)
    return when {
        fromViewer -> PreviewPart.Res(R.string.inbox_you_sent, listOf(media))
        isGroup && senderName.isNotBlank() -> PreviewPart.Res(
            R.string.inbox_sent_by,
            listOf(PreviewPart.Text(senderName), media),
        )
        else -> PreviewPart.Res(R.string.inbox_sent_you, listOf(media))
    }
}

/** Attachment noun for [kind], or null when there is no attachment to describe. */
@StringRes
internal fun mediaNoun(kind: String): Int? = when (kind.trim().lowercase()) {
    "image" -> R.string.inbox_noun_photo
    "video" -> R.string.inbox_noun_video
    "audio" -> R.string.inbox_noun_voice
    "sticker" -> R.string.inbox_noun_sticker
    "gif" -> R.string.inbox_noun_gif
    "link" -> R.string.inbox_noun_link
    "", "text" -> null
    else -> R.string.inbox_noun_attachment
}

/** Preview data for a loaded bubble. */
fun ChatMessage.toPreview(): ConversationPreview = ConversationPreview(
    kind = kind.ifBlank { localMedia?.kind ?: media.firstOrNull()?.type.orEmpty() },
    text = text,
    fromViewer = sender == MessageSender.Me,
    senderName = senderName,
)
