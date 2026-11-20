package com.goldrift.instadm.ui.chat

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Minimum same-day gap between consecutive messages before inserting a centered time label. */
const val TIME_GAP_DIVIDER_MS = 20 * 60_000L

/** Maximum gap within a single sender's visual bubble cluster. */
const val CLUSTER_GAP_MS = 3 * 60_000L

private const val MAX_JUMBO_EMOJI_COUNT = 3
private const val VARIATION_SELECTOR_TEXT = 0xFE0E
private const val VARIATION_SELECTOR_EMOJI = 0xFE0F
private const val COMBINING_KEYCAP = 0x20E3
private const val ZWJ = 0x200D
private const val TAG_BASE_BLACK_FLAG = 0x1F3F4
private const val TAG_END = 0xE007F

enum class TranscriptDivider { None, Day, TimeGap }

enum class ClusterPosition { Solo, First, Middle, Last }

/**
 * Decides which divider sits above [message] in chronological transcript order.
 * Day boundaries always take precedence over same-day time-gap labels.
 */
fun transcriptDividerBefore(
    previous: ChatMessage?,
    message: ChatMessage,
    zone: TimeZone = TimeZone.getDefault(),
): TranscriptDivider {
    if (message.timestampMs <= 0) return TranscriptDivider.None
    if (previous == null || previous.timestampMs <= 0) return TranscriptDivider.Day
    if (calendarDaysBetween(previous.timestampMs, message.timestampMs, zone) > 0) {
        return TranscriptDivider.Day
    }
    return if (message.timestampMs - previous.timestampMs >= TIME_GAP_DIVIDER_MS) {
        TranscriptDivider.TimeGap
    } else {
        TranscriptDivider.None
    }
}

/**
 * Formats a message's timestamp for the inline tap-to-reveal footer: time only
 * on the current calendar day, month and day earlier in the same year, and year
 * included for older messages.
 */
fun formatRevealedTimestamp(
    timestampMs: Long,
    fallbackTime: String,
    nowMs: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    if (timestampMs <= 0) return fallbackTime
    val date = Date(timestampMs)
    val timeText = SimpleDateFormat("h:mm a", locale).apply { timeZone = zone }.format(date)
    if (calendarDaysBetween(timestampMs, nowMs, zone) == 0L) return timeText

    val msgCal = Calendar.getInstance(zone).apply { timeInMillis = timestampMs }
    val nowCal = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
    val datePattern = if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
        "MMM d"
    } else {
        "MMM d, yyyy"
    }
    val dateText = SimpleDateFormat(datePattern, locale).apply { timeZone = zone }.format(date)
    return "$dateText · $timeText"
}

/** True when a message renders only sticker artwork without a bubble container. */
fun ChatMessage.isStickerOnly(): Boolean =
    media.isNotEmpty() &&
        media.all { it.isSticker } &&
        text.isBlank() &&
        reply == null &&
        localMedia == null

/** True when a message renders 1 to 3 standalone emoji without a bubble container. */
fun ChatMessage.isJumboEmoji(): Boolean =
    !isAdminNotice() &&
        media.isEmpty() &&
        localMedia == null &&
        reply == null &&
        isJumboEmojiText(text)

/** True when the message renders inside a standard rounded bubble Surface. */
fun ChatMessage.hasBubbleContainer(): Boolean =
    !isAdminNotice() && !isStickerOnly() && !isJumboEmoji()

/**
 * Whether [older] and [newer] belong to the same visual bubble cluster: same
 * sender (and same group member), no day or time divider between them, within
 * [CLUSTER_GAP_MS], and both wrapped in bubble containers.
 */
fun sameSenderCluster(
    older: ChatMessage,
    newer: ChatMessage,
    zone: TimeZone = TimeZone.getDefault(),
): Boolean {
    if (!older.hasBubbleContainer() || !newer.hasBubbleContainer()) return false
    if (older.sender != newer.sender) return false
    if (older.sender == MessageSender.Them && older.senderFbid != newer.senderFbid) return false
    if (transcriptDividerBefore(older, newer, zone) != TranscriptDivider.None) return false
    if (older.timestampMs <= 0 || newer.timestampMs <= 0) return false
    return (newer.timestampMs - older.timestampMs) in 0 until CLUSTER_GAP_MS
}

/** Position of [message] within its visual sender cluster. */
fun clusterPosition(
    previous: ChatMessage?,
    message: ChatMessage,
    next: ChatMessage?,
    zone: TimeZone = TimeZone.getDefault(),
): ClusterPosition {
    if (!message.hasBubbleContainer()) return ClusterPosition.Solo
    val joinedPrev = previous != null && sameSenderCluster(previous, message, zone)
    val joinedNext = next != null && sameSenderCluster(message, next, zone)
    return when {
        joinedPrev && joinedNext -> ClusterPosition.Middle
        !joinedPrev && joinedNext -> ClusterPosition.First
        joinedPrev && !joinedNext -> ClusterPosition.Last
        else -> ClusterPosition.Solo
    }
}

/** True when [text] contains only 1 to 3 emoji clusters and optional whitespace. */
fun isJumboEmojiText(text: String): Boolean {
    if (text.isBlank()) return false
    var index = 0
    val length = text.length
    var count = 0
    while (index < length) {
        val cp = text.codePointAt(index)
        if (Character.isWhitespace(cp)) {
            index += Character.charCount(cp)
            continue
        }
        val nextIndex = consumeEmojiCluster(text, index) ?: return false
        count++
        if (count > MAX_JUMBO_EMOJI_COUNT) return false
        index = nextIndex
    }
    return count in 1..MAX_JUMBO_EMOJI_COUNT
}

private fun consumeEmojiCluster(text: String, startIndex: Int): Int? {
    var index = consumeSingleEmojiComponent(text, startIndex) ?: return null
    val length = text.length
    while (index < length && text.codePointAt(index) == ZWJ) {
        val afterZwj = index + Character.charCount(ZWJ)
        if (afterZwj >= length) return null
        index = consumeSingleEmojiComponent(text, afterZwj) ?: return null
    }
    return index
}

private fun consumeSingleEmojiComponent(text: String, startIndex: Int): Int? {
    val length = text.length
    if (startIndex >= length) return null
    val first = text.codePointAt(startIndex)
    var index = startIndex + Character.charCount(first)

    if (isKeycapBase(first)) {
        if (index < length && isVariationSelector(text.codePointAt(index))) {
            index += Character.charCount(text.codePointAt(index))
        }
        if (index < length && text.codePointAt(index) == COMBINING_KEYCAP) {
            return index + Character.charCount(COMBINING_KEYCAP)
        }
        return null
    }

    if (isRegionalIndicator(first)) {
        if (index < length && isRegionalIndicator(text.codePointAt(index))) {
            return index + Character.charCount(text.codePointAt(index))
        }
        return null
    }

    if (first == TAG_BASE_BLACK_FLAG && index < length && isTagCharacter(text.codePointAt(index))) {
        while (index < length && isTagCharacter(text.codePointAt(index))) {
            index += Character.charCount(text.codePointAt(index))
        }
        if (index < length && text.codePointAt(index) == TAG_END) {
            return index + Character.charCount(TAG_END)
        }
        return null
    }

    if (!isEmojiBase(first)) return null

    while (index < length) {
        val next = text.codePointAt(index)
        if (isVariationSelector(next) || isSkinToneModifier(next)) {
            index += Character.charCount(next)
        } else {
            break
        }
    }
    return index
}

private fun isKeycapBase(cp: Int): Boolean =
    cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code

private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

private fun isSkinToneModifier(cp: Int): Boolean = cp in 0x1F3FB..0x1F3FF

private fun isVariationSelector(cp: Int): Boolean =
    cp == VARIATION_SELECTOR_TEXT || cp == VARIATION_SELECTOR_EMOJI

private fun isTagCharacter(cp: Int): Boolean =
    cp in 0xE0020..0xE007E

private fun isEmojiBase(cp: Int): Boolean = when (cp) {
    in 0x1F000..0x1F1FF -> !isRegionalIndicator(cp)
    in 0x1F200..0x1FAFF -> true
    in 0x2600..0x27BF -> true
    in 0x23E9..0x23FA -> true
    in 0x2194..0x2199 -> true
    in 0x21A9..0x21AA -> true
    in 0x25FB..0x25FE -> true
    in 0x2934..0x2935 -> true
    in 0x2B05..0x2B07 -> true
    0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139,
    0x231A, 0x231B, 0x2328, 0x23CF, 0x24C2,
    0x25AA, 0x25AB, 0x25B6, 0x25C0,
    0x2B1B, 0x2B1C, 0x2B50, 0x2B55,
    0x3030, 0x303D, 0x3297, 0x3299 -> true
    else -> false
}

/** Visual thumbnail shown in a reply quote or composer reply bar. */
data class ReplyThumbnail(
    val model: Any,
    val isVideo: Boolean = false,
)

/**
 * Extracts a preview image model for replies targeting photos, videos, reels,
 * link previews, stickers, or pending image uploads.
 */
fun ChatMessage.replyThumbnail(): ReplyThumbnail? {
    for (item in media) {
        if (item.viewOnce || item.isAudio) continue
        val isVideoMedia = item.isVideo && item.type != "gif"
        if (!item.isVideo && !item.isLink && item.localPath.isNotBlank()) {
            val localFile = File(item.localPath)
            if (localFile.isFile) {
                return ReplyThumbnail(model = localFile, isVideo = false)
            }
        }
        val url = if (item.isSticker) item.stickerUrl else item.thumbnailUrl
        if (url.isNotBlank()) {
            return ReplyThumbnail(model = url, isVideo = isVideoMedia)
        }
    }
    val pending = localMedia
    if (pending != null && pending.kind == "image" && pending.uri.isNotBlank()) {
        return ReplyThumbnail(model = pending.uri, isVideo = false)
    }
    return null
}

/**
 * Counts incoming non-admin messages appended after [previousNewestId].
 * Prepending older pages leaves the newest item at the end, so it yields 0.
 */
fun countNewArrivals(
    transcript: List<ChatMessage>,
    previousNewestId: String?,
): Int {
    if (previousNewestId.isNullOrBlank() || transcript.isEmpty()) return 0
    val index = transcript.indexOfLast { it.id == previousNewestId }
    if (index < 0 || index >= transcript.lastIndex) return 0
    var arrivals = 0
    for (i in (index + 1)..transcript.lastIndex) {
        val message = transcript[i]
        if (message.sender == MessageSender.Them && !message.isAdminNotice()) {
            arrivals++
        }
    }
    return arrivals
}

/** Total member count for a group header, including the viewer. */
fun groupMemberCount(participants: List<ConversationParticipant>): Int =
    if (participants.isEmpty()) 0 else participants.size + 1
