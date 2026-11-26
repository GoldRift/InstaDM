package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.ConversationParticipant
import com.goldrift.instadm.ui.chat.MAX_MESSAGE_LENGTH
import com.goldrift.instadm.ui.share.ShareCompletion
import com.goldrift.instadm.ui.share.canSendShare
import com.goldrift.instadm.ui.share.filterShareConversations
import com.goldrift.instadm.ui.share.pruneSelectedRecipients
import com.goldrift.instadm.ui.share.resolveShareCompletion
import com.goldrift.instadm.ui.share.shareTextLength
import com.goldrift.instadm.ui.share.toggleShareRecipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePickerLogicTest {
    private val dmMaya = Conversation(
        id = "t-maya",
        contact = Contact(id = "c-maya", name = "Maya Chen", initials = "MC"),
    )
    private val dmDev = Conversation(
        id = "t-dev",
        contact = Contact(id = "c-dev", name = "Dev Patel", initials = "DP"),
    )
    private val groupSupper = Conversation(
        id = "t-supper",
        contact = Contact(id = "c-supper", name = "Supper Club", initials = "SC"),
        isGroup = true,
        participants = listOf(
            ConversationParticipant(fbid = 101L, name = "Sofia Rossi", initials = "SR"),
            ConversationParticipant(fbid = 102L, name = "Liam O'Brien", initials = "LO"),
        ),
    )
    private val all = listOf(dmMaya, dmDev, groupSupper)

    @Test
    fun filterShareConversationsReturnsOriginalForBlankQuery() {
        assertSame(all, filterShareConversations(all, ""))
        assertSame(all, filterShareConversations(all, "   \n  "))
    }

    @Test
    fun filterShareConversationsMatchesTitleCaseInsensitively() {
        assertEquals(listOf(dmMaya), filterShareConversations(all, "maya"))
        assertEquals(listOf(dmDev), filterShareConversations(all, "  PATEL "))
    }

    @Test
    fun filterShareConversationsMatchesGroupTitleAndParticipants() {
        assertEquals(listOf(groupSupper), filterShareConversations(all, "supper"))
        assertEquals(listOf(groupSupper), filterShareConversations(all, "sofia"))
        assertEquals(listOf(groupSupper), filterShareConversations(all, "O'BRIEN"))
    }

    @Test
    fun filterShareConversationsReturnsEmptyWhenNoMatch() {
        assertEquals(emptyList<Conversation>(), filterShareConversations(all, "nobody"))
    }

    @Test
    fun toggleShareRecipientAppendsRemovesAndIgnoresBlankIds() {
        val one = toggleShareRecipient(emptyList(), "t-maya")
        assertEquals(listOf("t-maya"), one)

        val two = toggleShareRecipient(one, "t-supper")
        assertEquals(listOf("t-maya", "t-supper"), two)

        val three = toggleShareRecipient(two, "t-dev")
        assertEquals(listOf("t-maya", "t-supper", "t-dev"), three)

        val removedMiddle = toggleShareRecipient(three, "t-supper")
        assertEquals(listOf("t-maya", "t-dev"), removedMiddle)

        assertEquals(removedMiddle, toggleShareRecipient(removedMiddle, ""))
        assertEquals(removedMiddle, toggleShareRecipient(removedMiddle, "   "))
    }

    @Test
    fun pruneSelectedRecipientsKeepsOnlyExistingConversationsInSelectionOrder() {
        val selected = listOf("t-supper", "missing-id", "t-maya", "also-missing")
        assertEquals(listOf("t-supper", "t-maya"), pruneSelectedRecipients(selected, all))
    }

    @Test
    fun shareTextLengthTrimsWhitespaceAndCountsUnicodeCodePoints() {
        assertEquals(0, shareTextLength("   \n\t "))
        assertEquals(5, shareTextLength("  hello  "))
        assertEquals(3, shareTextLength(" 🎉🎉🎉 "))
        assertEquals(4, shareTextLength("hi🎉!"))
    }

    @Test
    fun canSendShareEnforcesRecipientsContentLimitsAndInFlightFlags() {
        assertFalse(
            canSendShare(
                selectedCount = 0,
                noteText = "hello",
                stagedMediaCount = 1,
                preparingMedia = false,
                isSending = false,
            ),
        )
        assertFalse(
            canSendShare(
                selectedCount = 2,
                noteText = "   ",
                stagedMediaCount = 0,
                preparingMedia = false,
                isSending = false,
            ),
        )
        assertFalse(
            canSendShare(
                selectedCount = 1,
                noteText = "hello",
                stagedMediaCount = 1,
                preparingMedia = true,
                isSending = false,
            ),
        )
        assertFalse(
            canSendShare(
                selectedCount = 1,
                noteText = "hello",
                stagedMediaCount = 1,
                preparingMedia = false,
                isSending = true,
            ),
        )
        assertFalse(
            canSendShare(
                selectedCount = 1,
                noteText = "🎉".repeat(MAX_MESSAGE_LENGTH + 1),
                stagedMediaCount = 0,
                preparingMedia = false,
                isSending = false,
            ),
        )
        assertTrue(
            canSendShare(
                selectedCount = 1,
                noteText = "🎉".repeat(MAX_MESSAGE_LENGTH),
                stagedMediaCount = 0,
                preparingMedia = false,
                isSending = false,
            ),
        )
        assertTrue(
            canSendShare(
                selectedCount = 3,
                noteText = "",
                stagedMediaCount = 2,
                preparingMedia = false,
                isSending = false,
            ),
        )
    }

    @Test
    fun resolveShareCompletionHandlesNoneSingleAndMultiple() {
        assertEquals(ShareCompletion.None, resolveShareCompletion(emptyList()))
        assertEquals(ShareCompletion.OpenChat("t-maya"), resolveShareCompletion(listOf("t-maya")))
        assertEquals(
            ShareCompletion.ReturnToInbox(2),
            resolveShareCompletion(listOf("t-maya", "t-dev")),
        )
    }
}
