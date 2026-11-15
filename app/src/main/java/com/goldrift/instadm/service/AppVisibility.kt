package com.goldrift.instadm.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks whether the single activity is visible so the service can avoid
 * posting a notification for a message the user is already looking at.
 * Compose state lets the open chat re-run read marking after a resume.
 */
object AppVisibility {
    var isForeground: Boolean by mutableStateOf(false)

    /** Chat the reader has on screen; a notification there would be redundant. */
    var openThreadId: String? by mutableStateOf(null)
        private set

    fun openChat(threadId: String) {
        openThreadId = threadId
    }

    /** Only the chat that registered may release it; two-pane disposal can be late. */
    fun closeChat(threadId: String) {
        if (openThreadId == threadId) openThreadId = null
    }
}
