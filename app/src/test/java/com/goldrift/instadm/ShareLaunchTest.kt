package com.goldrift.instadm

import com.goldrift.instadm.ui.ShareArrivalAction
import com.goldrift.instadm.ui.resolveShareArrival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ShareLaunchTest {
    @Test
    fun mainActivityUsesSingleTaskAndNeverLaunchesDocumentTasks() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifest)

        val activities = document.getElementsByTagName("activity")
        val androidNs = "http://schemas.android.com/apk/res/android"
        var mainActivity: Element? = null
        for (i in 0 until activities.length) {
            val element = activities.item(i) as? Element ?: continue
            if (element.getAttributeNS(androidNs, "name") == ".MainActivity") {
                mainActivity = element
                break
            }
        }

        val activity = mainActivity
        assertNotNull("Expected .MainActivity in AndroidManifest.xml", activity)
        assertEquals("singleTask", activity!!.getAttributeNS(androidNs, "launchMode"))
        assertEquals("never", activity.getAttributeNS(androidNs, "documentLaunchMode"))
    }

    @Test
    fun resolveShareArrivalIgnoresMissingHandledOrUnreadyRoutes() {
        assertNull(resolveShareArrival(pendingShareId = null, handledShareId = 0L, currentRoute = "inbox"))
        assertNull(resolveShareArrival(pendingShareId = 1L, handledShareId = 1L, currentRoute = "search"))
        assertNull(resolveShareArrival(pendingShareId = 1L, handledShareId = 1L, currentRoute = "chat/{threadId}"))
        assertNull(resolveShareArrival(pendingShareId = 1L, handledShareId = 0L, currentRoute = null))
    }

    @Test
    fun resolveShareArrivalPopsBackToInboxFromSecondaryRoutes() {
        val expected = ShareArrivalAction(
            markHandledId = 1L,
            clearUiForPicker = true,
            popToInbox = true,
        )
        assertEquals(expected, resolveShareArrival(pendingShareId = 1L, handledShareId = 0L, currentRoute = "chat/{threadId}"))
        assertEquals(expected, resolveShareArrival(pendingShareId = 1L, handledShareId = 0L, currentRoute = "settings"))
        assertEquals(expected, resolveShareArrival(pendingShareId = 1L, handledShareId = 0L, currentRoute = "search"))
    }

    @Test
    fun resolveShareArrivalClearsUiWithoutPoppingOnInboxOrLogin() {
        val expected = ShareArrivalAction(
            markHandledId = 1L,
            clearUiForPicker = true,
            popToInbox = false,
        )
        assertEquals(expected, resolveShareArrival(pendingShareId = 1L, handledShareId = 0L, currentRoute = "inbox"))
        assertEquals(expected, resolveShareArrival(pendingShareId = 1L, handledShareId = 0L, currentRoute = "login"))
    }

    @Test
    fun resolveShareArrivalTriggersAgainForLaterShares() {
        assertEquals(
            ShareArrivalAction(markHandledId = 2L, clearUiForPicker = true, popToInbox = true),
            resolveShareArrival(pendingShareId = 2L, handledShareId = 1L, currentRoute = "chat/{threadId}"),
        )
    }
}
