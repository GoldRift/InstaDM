package com.goldrift.instadm

import androidx.compose.ui.graphics.Color
import com.goldrift.instadm.data.MentionTypes
import com.goldrift.instadm.data.MessageMention
import com.goldrift.instadm.data.mentionsToJson
import com.goldrift.instadm.data.parseMentionsJson
import com.goldrift.instadm.ui.chat.ConversationParticipant
import com.goldrift.instadm.ui.chat.DraftMention
import com.goldrift.instadm.ui.chat.MentionQuery
import com.goldrift.instadm.ui.chat.MentionSuggestion
import com.goldrift.instadm.ui.chat.activeMentionQuery
import com.goldrift.instadm.ui.chat.applyMentionSuggestion
import com.goldrift.instadm.ui.chat.buildMentionAnnotatedString
import com.goldrift.instadm.ui.chat.mentionSuggestions
import com.goldrift.instadm.ui.chat.reconcileDraftMentions
import com.goldrift.instadm.ui.chat.resolveOutgoingMentions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionsTest {
    private val maya = ConversationParticipant(
        fbid = 101L,
        name = "Maya Chen",
        initials = "MC",
        username = "maya.chen",
    )
    private val dev = ConversationParticipant(
        fbid = 102L,
        name = "Dev Patel",
        initials = "DP",
        username = "dev.patel",
    )

    @Test
    fun activeMentionQueryDetectsTriggerAtStartOrAfterWhitespace() {
        assertEquals(MentionQuery(start = 0, end = 1, query = ""), activeMentionQuery("@", 1))
        assertEquals(MentionQuery(start = 0, end = 4, query = "may"), activeMentionQuery("@may", 4))
        assertEquals(
            MentionQuery(start = 4, end = 8, query = "dev"),
            activeMentionQuery("hey @dev", 8),
        )
        assertEquals(
            MentionQuery(start = 4, end = 8, query = "dev"),
            activeMentionQuery("hey @dev later", 8),
        )
        assertNull(activeMentionQuery("email@domain.com", 12))
        assertNull(activeMentionQuery("hey @maya there", 15))
        assertNull(activeMentionQuery("hello", 5))
    }

    @Test
    fun mentionSuggestionsIncludeEveryoneOnlyInGroupsAndFilterParticipants() {
        val groupAll = mentionSuggestions(
            query = "",
            isGroup = true,
            threadKey = 200_000L,
            participants = listOf(maya, dev),
        )
        assertEquals(3, groupAll.size)
        assertTrue(groupAll.first() is MentionSuggestion.Everyone)
        assertEquals(200_000L, (groupAll.first() as MentionSuggestion.Everyone).threadKey)

        val directAll = mentionSuggestions(
            query = "",
            isGroup = false,
            threadKey = 200_000L,
            participants = listOf(maya),
        )
        assertEquals(1, directAll.size)
        assertTrue(directAll.single() is MentionSuggestion.Person)

        val filtered = mentionSuggestions(
            query = "pat",
            isGroup = true,
            threadKey = 200_000L,
            participants = listOf(maya, dev),
        )
        assertEquals(listOf(MentionSuggestion.Person(dev)), filtered)

        val everyoneFiltered = mentionSuggestions(
            query = "every",
            isGroup = true,
            threadKey = 200_000L,
            participants = listOf(maya, dev),
        )
        assertEquals(listOf(MentionSuggestion.Everyone(200_000L)), everyoneFiltered)
    }

    @Test
    fun applySuggestionAndReconcilePreservesOffsetsAcrossEdits() {
        val query = MentionQuery(start = 4, end = 7, query = "ma")
        val inserted = applyMentionSuggestion(
            text = "hey @ma",
            query = query,
            suggestion = MentionSuggestion.Person(maya),
            existing = emptyList(),
        )
        assertEquals("hey @maya.chen ", inserted.text)
        assertEquals(15, inserted.cursor)
        assertEquals(
            listOf(DraftMention(start = 4, end = 14, type = MentionTypes.PERSON, fbid = 101L, text = "@maya.chen")),
            inserted.mentions,
        )

        // Prepending text shifts the tracked mention forward.
        val shifted = reconcileDraftMentions("oh! hey @maya.chen ", inserted.mentions)
        assertEquals(
            listOf(DraftMention(start = 8, end = 18, type = MentionTypes.PERSON, fbid = 101L, text = "@maya.chen")),
            shifted,
        )

        // Deleting inside the mention drops it.
        val broken = reconcileDraftMentions("oh! hey @maya.che ", shifted)
        assertTrue(broken.isEmpty())
    }

    @Test
    fun resolveOutgoingMentionsAdjustsForLeadingTrimAndIncludesEveryone() {
        val raw = "  👋 @maya.chen and @everyone!  "
        val tracked = listOf(
            DraftMention(start = 5, end = 15, type = MentionTypes.PERSON, fbid = 101L, text = "@maya.chen"),
            DraftMention(start = 20, end = 29, type = MentionTypes.EVERYONE, fbid = 200_000L, text = "@everyone"),
        )
        val outgoing = resolveOutgoingMentions(raw, tracked)
        assertEquals(
            listOf(
                MessageMention(offset = 3, length = 10, type = MentionTypes.PERSON, fbid = 101L),
                MessageMention(offset = 18, length = 9, type = MentionTypes.EVERYONE, fbid = 200_000L),
            ),
            outgoing,
        )
    }

    @Test
    fun buildMentionAnnotatedStringHighlightsValidSpansAndIgnoresBadBounds() {
        val text = "👋 @maya.chen and @everyone"
        val annotated = buildMentionAnnotatedString(
            text = text,
            mentions = listOf(
                MessageMention(offset = 3, length = 10, type = MentionTypes.PERSON, fbid = 101L),
                MessageMention(offset = 18, length = 9, type = MentionTypes.EVERYONE, fbid = 200_000L),
                MessageMention(offset = -1, length = 4, type = MentionTypes.PERSON, fbid = 1L),
                MessageMention(offset = 100, length = 5, type = MentionTypes.PERSON, fbid = 2L),
            ),
            highlightColor = Color.Blue,
            highlightBackground = Color.Cyan,
        )
        assertEquals(text, annotated.text)
        assertEquals(2, annotated.spanStyles.size)
        assertEquals(3, annotated.spanStyles[0].start)
        assertEquals(13, annotated.spanStyles[0].end)
        assertEquals(18, annotated.spanStyles[1].start)
        assertEquals(27, annotated.spanStyles[1].end)
    }

    @Test
    fun mentionsJsonRoundTrips() {
        val input = listOf(
            MessageMention(offset = 12, length = 9, type = MentionTypes.EVERYONE, fbid = 200_000L),
            MessageMention(offset = 0, length = 5, type = MentionTypes.PERSON, fbid = 42L),
        )
        val encoded = mentionsToJson(input)
        val decoded = parseMentionsJson(encoded)
        assertEquals(input.sortedBy { it.offset }, decoded)
    }
}
