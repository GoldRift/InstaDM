package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ConversationPreview
import com.goldrift.instadm.ui.chat.PreviewPart
import com.goldrift.instadm.ui.chat.label
import com.goldrift.instadm.ui.chat.render
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagePreviewTest {
    private fun Res(id: Int, vararg args: PreviewPart) = PreviewPart.Res(id, args.toList())

    @Test
    fun directIncomingMediaReadsAsASentence() {
        val label = ConversationPreview(kind = "video").label(isGroup = false)

        assertEquals(Res(R.string.inbox_sent_you, Res(R.string.inbox_noun_video)), label)
    }

    @Test
    fun voiceNotesAndStickersHaveTheirOwnNouns() {
        assertEquals(
            Res(R.string.inbox_noun_voice),
            mediaLabel(kind = "audio"),
        )
        assertEquals(
            Res(R.string.inbox_noun_sticker),
            mediaLabel(kind = "sticker"),
        )
    }

    @Test
    fun captionsWinOverTheAttachmentLabel() {
        val label = ConversationPreview(kind = "image", text = "look at this").label(isGroup = false)

        assertEquals(PreviewPart.Text("look at this"), label)
    }

    @Test
    fun groupSenderIsNamed() {
        val label = ConversationPreview(
            kind = "image",
            senderName = "Maya",
        ).label(isGroup = true)

        assertEquals(
            Res(R.string.inbox_sent_by, PreviewPart.Text("Maya"), Res(R.string.inbox_noun_photo)),
            label,
        )
    }

    @Test
    fun groupTextIsPrefixedWithTheSender() {
        val label = ConversationPreview(text = "see you at 8", senderName = "Maya").label(isGroup = true)

        assertEquals(
            Res(R.string.inbox_sender_prefix, PreviewPart.Text("Maya"), PreviewPart.Text("see you at 8")),
            label,
        )
    }

    @Test
    fun theViewerKeepsTheYouPrefix() {
        assertEquals(
            Res(R.string.inbox_you_prefix, PreviewPart.Text("on my way")),
            ConversationPreview(text = "on my way", fromViewer = true).label(isGroup = false),
        )
        assertEquals(
            Res(R.string.inbox_you_sent, Res(R.string.inbox_noun_photo)),
            ConversationPreview(kind = "image", fromViewer = true).label(isGroup = true),
        )
    }

    @Test
    fun unknownKindsFallBackToAnAttachment() {
        assertEquals(
            Res(R.string.inbox_noun_attachment),
            mediaLabel(kind = "music"),
        )
    }

    @Test
    fun nothingToShowMeansNoLabel() {
        assertNull(ConversationPreview().label(isGroup = false))
        assertNull(ConversationPreview(kind = "text").label(isGroup = true))
    }

    @Test
    fun nestedPartsResolveThroughTheCallerLookup() {
        val rendered = ConversationPreview(kind = "video").label(isGroup = false)
            ?.render { id, args ->
                when (id) {
                    R.string.inbox_sent_you -> "Sent you ${args.single()}"
                    R.string.inbox_noun_video -> "a video"
                    else -> error("unexpected string resource $id")
                }
            }

        assertEquals("Sent you a video", rendered)
    }

    private fun mediaLabel(kind: String): PreviewPart? =
        ConversationPreview(kind = kind)
            .label(isGroup = false)
            ?.let { (it as PreviewPart.Res).args.single() }
}
