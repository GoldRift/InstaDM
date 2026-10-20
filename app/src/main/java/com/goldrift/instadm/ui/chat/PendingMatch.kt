package com.goldrift.instadm.ui.chat

import com.goldrift.instadm.data.RealtimeMessage

/**
 * True when an outgoing bubble is waiting for [incoming]. Offline threading IDs
 * are authoritative; text is the fallback for sends that did not get one.
 */
fun matchesPendingSend(candidate: ChatMessage, incoming: RealtimeMessage): Boolean {
    if (!candidate.pending || candidate.sender != MessageSender.Me) return false
    if (incoming.offlineThreadingId.isNotBlank() && candidate.offlineThreadingId.isNotBlank()) {
        return candidate.offlineThreadingId == incoming.offlineThreadingId
    }
    return incoming.text.isNotBlank() && candidate.text == incoming.text
}
