package com.goldrift.instadm.data

import android.content.Intent
import android.net.Uri

/** Text and attachments handed over by another app through the system share sheet. */
data class SharedContent(
    val text: String,
    val uris: List<Uri>,
) {
    val isEmpty: Boolean get() = text.isBlank() && uris.isEmpty()

    companion object {
        /** Instagram accepts a bounded carousel; more attachments would flood the outbox. */
        private const val MAX_URIS = 10

        fun from(intent: Intent?): SharedContent? {
            if (intent == null) return null
            val action = intent.action
            if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

            val clip = intent.clipData
            val uris = buildList {
                addAll(streamUris(intent, multiple = action == Intent.ACTION_SEND_MULTIPLE))
                intent.data?.let(::add)
                if (clip != null) {
                    for (index in 0 until clip.itemCount) {
                        clip.getItemAt(index).uri?.let(::add)
                    }
                }
            }.filter { it.scheme == "content" || it.scheme == "file" }
                .distinct()
                .take(MAX_URIS)
            // A file share often puts a URI string in the clip text; only take
            // that text when there is no attachment to avoid a URL in the draft.
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: clip?.takeIf { uris.isEmpty() && it.itemCount > 0 }
                    ?.getItemAt(0)?.text?.toString()

            return SharedContent(text.orEmpty().trim(), uris).takeUnless { it.isEmpty }
        }

        @Suppress("DEPRECATION")
        private fun streamUris(intent: Intent, multiple: Boolean): List<Uri> = if (multiple) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }
    }
}
