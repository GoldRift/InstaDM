package com.goldrift.instadm

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.MailboxSync
import com.goldrift.instadm.data.MediaReader
import com.goldrift.instadm.data.Outbox
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.RealtimeMessage
import com.goldrift.instadm.data.SettingsStore
import com.goldrift.instadm.data.db.InstaDmDatabase
import com.goldrift.instadm.service.AppVisibility
import com.goldrift.instadm.ui.chat.ChatBanner
import com.goldrift.instadm.ui.chat.InboxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * The banner is the in-app counterpart of a notification: a message in another
 * chat shows it while the app is in front, and the open chat, the viewer's own
 * messages, and a backgrounded app do not. The trigger is called directly so
 * each drop is asserted without a race against the event collector.
 */
@RunWith(AndroidJUnit4::class)
class ChatBannerTest {
    private lateinit var context: Context
    private lateinit var db: InstaDmDatabase
    private lateinit var store: ChatStore
    private lateinit var scope: CoroutineScope
    private lateinit var dir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InstaDmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ChatStore(db)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dir = File(context.cacheDir, "chat-banner-${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        AppVisibility.closeChat(OPEN)
        AppVisibility.isForeground = false
        scope.cancel()
        dir.deleteRecursively()
        db.close()
    }

    @Test
    fun onlyAnotherChatWhileInFrontShowsTheBanner() = runBlocking {
        val state = inboxState()
        AppVisibility.isForeground = true
        AppVisibility.openChat(OPEN)

        // These three are dropped before any coroutine starts, so the empty
        // state is the rule rather than a slow lookup.
        state.showBannerFor(incoming(OPEN, "in the open chat"))
        state.showBannerFor(incoming(OTHER, "sent by me", fromViewer = true))
        AppVisibility.isForeground = false
        state.showBannerFor(incoming(OTHER, "while backgrounded"))
        assertNull(state.banner)

        AppVisibility.isForeground = true
        state.showBannerFor(incoming(OTHER, "in the other chat"))
        val banner = awaitBanner(state)
        assertEquals(OTHER, banner.threadId)
        assertEquals("Test Participant", banner.title)
        assertEquals("in the other chat", banner.preview)

        // Opening that chat takes the banner down with it.
        state.openThread(OTHER)
        assertNull(state.banner)

        state.showBannerFor(incoming(OTHER, "again"))
        awaitBanner(state)
        state.dismissBanner()
        assertNull(state.banner)
    }

    private fun inboxState(): InboxState = InboxState(
        repository = FakeProtocol(cookies = true),
        chatStore = store,
        mailboxSync = MailboxSync(FakeProtocol(cookies = true), store),
        outbox = Outbox(
            db = db,
            chatStore = store,
            sender = NeverSends,
            mediaReader = MediaReader(context),
            outboxDir = dir,
            mediaCacheDir = dir,
            scope = scope,
            retryBackoffMs = 10,
        ),
        settings = SettingsStore.create(context),
        scope = scope,
        mediaReader = MediaReader(context),
        appContext = context,
    )

    private suspend fun awaitBanner(state: InboxState): ChatBanner {
        withTimeout(5_000) {
            while (state.banner == null) delay(20)
        }
        return checkNotNull(state.banner)
    }

    private fun incoming(
        threadId: String,
        text: String,
        fromViewer: Boolean = false,
    ) = RealtimeEvent(
        type = "message.new",
        threadId = threadId,
        message = RealtimeMessage(
            id = "message-$threadId-${text.hashCode()}",
            text = text,
            senderName = if (fromViewer) "Me" else "Test Participant",
            senderFbid = if (fromViewer) 111 else 222,
            isFromViewer = fromViewer,
            timestampMs = 1_800_000_000_000L,
            kind = "text",
        ),
    )

    private companion object {
        const val OPEN = "thread-open"
        const val OTHER = "thread-other"
    }
}
