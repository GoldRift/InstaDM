package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.ConversationParticipant
import com.goldrift.instadm.ui.chat.directConversationForRecipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Picking a search result must open the person's DM, never a group they also
 * belong to. Only a matching direct chat may answer for a recipient.
 */
class RecipientConversationTest {
    private fun participant(fbid: Long, name: String, igId: String = "") =
        ConversationParticipant(
            fbid = fbid,
            name = name,
            initials = name.take(1),
            igId = igId,
        )

    private fun conversation(
        id: String,
        name: String,
        isGroup: Boolean,
        participants: List<ConversationParticipant>,
    ) = Conversation(
        id = id,
        contact = Contact(id = id, name = name, initials = name.take(1)),
        isGroup = isGroup,
        participants = participants,
    )

    @Test
    fun groupContainingThePersonIsNotTheRecipient() {
        // The bug: a group row that happens to list the person answered for them.
        val group = conversation(
            id = "group-thread",
            name = "Weekend Plans",
            isGroup = true,
            participants = listOf(participant(fbid = 1001L, name = "Maya")),
        )

        val result = listOf(group).directConversationForRecipient(
            recipientFbid = "1001",
            igId = "",
        )

        assertNull(result)
    }

    @Test
    fun directChatWinsEvenWhenTheGroupIsListedFirst() {
        val group = conversation(
            id = "group-thread",
            name = "Weekend Plans",
            isGroup = true,
            participants = listOf(participant(fbid = 1001L, name = "Maya")),
        )
        val direct = conversation(
            id = "direct-thread",
            name = "Maya",
            isGroup = false,
            participants = listOf(participant(fbid = 1001L, name = "Maya")),
        )

        val result = listOf(group, direct).directConversationForRecipient(
            recipientFbid = "1001",
            igId = "",
        )

        assertEquals("direct-thread", result?.id)
    }

    @Test
    fun matchesDirectChatByIgIdWhenTheFbidIsMissing() {
        val group = conversation(
            id = "group-thread",
            name = "Weekend Plans",
            isGroup = true,
            participants = listOf(participant(fbid = 0L, name = "Maya", igId = "123")),
        )
        val direct = conversation(
            id = "direct-thread",
            name = "Maya",
            isGroup = false,
            participants = listOf(participant(fbid = 0L, name = "Maya", igId = "123")),
        )

        val result = listOf(group, direct).directConversationForRecipient(
            recipientFbid = "",
            igId = "123",
        )

        assertEquals("direct-thread", result?.id)
    }

    @Test
    fun returnsNullWhenNoConversationContainsThePerson() {
        val other = conversation(
            id = "other-thread",
            name = "Dev",
            isGroup = false,
            participants = listOf(participant(fbid = 2002L, name = "Dev")),
        )

        val result = listOf(other).directConversationForRecipient(
            recipientFbid = "1001",
            igId = "123",
        )

        assertNull(result)
    }

    @Test
    fun blankKeysNeverMatchTheZeroFbidParticipant() {
        // A participant parsed without an FBID has fbid 0; a blank search must
        // not collapse that into "0" and open a stranger's chat.
        val blankParticipant = conversation(
            id = "blank-thread",
            name = "Unknown",
            isGroup = false,
            participants = listOf(participant(fbid = 0L, name = "Unknown", igId = "")),
        )

        val result = listOf(blankParticipant).directConversationForRecipient(
            recipientFbid = "",
            igId = "",
        )

        assertNull(result)
    }
}
