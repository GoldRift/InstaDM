package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.NowPlaying
import com.goldrift.instadm.ui.components.showVoiceMiniPlayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerVisibilityTest {
    private val note = NowPlaying(
        threadId = "maya",
        messageId = "demo_maya_9",
        title = "Maya Chen",
        avatarUrl = "",
        initials = "MC",
    )

    @Test
    fun showsWhileItsChatIsNotOnScreen() {
        assertTrue(
            showVoiceMiniPlayer(note, visibleThreadId = "sofia", enabled = true, finished = false),
        )
        assertTrue(
            showVoiceMiniPlayer(note, visibleThreadId = null, enabled = true, finished = false),
        )
    }

    @Test
    fun hidesInsideTheChatThatOwnsTheNote() {
        assertFalse(
            showVoiceMiniPlayer(note, visibleThreadId = "maya", enabled = true, finished = false),
        )
    }

    @Test
    fun hidesWhenDisabledFinishedOrNothingPlays() {
        assertFalse(
            showVoiceMiniPlayer(note, visibleThreadId = "sofia", enabled = false, finished = false),
        )
        assertFalse(
            showVoiceMiniPlayer(note, visibleThreadId = "sofia", enabled = true, finished = true),
        )
        assertFalse(
            showVoiceMiniPlayer(null, visibleThreadId = "sofia", enabled = true, finished = false),
        )
        assertFalse(
            showVoiceMiniPlayer(
                note,
                visibleThreadId = "sofia",
                enabled = true,
                finished = false,
                mediaViewerOpen = true,
            ),
        )
    }
}
