package com.goldrift.instadm.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.goldrift.instadm.data.MentionTypes
import com.goldrift.instadm.data.MessageMention

/** Active `@query` span under the composer cursor. */
data class MentionQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

/** One selectable candidate in the composer mention sheet. */
sealed interface MentionSuggestion {
    data class Everyone(val threadKey: Long) : MentionSuggestion
    data class Person(val participant: ConversationParticipant) : MentionSuggestion
}

/** A mention inserted into the composer draft and tracked across edits. */
data class DraftMention(
    val start: Int,
    val end: Int,
    val type: String,
    val fbid: Long,
    val text: String,
)

data class MentionInsertion(
    val text: String,
    val cursor: Int,
    val mentions: List<DraftMention>,
)

/**
 * Finds the active `@query` immediately before [cursor], or null when the
 * cursor is not inside a mention token. `@` must sit at index 0 or after
 * whitespace so email addresses do not trigger autocomplete.
 */
fun activeMentionQuery(text: CharSequence, cursor: Int): MentionQuery? {
    val clamped = cursor.coerceIn(0, text.length)
    var index = clamped - 1
    while (index >= 0) {
        val ch = text[index]
        if (ch == '@') {
            if (index > 0 && !text[index - 1].isWhitespace()) return null
            val query = text.subSequence(index + 1, clamped).toString()
            return MentionQuery(start = index, end = clamped, query = query)
        }
        if (ch.isWhitespace()) return null
        index--
    }
    return null
}

/**
 * Ranks mention candidates for [query]. `@everyone` is offered only in group
 * threads, matching Instagram's group command behavior.
 */
fun mentionSuggestions(
    query: String,
    isGroup: Boolean,
    threadKey: Long,
    participants: List<ConversationParticipant>,
    limit: Int = 8,
): List<MentionSuggestion> {
    val needle = query.trim().lowercase()
    val results = ArrayList<MentionSuggestion>()

    if (isGroup && (needle.isEmpty() || "everyone".contains(needle))) {
        results += MentionSuggestion.Everyone(threadKey)
    }

    val matching = participants.filter { participant ->
        if (participant.fbid <= 0L) return@filter false
        if (needle.isEmpty()) return@filter true
        participant.username.lowercase().contains(needle) ||
            participant.name.lowercase().contains(needle)
    }.sortedWith(
        compareBy<ConversationParticipant>(
            { !(it.username.lowercase().startsWith(needle) || it.name.lowercase().startsWith(needle)) },
            { it.name.lowercase() },
            { it.username.lowercase() },
        ),
    )

    for (participant in matching) {
        if (results.size >= limit) break
        results += MentionSuggestion.Person(participant)
    }
    return results
}

/**
 * Replaces [query] in [text] with the chosen [suggestion] followed by a space,
 * shifting any previously tracked mentions that lie after the edit.
 */
fun applyMentionSuggestion(
    text: String,
    query: MentionQuery,
    suggestion: MentionSuggestion,
    existing: List<DraftMention>,
): MentionInsertion {
    val label = when (suggestion) {
        is MentionSuggestion.Everyone -> "@everyone"
        is MentionSuggestion.Person -> {
            val handle = suggestion.participant.username.ifBlank { suggestion.participant.name }
            "@$handle"
        }
    }
    val type = when (suggestion) {
        is MentionSuggestion.Everyone -> MentionTypes.EVERYONE
        is MentionSuggestion.Person -> MentionTypes.PERSON
    }
    val fbid = when (suggestion) {
        is MentionSuggestion.Everyone -> suggestion.threadKey
        is MentionSuggestion.Person -> suggestion.participant.fbid
    }

    val replacement = "$label "
    val nextText = text.substring(0, query.start) + replacement + text.substring(query.end)
    val delta = replacement.length - (query.end - query.start)
    val updated = ArrayList<DraftMention>(existing.size + 1)
    for (mention in existing) {
        when {
            mention.end <= query.start -> updated += mention
            mention.start >= query.end -> updated += mention.copy(
                start = mention.start + delta,
                end = mention.end + delta,
            )
            // Overlapping a replaced query invalidates the old tracked mention.
        }
    }
    updated += DraftMention(
        start = query.start,
        end = query.start + label.length,
        type = type,
        fbid = fbid,
        text = label,
    )
    updated.sortBy { it.start }
    return MentionInsertion(
        text = nextText,
        cursor = query.start + replacement.length,
        mentions = updated,
    )
}

/**
 * Keeps tracked mentions aligned when the user edits [text] manually. If a
 * mention's exact token is still present (possibly shifted by edits earlier in
 * the draft), its span updates; if the user edited inside the token, it drops.
 */
fun reconcileDraftMentions(
    text: String,
    existing: List<DraftMention>,
): List<DraftMention> {
    if (existing.isEmpty() || text.isEmpty()) return emptyList()
    val kept = ArrayList<DraftMention>(existing.size)
    var searchFrom = 0
    for (mention in existing.sortedBy { it.start }) {
        if (mention.text.isEmpty()) continue
        // Fast path: unchanged at the exact recorded offset.
        if (mention.start >= searchFrom &&
            mention.end <= text.length &&
            text.regionMatches(mention.start, mention.text, 0, mention.text.length)
        ) {
            kept += mention
            searchFrom = mention.end
            continue
        }
        // Fallback: find the nearest intact occurrence at or after previous kept mentions.
        val found = text.indexOf(mention.text, startIndex = searchFrom)
        if (found >= 0) {
            val end = found + mention.text.length
            kept += mention.copy(start = found, end = end)
            searchFrom = end
        }
    }
    return kept
}

/**
 * Converts tracked composer mentions into outgoing [MessageMention] spans
 * relative to `rawText.trim()`, matching the trimmed body sent to Instagram.
 */
fun resolveOutgoingMentions(
    rawText: String,
    tracked: List<DraftMention>,
): List<MessageMention> {
    if (tracked.isEmpty()) return emptyList()
    val leadingTrim = rawText.indexOfFirst { !it.isWhitespace() }
    if (leadingTrim < 0) return emptyList()
    val trimmed = rawText.trim()
    val reconciled = reconcileDraftMentions(rawText, tracked)
    return reconciled.mapNotNull { mention ->
        val offset = mention.start - leadingTrim
        val length = mention.end - mention.start
        if (offset < 0 || length <= 0 || offset + length > trimmed.length) {
            null
        } else {
            MessageMention(
                offset = offset,
                length = length,
                type = mention.type,
                fbid = mention.fbid,
            )
        }
    }
}

/**
 * Builds an [AnnotatedString] with mention spans emphasized. Out-of-bounds or
 * overlapping spans are skipped so malformed server data never crashes rendering.
 */
fun buildMentionAnnotatedString(
    text: String,
    mentions: List<MessageMention>,
    highlightColor: Color,
    highlightBackground: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    if (mentions.isEmpty() || text.isEmpty()) return@buildAnnotatedString
    var prevEnd = 0
    val style = SpanStyle(
        color = highlightColor,
        background = highlightBackground,
        fontWeight = FontWeight.SemiBold,
    )
    for (mention in mentions.sortedBy { it.offset }) {
        val start = mention.offset
        val end = mention.offset + mention.length
        if (start < prevEnd || start < 0 || mention.length <= 0 || end > text.length) continue
        addStyle(style = style, start = start, end = end)
        prevEnd = end
    }
}
