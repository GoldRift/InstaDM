package com.goldrift.instadm

import android.app.PendingIntent
import com.goldrift.instadm.data.REACTION_LOG_KIND
import com.goldrift.instadm.service.canLikeNotification
import com.goldrift.instadm.service.replyPendingFlags
import com.goldrift.instadm.ui.chat.ADMIN_GROUP_KIND
import com.goldrift.instadm.ui.chat.ADMIN_KIND
import com.goldrift.instadm.ui.chat.CALL_KIND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionsTest {
    @Test
    fun replyActionIsMutableOnEveryApi() {
        // 30: SystemUI writes a fill-in intent into the PendingIntent, 31: RemoteInput requires mutability.
        assertEquals(0, replyPendingFlags(withRemoteInput = true, sdkInt = 30))
        assertEquals(PendingIntent.FLAG_MUTABLE, replyPendingFlags(withRemoteInput = true, sdkInt = 31))
    }

    @Test
    fun inputlessActionsAreImmutableOnEveryApi() {
        assertEquals(PendingIntent.FLAG_IMMUTABLE, replyPendingFlags(withRemoteInput = false, sdkInt = 30))
        assertEquals(PendingIntent.FLAG_IMMUTABLE, replyPendingFlags(withRemoteInput = false, sdkInt = 31))
    }

    @Test
    fun activityNoticesCannotBeLikedFromANotification() {
        assertFalse(canLikeNotification(REACTION_LOG_KIND))
        assertFalse(canLikeNotification(ADMIN_KIND))
        assertFalse(canLikeNotification(ADMIN_GROUP_KIND))
        assertFalse(canLikeNotification("$ADMIN_KIND-restricted"))
        assertFalse(canLikeNotification(CALL_KIND))
    }

    @Test
    fun realMessagesCanBeLiked() {
        assertTrue(canLikeNotification("text"))
        assertTrue(canLikeNotification("image"))
        assertTrue(canLikeNotification(""))
    }
}
