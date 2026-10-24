package com.goldrift.instadm.data

/**
 * A quoted message stored with a reply. Instagram embeds the quoted message in
 * the reply payload, so the app can render it even when the original is outside
 * the loaded page or missing from the local cache.
 */
data class ReplySnapshot(
    val id: String,
    val text: String = "",
    val senderName: String = "",
    val isFromViewer: Boolean = false,
    val kind: String = "",
    val mediaCount: Int = 0,
)
