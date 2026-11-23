package com.goldrift.instadm

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.MailboxSync
import com.goldrift.instadm.data.RealtimeMessage
import com.goldrift.instadm.data.db.InstaDmDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Needs a device: Room and the in-memory database only run under
 * instrumentation, so these cases are unrun on the JVM (AGENTS.md).
 */
@RunWith(AndroidJUnit4::class)
class MailboxSyncTest {
    private lateinit var db: InstaDmDatabase
    private lateinit var store: ChatStore
    private lateinit var fake: FakeProtocol
    private lateinit var mailboxSync: MailboxSync

    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InstaDmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ChatStore(db) { now }
        fake = FakeProtocol(cookies = true)
        mailboxSync = MailboxSync(fake, store)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun unreadThreadReadElsewhereIsCleared() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestRealtimeMessage("thread-1", incoming("message-3", 1_700_000_005_000))
        assertEquals(1, store.unreadCount("thread-1"))

        // The read happened on another client while no watch was running, so
        // only the thread's own viewer receipt records it.
        fake.threads["thread-1"] = JSONObject(THREAD_1_READ_ELSEWHERE)
        mailboxSync.reconcileUnreadThreads()

        assertEquals(0, store.unreadCount("thread-1"))
        assertEquals(1_700_000_005_000L, db.threads().thread("thread-1")!!.readWatermarkMs)
    }

    @Test
    fun otherParticipantsReceiptDoesNotClear() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestRealtimeMessage("thread-1", incoming("message-3", 1_700_000_005_000))
        assertEquals(1, store.unreadCount("thread-1"))

        fake.threads["thread-1"] = JSONObject(THREAD_1_OTHER_SEEN)
        mailboxSync.reconcileUnreadThreads()

        assertEquals(1, store.unreadCount("thread-1"))
        assertEquals(0L, db.threads().thread("thread-1")!!.readWatermarkMs)
    }

    @Test
    fun passHonoursTheLimitNewestFirst() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX_TWO_THREADS))
        store.ingestRealtimeMessage("thread-1", incoming("message-3", 1_700_000_005_000))
        store.ingestRealtimeMessage("thread-2", incoming("message-21", 1_700_000_004_000))
        assertEquals(1, store.unreadCount("thread-1"))
        assertEquals(1, store.unreadCount("thread-2"))

        mailboxSync.reconcileUnreadThreads(limit = 1)

        assertEquals(listOf("thread-1"), fake.loadedThreads)
        assertEquals(1, store.unreadCount("thread-2"))
    }

    @Test
    fun failingThreadDoesNotStopThePass() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX_TWO_THREADS))
        store.ingestRealtimeMessage("thread-1", incoming("message-3", 1_700_000_005_000))
        store.ingestRealtimeMessage("thread-2", incoming("message-21", 1_700_000_004_000))
        fake.failingThreads += "thread-1"
        fake.threads["thread-2"] = JSONObject(THREAD_2_READ_ELSEWHERE)

        mailboxSync.reconcileUnreadThreads()

        assertEquals(setOf("thread-1", "thread-2"), fake.loadedThreads.toSet())
        assertEquals(1, store.unreadCount("thread-1"))
        assertEquals(0, store.unreadCount("thread-2"))
    }

    @Test
    fun markedUnreadOnlyThreadIsNotFetched() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX_TWO_THREADS))
        store.ingestMailbox(markedUnreadTwoThreadMailbox())
        // No realtime messages: thread-1 carries only the server's unread flag,
        // with nothing above its watermark, so there is no stale read to fetch.
        assertTrue(db.threads().thread("thread-1")!!.markedUnread)

        mailboxSync.reconcileUnreadThreads()

        assertTrue(fake.loadedThreads.isEmpty())
        assertTrue(db.threads().thread("thread-1")!!.markedUnread)
    }

    private fun incoming(id: String, timestampMs: Long) = RealtimeMessage(
        id = id,
        text = "read elsewhere",
        senderName = "Test Participant",
        senderFbid = 222,
        isFromViewer = false,
        timestampMs = timestampMs,
        kind = "text",
    )

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
                    "text": "see you soon"
                  }
                }
              ]
            }
        """.trimIndent()

        /** [MAILBOX_TWO_THREADS] with the server's marked-unread flag set on thread-1. */
        fun markedUnreadTwoThreadMailbox(): JSONObject {
            val mailbox = JSONObject(MAILBOX_TWO_THREADS)
            mailbox.getJSONArray("threads").getJSONObject(0).put("marked_as_unread", true)
            return mailbox
        }

        val MAILBOX_TWO_THREADS = """
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
                  "last_activity_ms": 1700000000000,
                  "folder": "PRIMARY",
                  "system_folder": "INBOX",
                  "marked_as_unread": false,
                  "last_message": {
                    "id": "message-1",
                    "sender_fbid": 222,
                    "sender_name": "Test Participant",
                    "is_from_viewer": false,
                    "timestamp_ms": 1700000000000,
                    "kind": "text",
                    "text": "see you soon"
                  }
                },
                {
                  "id": "thread-2",
                  "thread_fbid": "thread-2",
                  "thread_id": "long-thread-2",
                  "thread_key": 333,
                  "is_group": false,
                  "title": "Someone Else",
                  "participants": [
                    { "ig_id": "ig-third", "fbid": 333, "username": "third", "name": "Someone Else" }
                  ],
                  "last_activity_ms": 1700000003000,
                  "folder": "PRIMARY",
                  "system_folder": "INBOX",
                  "marked_as_unread": false,
                  "last_message": {
                    "id": "message-20",
                    "sender_fbid": 333,
                    "sender_name": "Someone Else",
                    "is_from_viewer": false,
                    "timestamp_ms": 1700000003000,
                    "kind": "text",
                    "text": "earlier"
                  }
                }
              ]
            }
        """.trimIndent()

        val THREAD_1_READ_ELSEWHERE = """
            {
              "protocol_version": "test",
              "thread": {
                "id": "thread-1",
                "thread_fbid": "thread-1",
                "thread_id": "long-thread-1",
                "title": "Test Participant",
                "last_activity_ms": 1700000005000
              },
              "messages": [
                {
                  "id": "message-3",
                  "sender_fbid": 222,
                  "sender_name": "Test Participant",
                  "timestamp_ms": 1700000005000,
                  "kind": "text",
                  "text": "read elsewhere",
                  "is_from_viewer": false
                }
              ],
              "read_receipts": [
                { "participant_fbid": 111, "watermark_ms": 1700000005000, "from_viewer": true }
              ],
              "has_more_older": false
            }
        """.trimIndent()

        val THREAD_1_OTHER_SEEN = """
            {
              "protocol_version": "test",
              "thread": {
                "id": "thread-1",
                "thread_fbid": "thread-1",
                "thread_id": "long-thread-1",
                "title": "Test Participant",
                "last_activity_ms": 1700000005000
              },
              "messages": [
                {
                  "id": "message-3",
                  "sender_fbid": 222,
                  "sender_name": "Test Participant",
                  "timestamp_ms": 1700000005000,
                  "kind": "text",
                  "text": "read elsewhere",
                  "is_from_viewer": false
                }
              ],
              "read_receipts": [
                { "participant_fbid": 222, "watermark_ms": 1700000005000, "from_viewer": false }
              ],
              "has_more_older": false
            }
        """.trimIndent()

        val THREAD_2_READ_ELSEWHERE = """
            {
              "protocol_version": "test",
              "thread": {
                "id": "thread-2",
                "thread_fbid": "thread-2",
                "thread_id": "long-thread-2",
                "title": "Someone Else",
                "last_activity_ms": 1700000004000
              },
              "messages": [
                {
                  "id": "message-21",
                  "sender_fbid": 333,
                  "sender_name": "Someone Else",
                  "timestamp_ms": 1700000004000,
                  "kind": "text",
                  "text": "read elsewhere",
                  "is_from_viewer": false
                }
              ],
              "read_receipts": [
                { "participant_fbid": 111, "watermark_ms": 1700000004000, "from_viewer": true }
              ],
              "has_more_older": false
            }
        """.trimIndent()
    }
}
