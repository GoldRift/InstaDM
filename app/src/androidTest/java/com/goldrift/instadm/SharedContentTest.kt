package com.goldrift.instadm

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.data.SharedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedContentTest {
    @Test
    fun readsTextShare() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "  look at this  ")
        }

        assertEquals(SharedContent("look at this", emptyList()), SharedContent.from(intent))
    }

    @Test
    fun readsSingleAttachmentFromClipData() {
        val uri = Uri.parse("content://media/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            clipData = ClipData.newRawUri("image", uri)
        }

        assertEquals(SharedContent("", listOf(uri)), SharedContent.from(intent))
    }

    @Test
    fun readsMultipleAttachmentsFromStream() {
        val first = Uri.parse("content://media/1")
        val second = Uri.parse("content://media/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }

        assertEquals(SharedContent("", listOf(first, second)), SharedContent.from(intent))
    }

    @Test
    fun ignoresEmptyAndOtherIntents() {
        assertNull(SharedContent.from(Intent(Intent.ACTION_VIEW)))
        assertNull(SharedContent.from(Intent(Intent.ACTION_SEND).apply { type = "text/plain" }))
    }
}
