package com.goldrift.instadm

import com.goldrift.instadm.data.chunkedQuery
import com.goldrift.instadm.data.db.AttachmentEntity
import com.goldrift.instadm.data.db.MessageEntity
import com.goldrift.instadm.data.db.MessageStatus
import com.goldrift.instadm.data.parseReadReceipts
import com.goldrift.instadm.data.parseThreadMessages
import com.goldrift.instadm.data.planServerMessageUpsert
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatStoreBatchTest {

    @Test
    fun planSkipsTombstonesReconcilesOtidAndOnlyDeletesExistingChildRows() {
        val parsed = parseThreadMessages(
            JSONArray(
                """
                [
                  { "id": "mid.deleted", "sender_fbid": 2, "timestamp_ms": 100, "text": "revive?", "kind": "text" },
                  {
                    "id": "mid.echo",
                    "offline_threading_id": "otid-1",
                    "sender_fbid": 1,
                    "timestamp_ms": 5000,
                    "kind": "audio",
                    "is_from_viewer": true,
                    "media_count": 1,
                    "media": [
                      { "type": "audio", "url": "https://cdn.example.invalid/a.m4a", "duration_ms": 2000 }
                    ]
                  },
                  {
                    "id": "mid.plain",
                    "sender_fbid": 2,
                    "timestamp_ms": 6000,
                    "text": "hello",
                    "kind": "text",
                    "reactions": [
                      { "emoji": "❤️", "sender_fbid": 1, "is_from_viewer": true, "timestamp_ms": 6100 }
                    ]
                  },
                  {
                    "id": "mid.cleared",
                    "sender_fbid": 2,
                    "timestamp_ms": 7000,
                    "text": "no reactions now",
                    "kind": "text"
                  }
                ]
                """.trimIndent(),
            ),
        )

        val tombstone = MessageEntity(
            id = "mid.deleted",
            threadId = "t1",
            status = MessageStatus.DELETED,
        )
        val pending = MessageEntity(
            id = "local:1",
            localId = "local:1",
            threadId = "t1",
            timestampMs = 4500,
            kind = "audio",
            replyToId = "mid.orig",
            replyToText = "quoted",
            otid = "otid-1",
            isFromViewer = true,
            status = MessageStatus.SENDING,
        )
        val pendingVoice = AttachmentEntity(
            messageId = "local:1",
            partIndex = 0,
            kind = "audio",
            localPath = "/tmp/voice.m4a",
            downloadState = AttachmentEntity.DOWNLOAD_READY,
            waveform = "[0.2,0.8]",
        )

        val plan = planServerMessageUpsert(
            threadId = "t1",
            parsed = parsed,
            existingById = mapOf("mid.deleted" to tombstone),
            existingByOtid = mapOf("otid-1" to listOf(pending)),
            duplicateAttachmentsByMsgId = mapOf("local:1" to listOf(pendingVoice)),
            existingAttachmentsByMsgId = emptyMap(),
            existingReactionMsgIds = setOf("mid.cleared"),
        )

        assertEquals(listOf("mid.echo", "mid.plain", "mid.cleared"), plan.messages.map { it.id })
        assertEquals(listOf("local:1"), plan.duplicateIdsToDelete)

        val echoed = plan.messages.first { it.id == "mid.echo" }
        assertEquals(5000L, echoed.timestampMs)
        assertEquals("mid.orig", echoed.replyToId)
        assertEquals("quoted", echoed.replyToText)

        // None of the upserted IDs had pre-existing attachments, so no DELETE is needed.
        assertEquals(emptyList<String>(), plan.attachmentIdsToDelete)
        assertEquals(1, plan.attachments.size)
        assertEquals("/tmp/voice.m4a", plan.attachments.single().localPath)
        assertEquals(AttachmentEntity.DOWNLOAD_READY, plan.attachments.single().downloadState)
        assertEquals("[0.2,0.8]", plan.attachments.single().waveform)

        // Only mid.cleared had pre-existing reactions, so mid.plain and mid.echo skip DELETE.
        assertEquals(listOf("mid.cleared"), plan.reactionIdsToDelete)
        assertEquals(1, plan.reactions.size)
        assertEquals("mid.plain", plan.reactions.single().messageId)
        assertEquals("❤️", plan.reactions.single().emoji)
    }

    @Test
    fun parseReadReceiptsTakesMaxViewerAndOtherWatermarks() {
        val receipts = parseReadReceipts(
            JSONArray(
                """
                [
                  { "watermark_ms": 1000, "from_viewer": true },
                  { "watermark_ms": 2500, "from_viewer": true },
                  { "watermark_ms": 1800, "from_viewer": false },
                  { "watermark_ms": 0, "from_viewer": false }
                ]
                """.trimIndent(),
            ),
        )
        assertEquals(2500L, receipts.viewerWatermark)
        assertEquals(1800L, receipts.otherWatermark)
    }

    @Test
    fun chunkedQuerySplitsLargeListsAtLimit() {
        val batches = mutableListOf<Int>()
        val result = chunkedQuery((1..1200).map { "id-$it" }, chunkSize = 500) { chunk ->
            batches += chunk.size
            chunk.map { it.uppercase() }
        }
        assertEquals(listOf(500, 500, 200), batches)
        assertEquals(1200, result.size)
        assertEquals("ID-1", result.first())
        assertEquals("ID-1200", result.last())
    }
}
