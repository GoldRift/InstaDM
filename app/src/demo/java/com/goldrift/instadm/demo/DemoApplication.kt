package com.goldrift.instadm.demo

import com.goldrift.instadm.InstaDmApplication
import com.goldrift.instadm.data.ProtocolApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Demo entry point. It uses the real app shell and Room store but replaces the
 * Go protocol with local data, and starts the outbox so sends resolve offline.
 * Realtime is deliberately never started.
 */
class DemoApplication : InstaDmApplication() {
    private val demoScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val demoSeeder by lazy { DemoSeeder(chatStore) }

    override val repository: ProtocolApi by lazy {
        DemoProtocol(this, chatStore, demoSeeder, demoScope)
    }

    override val requestsNotificationPermission: Boolean = false

    override fun onCreate() {
        super.onCreate()
        demoScope.launch {
            resetDemoData()
            outbox.start()
        }
    }

    /** Wipes and reseeds the demo database; used by the screenshot suite. */
    suspend fun resetDemoData() {
        outbox.clear()
        demoSeeder.reset()
    }
}
