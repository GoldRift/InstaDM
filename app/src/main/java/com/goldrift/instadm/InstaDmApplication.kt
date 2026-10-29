package com.goldrift.instadm

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.MailboxSync
import com.goldrift.instadm.data.MediaReader
import com.goldrift.instadm.data.Outbox
import com.goldrift.instadm.data.ProtocolApi
import com.goldrift.instadm.data.ProtocolOutboxSender
import com.goldrift.instadm.data.ProtocolRepository
import com.goldrift.instadm.data.RealtimeRecorder
import com.goldrift.instadm.data.SessionStore
import com.goldrift.instadm.data.db.InstaDmDatabase
import com.goldrift.instadm.service.MessageNotifier
import com.goldrift.instadm.ui.chat.InboxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Supplies the Coil image loader used for inline thumbnails. Instagram's CDN
 * expects the same browser fingerprint as the Go downloader, so every request
 * carries those headers.
 *
 * It also owns the process-scoped session, repository, and inbox state so a
 * configuration change does not rebuild the Go client or refetch the inbox.
 */
open class InstaDmApplication : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionStore by lazy { SessionStore(this) }
    open val repository: ProtocolApi by lazy { ProtocolRepository(sessionStore) }

    /** The demo build has no notification permission prompt. */
    open val requestsNotificationPermission: Boolean = true
    val mediaReader by lazy { MediaReader(this) }
    val database by lazy { InstaDmDatabase.build(this) }
    val chatStore by lazy { ChatStore(database) }
    val mailboxSync by lazy { MailboxSync(repository, chatStore) }
    val messageNotifier by lazy { MessageNotifier(this, chatStore) }
    val realtimeRecorder by lazy {
        RealtimeRecorder(
            chatStore = chatStore,
            scope = appScope,
            onIncomingMessage = { messageNotifier.enqueue(it) },
        ) { mailboxSync.fetchAndStore() }
    }
    val outbox by lazy {
        Outbox(
            db = database,
            chatStore = chatStore,
            sender = ProtocolOutboxSender(repository),
            mediaReader = mediaReader,
            outboxDir = sessionStore.outboxDir,
            mediaCacheDir = sessionStore.mediaCacheDir,
            scope = appScope,
        )
    }
    val inboxState by lazy { InboxState(repository, chatStore, mailboxSync, outbox, appScope, mediaReader) }

    override fun onCreate() {
        super.onCreate()
        // The recorder is process-scoped so realtime deltas are stored even when
        // only the foreground service is running. The outbox retries sends that
        // were interrupted by a previous process death.
        if (sessionStore.hasStoredSession()) {
            realtimeRecorder.start()
            outbox.start()
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            // Instagram delivers most stickers as animated WebP. ImageDecoder
            // is the only platform API that animates them (API 28+); the GIF
            // decoder keeps the older fallback path working.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Accept", "image/avif,image/webp,*/*")
                            .header("User-Agent", MEDIA_USER_AGENT)
                            .header("Sec-Fetch-Dest", "image")
                            .header("Sec-Fetch-Mode", "no-cors")
                            .header("Sec-Fetch-Site", "cross-site")
                            .header("sec-ch-ua", MEDIA_SEC_CH_UA)
                            .header("sec-ch-ua-platform", MEDIA_SEC_CH_PLATFORM)
                            .build(),
                    )
                }
                .build()
        }
        .crossfade(true)
        .build()

    private companion object {
        const val MEDIA_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36"
        const val MEDIA_SEC_CH_UA =
            "\"Chromium\";v=\"141\", \"Google Chrome\";v=\"141\", \"Not-A.Brand\";v=\"99\""
        const val MEDIA_SEC_CH_PLATFORM = "\"Linux\""
    }
}
