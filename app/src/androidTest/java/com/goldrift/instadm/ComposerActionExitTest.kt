package com.goldrift.instadm

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.ui.chat.ChatScreen
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.VoicePlayer
import com.goldrift.instadm.ui.theme.InstaDMTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented. `mainClock` is frozen and advanced by fixed frames because an
 * exiting AnimatedContent child, semantics included, stays composed until its
 * exit finishes; only a frame inside the exit shows whether it is gated.
 */
@RunWith(AndroidJUnit4::class)
class ComposerActionExitTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val voicePlayer = VoicePlayer(context)

    @Test
    fun leavingMicIsOutOfTheSemanticsTreeWhileItFadesOut() {
        val micLabel = context.getString(R.string.chat_mic_record)
        val sendLabel = context.getString(R.string.chat_send)
        composeChat()
        compose.onNodeWithContentDescription(micLabel).assertExists()

        compose.onNode(hasSetTextAction()).performTextInput("hi")
        compose.mainClock.advanceTimeByFrame()
        // A frame plus this slice is far inside the fast effects spring the exit
        // runs on, so the mic is still composed when it is asked for below.
        compose.mainClock.advanceTimeBy(50)

        compose.onNodeWithContentDescription(sendLabel).assertExists()
        compose.onNodeWithContentDescription(micLabel).assertDoesNotExist()
    }

    // The mic leaves through MicButton's own disabled branch; only this
    // direction clears the leaving control through inputOnlyWhen.
    @Test
    fun leavingSendIsOutOfTheSemanticsTreeWhileItFadesOut() {
        val micLabel = context.getString(R.string.chat_mic_record)
        val sendLabel = context.getString(R.string.chat_send)
        composeChat()

        compose.onNode(hasSetTextAction()).performTextInput("hi")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription(sendLabel).assertExists()

        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.mainClock.advanceTimeByFrame()
        // The send's exit springs run for hundreds of milliseconds, so this
        // slice is still inside the exit.
        compose.mainClock.advanceTimeBy(50)

        compose.onNodeWithContentDescription(micLabel).assertExists()
        compose.onNodeWithContentDescription(sendLabel).assertDoesNotExist()
    }

    private fun composeChat() {
        val conversation = Conversation(
            id = "composer-action-exit",
            contact = Contact(id = "maya", name = "Maya Chen", initials = "MC"),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            InstaDMTheme(darkTheme = false, dynamicColor = false) {
                ChatScreen(
                    conversation = conversation,
                    onSend = { _, _, _ -> },
                    onBack = {},
                    voicePlayer = voicePlayer,
                )
            }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }
}
