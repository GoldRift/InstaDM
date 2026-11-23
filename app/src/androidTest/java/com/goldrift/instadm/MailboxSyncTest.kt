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
    fun markedUnreadThreadIsFetchedButKeepsItsFlag() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestMailbox(coldStartMailbox(markedUnread = true))
        // No realtime messages: the incoming preview is the only evidence of a
        // message this process never saw, so the thread must be fetched.
        assertTrue(db.threads().thread("thread-1")!!.markedUnread)

        // The server still flags the thread unread and the receipt advances from
        // our empty watermark. A status fetch is not a read, so the dot survives.
        fake.threads["thread-1"] = JSONObject(THREAD_1_MARKED_UNREAD)
        mailboxSync.reconcileUnreadThreads()

        assertEquals(listOf("thread-1"), fake.loadedThreads)
        assertEquals(0, store.unreadCount("thread-1"))
        assertTrue(db.threads().thread("thread-1")!!.markedUnread)
    }

    @Test
    fun markedUnreadThreadWithOutgoingPreviewIsNotFetched() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestMailbox(coldStartMailbox(markedUnread = true, fromViewer = true))
        // Sending marks the thread read on the server, so an outgoing preview
        // has nothing new to show and spends no fetch.
        assertTrue(db.threads().thread("thread-1")!!.markedUnread)

        mailboxSync.reconcileUnreadThreads()

        assertTrue(fake.loadedThreads.isEmpty())
        assertTrue(db.threads().thread("thread-1")!!.markedUnread)
    }

    @Test
    fun coldStartBadgeAppearsForAMessageThatArrivedWhileDead() = runBlocking {
        // The inbox synced in the last process, then a message landed while this
        // process was dead; only the newer preview knows about it.
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestMailbox(coldStartMailbox())
        assertEquals(0, store.unreadCount("thread-1"))

        fake.threads["thread-1"] = JSONObject(THREAD_1_COLD_START)
        mailboxSync.reconcileUnreadThreads()

        assertEquals(listOf("thread-1"), fake.loadedThreads)
        assertEquals(1, store.unreadCount("thread-1"))
    }

    @Test
    fun coldStartPreviewReadElsewhereShowsNoBadge() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestMailbox(coldStartMailbox())
        assertEquals(0, store.unreadCount("thread-1"))

        // The viewer read the thread on another client while this process was
        // dead, so the fetched receipt settles the new preview.
        fake.threads["thread-1"] = JSONObject(THREAD_1_READ_ELSEWHERE)
        mailboxSync.reconcileUnreadThreads()

        assertEquals(listOf("thread-1"), fake.loadedThreads)
        assertEquals(0, store.unreadCount("thread-1"))
        assertEquals(1_700_000_005_000L, db.threads().thread("thread-1")!!.readWatermarkMs)
    }

    @Test
    fun aSecondPassHasNothingToFetch() = runBlocking {
        store.ingestMailbox(JSONObject(MAILBOX))
        store.ingestMailbox(coldStartMailbox())
        fake.threads["thread-1"] = JSONObject(THREAD_1_READ_ELSEWHERE)

        mailboxSync.reconcileUnreadThreads()
        assertEquals(listOf("thread-1"), fake.loadedThreads)

        // The fetch stored the page and the receipt covered it, so the preview
        // now matches the cache and nothing is unread.
        mailboxSync.reconcileUnreadThreads()

        assertEquals(listOf("thread-1"), fake.loadedThreads)
    }

    @Test
    fun missingBadgesTakePriorityOverAlreadyUnreadThreads() = runBlocking {
        store.ingestMailbox(threeThreadMailbox())
        // thread-1 and thread-2 already show a badge from cached rows; their
        // inbox previews are older than those rows.
        store.ingestRealtimeMessage("thread-1", incoming("message-3", 5_000_000_005_000))
        store.ingestRealtimeMessage("thread-2", incoming("message-21", 5_000_000_004_000))
        assertEquals(1, store.unreadCount("thread-1"))
        assertEquals(1, store.unreadCount("thread-2"))
        // thread-3 has no cached messages: only a fetch can produce its badge,
        // and its activity sits below both threads that already show one.
        assertEquals(0, store.unreadCount("thread-3"))

        fake.threads["thread-3"] = JSONObject(THREAD_3_COLD_START)
        fake.threads["thread-1"] = JSONObject(THREAD_1_LATE_READ_ELSEWHERE)

        mailboxSync.reconcileUnreadThreads(limit = 2)

        // Spending the pass on threads that already show a badge would starve
        // the thread that has none, so the missing badge goes first.
        assertEquals(listOf("thread-3", "thread-1"), fake.loadedThreads)
        assertEquals(1, store.unreadCount("thread-2"))
        assertEquals(1, store.unreadCount("thread-3"))
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

        /**
         * [MAILBOX] after a message landed while this process was dead: thread-1's
         * preview and activity move to [timestampMs]. [fromViewer] makes the
         * preview the viewer's own send; [markedUnread] carries the server's flag.
         * Only thread-1 is included so an exact fetch list stays meaningful.
         */
        fun coldStartMailbox(
            markedUnread: Boolean = false,
            fromViewer: Boolean = false,
            timestampMs: Long = 1_700_000_005_000L,
        ): JSONObject {
            val mailbox = JSONObject(MAILBOX)
            val thread = mailbox.getJSONArray("threads").getJSONObject(0)
            thread.put("last_activity_ms", timestampMs)
            thread.put("marked_as_unread", markedUnread)
            thread.getJSONObject("last_message").apply {
                put("id", if (fromViewer) "message-4" else "message-3")
                put("sender_fbid", if (fromViewer) 111 else 222)
                put("sender_name", if (fromViewer) "Me" else "Test Participant")
                put("is_from_viewer", fromViewer)
                put("timestamp_ms", timestampMs)
                put("text", if (fromViewer) "sent before the process died" else "arrived while dead")
            }
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

        /** thread-3, whose newest message arrived while this process was dead. */
        val THREAD_3_STUB = """
            {
              "id": "thread-3",
              "thread_fbid": "thread-3",
              "thread_id": "long-thread-3",
              "thread_key": 444,
              "is_group": false,
              "title": "Third Participant",
              "participants": [
                { "ig_id": "ig-third", "fbid": 444, "username": "third", "name": "Third Participant" }
              ],
              "last_activity_ms": 1700000003000,
              "folder": "PRIMARY",
              "system_folder": "INBOX",
              "marked_as_unread": false,
              "last_message": {
                "id": "message-33",
                "sender_fbid": 444,
                "sender_name": "Third Participant",
                "is_from_viewer": false,
                "timestamp_ms": 1700000003000,
                "kind": "text",
                "text": "arrived while dead"
              }
            }
        """.trimIndent()

        /** [MAILBOX_TWO_THREADS] plus [THREAD_3_STUB]; thread-3 has no cached messages. */
        fun threeThreadMailbox(): JSONObject {
            val mailbox = JSONObject(MAILBOX_TWO_THREADS)
            mailbox.getJSONArray("threads").put(JSONObject(THREAD_3_STUB))
            return mailbox
        }

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

        /** Thread-1 detail with the message that arrived while the process was dead, unread here. */
        val THREAD_1_COLD_START = """
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
                  "text": "arrived while dead",
                  "is_from_viewer": false
                }
              ],
              "read_receipts": [],
              "has_more_older": false
            }
        """.trimIndent()

        /**
         * [THREAD_1_COLD_START] on a thread the server still flags unread: the
         * viewer receipt covers the message, but the dot must survive the fetch.
         */
        val THREAD_1_MARKED_UNREAD = """
            {
              "protocol_version": "test",
              "thread": {
                "id": "thread-1",
                "thread_fbid": "thread-1",
                "thread_id": "long-thread-1",
                "title": "Test Participant",
                "last_activity_ms": 1700000005000,
                "marked_as_unread": true
              },
              "messages": [
                {
                  "id": "message-3",
                  "sender_fbid": 222,
                  "sender_name": "Test Participant",
                  "timestamp_ms": 1700000005000,
                  "kind": "text",
                  "text": "arrived while dead",
                  "is_from_viewer": false
                }
              ],
              "read_receipts": [
                { "participant_fbid": 111, "watermark_ms": 1700000005000, "from_viewer": true }
              ],
              "has_more_older": false
            }
        """.trimIndent()

        /** Thread-3 detail with the message only the inbox preview knew about. */
        val THREAD_3_COLD_START = """
            {
              "protocol_version": "test",
              "thread": {
                "id": "thread-3",
                "thread_fbid": "thread-3",
                "thread_id": "long-thread-3",
                "title": "Third Participant",
                "last_activity_ms": 1700000003000
              },
              "messages": [
                {
                  "id": "message-33",
                  "sender_fbid": 444,
                  "sender_name": "Third Participant",
                  "timestamp_ms": 1700000003000,
                  "kind": "text",
                  "text": "arrived while dead",
                  "is_from_viewer": false
                }
              ],
              "read_receipts": [],
              "has_more_older": false
            }
        """.trimIndent()

        /** Thread-1 detail whose viewer receipt covers the high-timestamp cached message. */
        val THREAD_1_LATE_READ_ELSEWHERE = """
            {
              "protocol_version": "test",
              "thread": {
                "id": "thread-1",
                "thread_fbid": "thread-1",
                "thread_id": "long-thread-1",
                "title": "Test Participant",
                "last_activity_ms": 5000000005000
              },
              "messages": [
                {
                  "id": "message-3",
                  "sender_fbid": 222,
                  "sender_name": "Test Participant",
                  "timestamp_ms": 5000000005000,
                  "kind": "text",
                  "text": "read elsewhere",
                  "is_from_viewer": false
                }
              ],
              "read_receipts": [
                { "participant_fbid": 111, "watermark_ms": 5000000005000, "from_viewer": true }
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
