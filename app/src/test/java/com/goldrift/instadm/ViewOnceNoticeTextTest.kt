package com.goldrift.instadm

import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.ui.chat.viewOnceNoticeText
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewOnceNoticeTextTest {
    private fun media(type: String, viewMode: String) = MessageMedia(
        type = type,
        url = "",
        viewOnce = true,
        viewMode = viewMode,
    )

    @Test
    fun picksThePhotoCopyForASingleView() {
        assertEquals(
            R.string.chat_view_once_photo,
            viewOnceNoticeText(media("image", "viewed")),
        )
        assertEquals(
            R.string.chat_view_once_photo,
            viewOnceNoticeText(media("image", "")),
        )
    }

    @Test
    fun picksTheVideoCopyForASingleView() {
        assertEquals(
            R.string.chat_view_once_video,
            viewOnceNoticeText(media("video", "viewed")),
        )
    }

    @Test
    fun picksTheReplayableCopy() {
        assertEquals(
            R.string.chat_view_twice_photo,
            viewOnceNoticeText(media("image", "replayed")),
        )
        assertEquals(
            R.string.chat_view_twice_video,
            viewOnceNoticeText(media("video", "replayed")),
        )
    }
}
