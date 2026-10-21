package com.goldrift.instadm

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.db.InstaDmDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

    private suspend fun ChatStore.conversationThread(threadId: String) =
        db.threads().thread(threadId) ?: error("missing thread $threadId")

    private companion object {
        val MAILBOX = """
            {
              "viewer": { "ig_id": "viewer-ig", "username": "me", "name": "Me", "fbid": 111 },
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
