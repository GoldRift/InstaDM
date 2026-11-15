package com.goldrift.instadm

import com.goldrift.instadm.service.suppressesNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSuppressionTest {
    @Test
    fun theOpenChatIsTheOnlyOneSuppressed() {
        assertTrue(suppressesNotification(true, "thread-1", "thread-1"))
    }

    @Test
    fun aForegroundMessageInAnotherChatStillNotifies() {
        assertFalse(suppressesNotification(true, "thread-1", "thread-2"))
    }

    @Test
    fun aBackgroundMessageNotifiesWithoutAnOpenChat() {
        assertFalse(suppressesNotification(false, null, "thread-1"))
    }

    @Test
    fun aBackgroundMessageInTheChatLastLeftNotifies() {
        assertFalse(suppressesNotification(false, "thread-1", "thread-1"))
    }
}
