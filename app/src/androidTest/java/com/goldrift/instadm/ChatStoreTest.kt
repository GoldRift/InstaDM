package com.goldrift.instadm

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.RealtimeMessage
import com.goldrift.instadm.data.db.InstaDmDatabase
import com.goldrift.instadm.data.db.MessageEntity
import com.goldrift.instadm.data.db.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatStoreTest {
    private lateinit var db: InstaDmDatabase
    private lateinit var store: ChatStore

    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InstaDmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ChatStore(db) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun mailboxStoresThreadPreviewAndParticipants() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))

        val inbox = store.observeInbox().first()
        assertEquals(1, inbox.size)
        val row = inbox.single()
        assertEquals("thread-1", row.thread.id)
        assertEquals("Test Participant", row.thread.title)
        assertEquals(1_700_000_000_000L, row.thread.lastActivityMs)
        assertEquals("message-1", row.thread.lastMessageId)
        assertEquals("see you soon", row.thread.lastMessageText)
        assertEquals("text", row.thread.lastMessageKind)
        assertTrue(!row.thread.lastMessageFromViewer)
        assertEquals(1, row.participants.size)
        assertEquals("Test Participant", row.participants.single().name)
        assertEquals(now, store.inboxSyncedAt())
        assertEquals("viewer-ig", store.viewer("viewer_ig_id"))
        val viewer = store.viewerProfile()
        assertEquals("Me", viewer.name)
        assertEquals("https://cdn.example.invalid/me.jpg", viewer.avatarUrl)
    }

    @Test
    fun ingestingTheSameMailboxTwiceDoesNotDuplicate() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestMailbox(JSONObject(MAILBOX))

        val inbox = store.observeInbox().first()
        assertEquals(1, inbox.size)
        assertEquals(1, inbox.single().participants.size)
    }

    @Test
    fun threadStoresMessagesAndUpdatesPreview() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))

        val messages = store.observeThread("thread-1").first()
        assertEquals(2, messages.size)
        assertEquals("message-1", messages[0].message.id)
        assertEquals("hello", messages[0].message.text)
        assertEquals("message-2", messages[1].message.id)
        assertEquals(1, messages[1].attachments.size)
        assertEquals("image", messages[1].attachments.single().kind)
        assertTrue(messages[1].message.isFromViewer)

        val thread = store.observeInbox().first().single().thread
        assertEquals("message-2", thread.lastMessageId)
        assertEquals("", thread.lastMessageText)
        assertEquals("image", thread.lastMessageKind)
        assertTrue(thread.lastMessageFromViewer)
        assertTrue(thread.hasMoreOlder)
        assertEquals("older-cursor", thread.oldestCursor)
        assertEquals(now, thread.messagesSyncedAt)
    }

    @Test
    fun ingestingTheSameThreadDoesNotDuplicateMessages() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))
        store.ingestThread(JSONObject(THREAD))

        assertEquals(2, store.observeThread("thread-1").first().size)
    }

    @Test
    fun mailboxRefreshKeepsLocalPagingState() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))
        store.ingestMailbox(JSONObject(MAILBOX))

        val thread = store.observeInbox().first().single().thread
        assertEquals("older-cursor", thread.oldestCursor)
        assertTrue(thread.hasMoreOlder)
        assertEquals(now, thread.messagesSyncedAt)
        assertEquals(2, store.observeThread("thread-1").first().size)
    }

    @Test
    fun olderPageAddsMessagesWithoutTouchingThePreview() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))
        store.ingestOlderPage(JSONObject(OLDER_PAGE))

        val messages = store.observeThread("thread-1").first()
        assertEquals(3, messages.size)
        assertEquals("message-0", messages.first().message.id)
        assertEquals("end-cursor", store.conversationThread("thread-1").oldestCursor)
        assertEquals("message-2", store.conversationThread("thread-1").lastMessageId)
    }

    @Test
    fun threadWithReadReceiptsMarksViewerMessagesSeen() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))

        val messages = store.observeThread("thread-1").first()
        assertTrue(messages[1].message.seen)
        assertEquals(1_700_000_002_000L, store.conversationThread("thread-1").readWatermarkMs)
    }

    @Test
    fun tombstonedMessagesStayGoneAfterReingest() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))
        db.messages().tombstone("message-2", now)

        store.ingestThread(JSONObject(THREAD))

        val messages = store.observeThread("thread-1").first()
        assertEquals(1, messages.size)
        assertEquals("message-1", messages.single().message.id)
    }

    @Test
    fun completingAMediaSendKeepsTheLocalFile() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "outbox-media-${System.nanoTime()}.jpg")
        file.writeBytes(byteArrayOf(1, 2, 3))

        val localId = "local:media"
        db.messages().upsertMessage(
            MessageEntity(
                id = localId,
                localId = localId,
                threadId = "thread-1",
                timestampMs = now,
                kind = "image",
                isFromViewer = true,
                status = MessageStatus.PENDING,
                mediaCount = 1,
            ),
        )

        store.completeOutgoingMedia(
            localMessageId = localId,
            threadId = "thread-1",
            result = JSONObject("""{ "message_id": "mid.9", "timestamp_ms": 1700000009000 }"""),
            kind = "image",
            localPath = file.absolutePath,
            width = 800,
            height = 600,
            durationMs = 0,
        )

        val row = store.observeThread("thread-1").first().single()
        assertEquals("mid.9", row.message.id)
        assertEquals(MessageStatus.SENT, row.message.status)
        assertEquals("image", row.message.kind)
        assertEquals(file.absolutePath, row.attachments.single().localPath)
        assertTrue(row.attachments.single().url.startsWith("file:"))
        assertEquals("mid.9", store.conversationThread("thread-1").lastMessageId)
        file.delete()
        Unit
    }

    @Test
    fun unknownThreadPageCreatesAStubRow() = runBlocking {
        store.ingestOlderPage(JSONObject(OLDER_PAGE))

        val thread = store.conversationThread("thread-1")
        assertEquals("thread-1", thread.id)
        assertEquals(1, store.observeThread("thread-1").first().size)
    }

    @Test
    fun clearingTheStoreRemovesEverything() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.clear()

        assertEquals(0, store.observeInbox().first().size)
        assertNull(store.inboxSyncedAt())
    }

    @Test
    fun newerMailboxStubMarksTheThreadStale() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))
        assertEquals(now, store.conversationThread("thread-1").messagesSyncedAt)

        store.ingestMailbox(JSONObject(MAILBOX_NEWER))

        val thread = store.conversationThread("thread-1")
        assertEquals("message-3", thread.lastMessageId)
        assertEquals(0L, thread.messagesSyncedAt)
    }

    @Test
    fun unchangedMailboxKeepsTheThreadFresh() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))
        store.ingestMailbox(JSONObject(MAILBOX))

        assertEquals(now, store.conversationThread("thread-1").messagesSyncedAt)
    }

    @Test
    fun realtimeMessageUpdatesThePreviewWithoutTouchingThePage() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))

        store.ingestRealtimeMessage(
            "thread-1",
            RealtimeMessage(
                id = "message-3",
                offlineThreadingId = "otid-3",
                text = "brand new",
                senderName = "Test Participant",
                senderFbid = 222,
                isFromViewer = false,
                timestampMs = 1_700_000_003_000,
                kind = "text",
            ),
        )

        val messages = store.observeThread("thread-1").first()
        assertEquals(1, messages.size)
        assertEquals("message-3", messages.single().message.id)
        assertEquals("brand new", messages.single().message.text)

        val thread = store.conversationThread("thread-1")
        assertEquals("message-3", thread.lastMessageId)
        assertEquals("brand new", thread.lastMessageText)
        assertEquals(1_700_000_003_000L, thread.lastActivityMs)
        assertEquals(0L, thread.messagesSyncedAt)
    }

    @Test
    fun olderPageDoesNotOverwriteANewerRealtimePreview() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestRealtimeMessage(
            "thread-1",
            RealtimeMessage(
                id = "message-3",
                text = "brand new",
                senderName = "Test Participant",
                senderFbid = 222,
                isFromViewer = false,
                timestampMs = 1_700_000_003_000,
                kind = "text",
            ),
        )

        store.ingestThread(JSONObject(THREAD))

        val thread = store.conversationThread("thread-1")
        assertEquals("message-3", thread.lastMessageId)
        assertEquals("brand new", thread.lastMessageText)
        assertEquals(3, store.observeThread("thread-1").first().size)
    }

    @Test
    fun pageRefreshKeepsReplySnapshotFromAPendingSend() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        val localId = "local:reply"
        db.messages().upsertMessage(
            MessageEntity(
                id = localId,
                localId = localId,
                threadId = "thread-1",
                timestampMs = 1_700_000_004_000,
                kind = "text",
                text = "reply text",
                replyToId = "message-1",
                replyToText = "hello",
                replyToSender = "Test Participant",
                replyToKind = "text",
                otid = "otid-4",
                isFromViewer = true,
                status = MessageStatus.PENDING,
            ),
        )

        store.ingestOlderPage(
            JSONObject(
                """
                {
                  "thread_id": "thread-1",
                  "messages": [
                    {
                      "id": "message-9",
                      "sender_fbid": 111,
                      "timestamp_ms": 1700000004000,
                      "kind": "text",
                      "text": "reply text",
                      "is_from_viewer": true,
                      "offline_threading_id": "otid-4"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val message = store.observeThread("thread-1").first().single().message
        assertEquals("message-9", message.id)
        assertEquals("message-1", message.replyToId)
        assertEquals("hello", message.replyToText)
        assertEquals("Test Participant", message.replyToSender)
    }

    @Test
    fun textCompletionAfterTheEchoKeepsTheReplySnapshot() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        val localId = "local:reply"
        db.messages().upsertMessage(
            MessageEntity(
                id = localId,
                localId = localId,
                threadId = "thread-1",
                timestampMs = 1_700_000_004_000,
                kind = "text",
                text = "reply text",
                replyToId = "message-1",
                replyToText = "hello",
                replyToSender = "Test Participant",
                replyToKind = "text",
                otid = "otid-4",
                isFromViewer = true,
                status = MessageStatus.PENDING,
            ),
        )

        // The realtime echo lands before the HTTP response and omits the quote.
        store.ingestRealtimeMessage(
            "thread-1",
            RealtimeMessage(
                id = "message-9",
                offlineThreadingId = "otid-4",
                text = "reply text",
                senderName = "Me",
                senderFbid = 111,
                isFromViewer = true,
                timestampMs = 1_700_000_004_000,
                kind = "text",
            ),
        )

        store.completeOutgoingText(
            localMessageId = localId,
            threadId = "thread-1",
            result = JSONObject(
                """{ "message_id": "message-9", "offline_threading_id": "otid-4", "timestamp_ms": 1700000004000 }""",
            ),
            fallbackText = "reply text",
        )

        val message = store.observeThread("thread-1").first().single().message
        assertEquals("message-9", message.id)
        assertEquals("message-1", message.replyToId)
        assertEquals("hello", message.replyToText)
        assertEquals("Test Participant", message.replyToSender)
    }

    @Test
    fun sentTextIsStoredImmediately() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))

        store.ingestSentText(
            "thread-1",
            JSONObject(
                """
                {
                  "message_id": "message-9",
                  "offline_threading_id": "otid-9",
                  "timestamp_ms": 1700000009000,
                  "text": "sent from the app"
                }
                """.trimIndent(),
            ),
            fallbackText = "sent from the app",
        )

        val message = store.observeThread("thread-1").first().single().message
        assertEquals("message-9", message.id)
        assertEquals("sent from the app", message.text)
        assertTrue(message.isFromViewer)
        assertEquals("otid-9", message.otid)
        assertEquals("sent from the app", store.conversationThread("thread-1").lastMessageText)
    }

    @Test
    fun editAndTombstoneUpdateThePreview() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))

        store.editMessageText("message-1", "edited hello")
        assertEquals("edited hello", store.observeThread("thread-1").first()[0].message.text)

        store.tombstoneMessage("message-2")
        val messages = store.observeThread("thread-1").first()
        assertEquals(1, messages.size)
        assertEquals("message-1", messages.single().message.id)
        val thread = store.conversationThread("thread-1")
        assertEquals("message-1", thread.lastMessageId)
        assertEquals("edited hello", thread.lastMessageText)
    }

    @Test
    fun olderPageThatAddsNothingStopsPaging() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(JSONObject(THREAD))

        store.ingestOlderPage(
            JSONObject(
                """
                {
                  "thread_id": "thread-1",
                  "messages": [
                    {
                      "id": "message-1",
                      "sender_fbid": 222,
                      "timestamp_ms": 1700000000000,
                      "kind": "text",
                      "text": "hello"
                    }
                  ],
                  "has_more_older": true,
                  "oldest_cursor": "same-cursor"
                }
                """.trimIndent(),
            ),
        )

        val thread = store.conversationThread("thread-1")
        assertFalse(thread.hasMoreOlder)
        assertEquals("same-cursor", thread.oldestCursor)
    }

    @Test
    fun threadStoresViewOnceAttachmentWithoutAUrl() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestThread(
            JSONObject(
                """
                {
                  "thread": {
                    "id": "thread-1",
                    "thread_fbid": "thread-1",
                    "thread_id": "long-thread-1",
                    "title": "Test Participant"
                  },
                  "messages": [
                    {
                      "id": "message-view-once",
                      "sender_fbid": 222,
                      "timestamp_ms": 1700000000000,
                      "kind": "image",
                      "media_count": 1,
                      "is_from_viewer": false,
                      "media": [
                        { "type": "image", "view_once": true, "view_mode": "viewed" }
                      ]
                    }
                  ],
                  "has_more_older": false
                }
                """.trimIndent(),
            ),
        )

        val attachment = store.observeThread("thread-1").first().single().attachments.single()
        assertEquals("image", attachment.kind)
        assertTrue(attachment.viewOnce)
        assertEquals("viewed", attachment.viewMode)
        assertEquals("", attachment.url)
    }

    private suspend fun ChatStore.conversationThread(threadId: String) =
        db.threads().thread(threadId) ?: error("missing thread $threadId")

    private companion object {
        val MAILBOX = """
            {
              "viewer": { "ig_id": "viewer-ig", "username": "me", "name": "Me", "fbid": 111, "avatar_url": "https://cdn.example.invalid/me.jpg" },
              "threads": [
                {
                  "id": "thread-1",
                  "thread_fbid": "thread-1",
                  "thread_id": "long-thread-1",
                  "thread_key": 222,
                  "is_group": false,
                  "title": "Test Participant",
                  "avatar_url": "https://cdn.example.invalid/other.jpg",
                  "participants": [
                    { "ig_id": "ig-other", "fbid": 222, "username": "other", "name": "Test Participant" }
                  ],
                  "last_activity_ms": 1700000000000,
                  "folder": "PRIMARY",
                  "system_folder": "INBOX",
                  "is_muted": false,
                  "is_pinned": false,
                  "marked_as_unread": false,
                  "last_message": {
                    "id": "message-1",
                    "sender_fbid": 222,
                    "sender_name": "Test Participant",
                    "is_from_viewer": false,
                    "timestamp_ms": 1700000000000,
                    "kind": "text",
                    "text": "see you soon",
                    "snippet": "Test Participant: see you soon"
                  }
                }
              ]
            }
        """.trimIndent()

        val THREAD = """
            {
              "protocol_version": "test",
              "thread": {
                "id": "thread-1",
                "thread_fbid": "thread-1",
                "thread_id": "long-thread-1",
                "title": "Test Participant",
                "participants": [
                  { "ig_id": "ig-me", "fbid": 111, "name": "Me", "is_viewer": true },
                  { "ig_id": "ig-other", "fbid": 222, "username": "other", "name": "Test Participant" }
                ],
                "last_activity_ms": 1700000000500
              },
              "messages": [
                {
                  "id": "message-1",
                  "sender_fbid": 222,
                  "sender_name": "Test Participant",
                  "timestamp_ms": 1700000000000,
                  "kind": "text",
                  "text": "hello",
                  "is_from_viewer": false
                },
                {
                  "id": "message-2",
                  "sender_fbid": 111,
                  "sender_name": "Me",
                  "timestamp_ms": 1700000000500,
                  "kind": "image",
                  "media_count": 1,
                  "is_from_viewer": true,
                  "offline_threading_id": "otid-2",
                  "media": [
                    {
                      "type": "image",
                      "attachment_fbid": "attachment-1",
                      "url": "https://cdn.example.invalid/image.jpg",
                      "preview_url": "https://cdn.example.invalid/image-preview.jpg",
                      "width": 800,
                      "height": 600
                    }
                  ]
                }
              ],
              "read_receipts": [
                { "participant_fbid": 111, "watermark_ms": 1700000002000, "from_viewer": true },
                { "participant_fbid": 222, "watermark_ms": 1700000000600 }
              ],
              "has_more_older": true,
              "oldest_cursor": "older-cursor"
            }
        """.trimIndent()

        val MAILBOX_NEWER = """
            {
              "viewer": { "ig_id": "viewer-ig", "username": "me", "name": "Me", "fbid": 111, "avatar_url": "https://cdn.example.invalid/me.jpg" },
              "threads": [
                {
                  "id": "thread-1",
                  "thread_fbid": "thread-1",
                  "thread_id": "long-thread-1",
                  "thread_key": 222,
                  "is_group": false,
                  "title": "Test Participant",
                  "participants": [
                    { "ig_id": "ig-other", "fbid": 222, "username": "other", "name": "Test Participant" }
                  ],
                  "last_activity_ms": 1700000003000,
                  "folder": "PRIMARY",
                  "system_folder": "INBOX",
                  "last_message": {
                    "id": "message-3",
                    "sender_fbid": 222,
                    "is_from_viewer": false,
                    "timestamp_ms": 1700000003000,
                    "kind": "text",
                    "text": "brand new"
                  }
                }
              ]
            }
        """.trimIndent()

        val OLDER_PAGE = """
            {
              "protocol_version": "test",
              "thread_id": "thread-1",
              "messages": [
                {
                  "id": "message-0",
                  "sender_fbid": 222,
                  "timestamp_ms": 1699999999000,
                  "kind": "text",
                  "text": "hello there",
                  "is_from_viewer": false
                }
              ],
              "has_more_older": false,
              "oldest_cursor": "end-cursor"
            }
        """.trimIndent()
    }
}
