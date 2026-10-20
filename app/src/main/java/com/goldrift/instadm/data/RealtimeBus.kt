package com.goldrift.instadm.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local bridge between the foreground watch service and the Compose
 * state. Events are not replayed; a mailbox refresh catches up after process
 * death or while the UI was not collecting.
 */
object RealtimeBus {
    private val eventFlow = MutableSharedFlow<RealtimeEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<RealtimeEvent> = eventFlow.asSharedFlow()

    private val statusFlow = MutableStateFlow(RealtimeStatus.Stopped)
    val status: StateFlow<RealtimeStatus> = statusFlow.asStateFlow()

    fun publish(event: RealtimeEvent) {
        eventFlow.tryEmit(event)
    }

    fun setStatus(status: RealtimeStatus) {
        statusFlow.value = status
    }
}
