package com.goldrift.instadm

import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.ReplySnapshot
import com.goldrift.instadm.ui.chat.ADMIN_KIND
import com.goldrift.instadm.ui.chat.CALL_KIND
import com.goldrift.instadm.ui.chat.CLUSTER_GAP_MS
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.ClusterPosition
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.PendingMedia
import com.goldrift.instadm.ui.chat.TIME_GAP_DIVIDER_MS
import com.goldrift.instadm.ui.chat.TranscriptDivider
import com.goldrift.instadm.ui.chat.clusterPosition
import com.goldrift.instadm.ui.chat.countNewArrivals
import com.goldrift.instadm.ui.chat.formatRevealedTimestamp
import com.goldrift.instadm.ui.chat.isJumboEmoji
import com.goldrift.instadm.ui.chat.isJumboEmojiText
import com.goldrift.instadm.ui.chat.messageContentType
import com.goldrift.instadm.ui.chat.replyThumbnail
import com.goldrift.instadm.ui.chat.sameSenderCluster
import com.goldrift.instadm.ui.chat.transcriptDividerBefore
import com.goldrift.instadm.ui.chat.transcriptRowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TranscriptLayoutTest {
    private val zone = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun message(
        id: String = "mid.1",
        text: String = "hello",
        sender: MessageSender = MessageSender.Me,
        timestampMs: Long = at(2026, 11, 19, 20, 14),
        timestamp: String = "8:14 PM",
        kind: String = "text",
        senderFbid: Long = 100L,
        media: List<MessageMedia> = emptyList(),
        reply: ReplySnapshot? = null,
        localMedia: PendingMedia? = null,
    ) = ChatMessage(
        id = id,
        text = text,
        sender = sender,
        timestamp = timestamp,
        timestampMs = timestampMs,
        kind = kind,
        senderFbid = senderFbid,
        media = media,
        reply = reply,
        localMedia = localMedia,
    )

    @Test
    fun firstMessageOrNewCalendarDayShowsDayDivider() {
        val yesterday = message(id = "1", timestampMs = at(2026, 11, 18, 23, 55))
        val today = message(id = "2", timestampMs = at(2026, 11, 19, 0, 5))

        assertEquals(TranscriptDivider.Day, transcriptDividerBefore(null, today, zone))
        assertEquals(TranscriptDivider.Day, transcriptDividerBefore(yesterday, today, zone))
        assertEquals(
            TranscriptDivider.Day,
            transcriptDividerBefore(message(id = "0", timestampMs = 0), today, zone),
        )
    }

    @Test
    fun sameDayGapAtOrAboveTwentyMinutesShowsTimeGapDivider() {
        val base = at(2026, 11, 19, 16, 0)
        val first = message(id = "1", timestampMs = base)
        val nineteenMinLater = message(id = "2", timestampMs = base + TIME_GAP_DIVIDER_MS - 1_000L)
        val twentyMinLater = message(id = "3", timestampMs = base + TIME_GAP_DIVIDER_MS)

        assertEquals(TranscriptDivider.None, transcriptDividerBefore(first, nineteenMinLater, zone))
        assertEquals(TranscriptDivider.TimeGap, transcriptDividerBefore(first, twentyMinLater, zone))
    }

    @Test
    fun messageWithoutTimestampShowsNoDivider() {
        val previous = message(id = "1", timestampMs = at(2026, 11, 19, 16, 0))
        val undated = message(id = "2", timestampMs = 0L)

        assertEquals(TranscriptDivider.None, transcriptDividerBefore(previous, undated, zone))
        assertEquals(TranscriptDivider.None, transcriptDividerBefore(null, undated, zone))
    }

    @Test
    fun revealedTimestampFormatsTodaySameYearAndPriorYear() {
        val now = at(2026, 11, 19, 21, 0)
        val today = at(2026, 11, 19, 20, 14)
        val twoDaysAgo = at(2026, 11, 17, 20, 14)
        val lastYear = at(2025, 11, 17, 20, 14)

        assertEquals(
            "8:14 PM",
            formatRevealedTimestamp(today, "fallback", now, zone, Locale.US),
        )
        assertEquals(
            "Nov 17 · 8:14 PM",
            formatRevealedTimestamp(twoDaysAgo, "fallback", now, zone, Locale.US),
        )
        assertEquals(
            "Nov 17, 2025 · 8:14 PM",
            formatRevealedTimestamp(lastYear, "fallback", now, zone, Locale.US),
        )
        assertEquals(
            "8:14 PM",
            formatRevealedTimestamp(0L, "8:14 PM", now, zone, Locale.US),
        )
    }

    @Test
    fun clustersConsecutiveBubblesFromSameSenderWithinClusterWindow() {
        val base = at(2026, 11, 19, 20, 0)
        val m1 = message(id = "1", sender = MessageSender.Me, timestampMs = base)
        val m2 = message(id = "2", sender = MessageSender.Me, timestampMs = base + 60_000L)
        val m3 = message(id = "3", sender = MessageSender.Me, timestampMs = base + 120_000L)

        assertTrue(sameSenderCluster(m1, m2, zone))
        assertEquals(ClusterPosition.First, clusterPosition(null, m1, m2, zone))
        assertEquals(ClusterPosition.Middle, clusterPosition(m1, m2, m3, zone))
        assertEquals(ClusterPosition.Last, clusterPosition(m2, m3, null, zone))
    }

    @Test
    fun clusterBreaksOnGapSenderMemberNoticeOrStandaloneMedia() {
        val base = at(2026, 11, 19, 20, 0)
        val m1 = message(id = "1", sender = MessageSender.Them, senderFbid = 10L, timestampMs = base)
        val sameSoon = message(id = "2", sender = MessageSender.Them, senderFbid = 10L, timestampMs = base + 30_000L)
        val afterClusterGap = message(
            id = "3",
            sender = MessageSender.Them,
            senderFbid = 10L,
            timestampMs = base + CLUSTER_GAP_MS,
        )
        val otherMember = message(id = "4", sender = MessageSender.Them, senderFbid = 20L, timestampMs = base + 10_000L)
        val mine = message(id = "5", sender = MessageSender.Me, timestampMs = base + 10_000L)
        val notice = message(id = "6", sender = MessageSender.Them, senderFbid = 10L, timestampMs = base + 10_000L, kind = ADMIN_KIND)
        val call = message(id = "7", sender = MessageSender.Them, senderFbid = 10L, timestampMs = base + 10_000L, kind = CALL_KIND)
        val sticker = message(
            id = "8",
            text = "",
            sender = MessageSender.Them,
            senderFbid = 10L,
            timestampMs = base + 10_000L,
            media = listOf(MessageMedia(type = "sticker", url = "https://example.com/s.webp")),
        )
        val jumboEmoji = message(
            id = "9",
            text = "❤️",
            sender = MessageSender.Them,
            senderFbid = 10L,
            timestampMs = base + 10_000L,
        )

        assertTrue(sameSenderCluster(m1, sameSoon, zone))
        assertFalse(sameSenderCluster(m1, afterClusterGap, zone))
        assertFalse(sameSenderCluster(m1, otherMember, zone))
        assertFalse(sameSenderCluster(m1, mine, zone))
        assertFalse(sameSenderCluster(m1, notice, zone))
        assertFalse(sameSenderCluster(m1, call, zone))
        assertFalse(sameSenderCluster(m1, sticker, zone))
        assertFalse(sameSenderCluster(m1, jumboEmoji, zone))
        assertEquals(ClusterPosition.Solo, clusterPosition(m1, sticker, sameSoon, zone))
        assertEquals(ClusterPosition.Solo, clusterPosition(m1, jumboEmoji, sameSoon, zone))
    }

    @Test
    fun recognizesUpToThreeStandaloneEmojisAsJumbo() {
        val valid = listOf("❤️", "✨", "😂🔥", "👍🏽", "👨‍👩‍👧‍👦", "🇺🇸", "1️⃣", "❤️ 😂 🔥")
        for (sample in valid) {
            assertTrue("expected jumbo for $sample", isJumboEmojiText(sample))
        }

        val invalid = listOf("", "   ", "hi", "123", "#1", "that's the one ✨", "🔥🔥🔥🔥", "❤️!")
        for (sample in invalid) {
            assertFalse("expected non-jumbo for $sample", isJumboEmojiText(sample))
        }
    }

    @Test
    fun repliesAttachmentsAndNoticesDisableJumboEmoji() {
        val emojiReply = message(text = "❤️", reply = ReplySnapshot(id = "mid.0", text = "hi"))
        val emojiWithMedia = message(
            text = "❤️",
            media = listOf(MessageMedia(type = "image", url = "https://example.com/a.jpg")),
        )
        val emojiWithLocal = message(
            text = "❤️",
            localMedia = PendingMedia(uri = "content://img/1", kind = "image", fileName = "a.jpg"),
        )
        val emojiNotice = message(text = "❤️", kind = ADMIN_KIND)

        assertFalse(emojiReply.isJumboEmoji())
        assertFalse(emojiWithMedia.isJumboEmoji())
        assertFalse(emojiWithLocal.isJumboEmoji())
        assertFalse(emojiNotice.isJumboEmoji())
        assertTrue(message(text = "❤️ 😂").isJumboEmoji())
    }

    @Test
    fun extractsReplyThumbnailsAndSkipsNonVisualMedia() {
        val tempFile = File.createTempFile("reply-thumb", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val photo = message(
            media = listOf(MessageMedia(type = "image", url = "https://example.com/p.jpg")),
        ).replyThumbnail()
        assertEquals("https://example.com/p.jpg", photo?.model)
        assertFalse(photo?.isVideo ?: true)

        val localPhoto = message(
            media = listOf(
                MessageMedia(
                    type = "image",
                    url = "https://example.com/p.jpg",
                    localPath = tempFile.absolutePath,
                ),
            ),
        ).replyThumbnail()
        assertEquals(tempFile, localPhoto?.model)
        assertFalse(localPhoto?.isVideo ?: true)

        val video = message(
            media = listOf(
                MessageMedia(
                    type = "video",
                    url = "https://example.com/v.mp4",
                    previewUrl = "https://example.com/v.jpg",
                ),
            ),
        ).replyThumbnail()
        assertEquals("https://example.com/v.jpg", video?.model)
        assertTrue(video?.isVideo ?: false)

        val reel = message(
            media = listOf(
                MessageMedia(
                    type = "reel",
                    url = "",
                    previewUrl = "https://example.com/reel.jpg",
                ),
            ),
        ).replyThumbnail()
        assertEquals("https://example.com/reel.jpg", reel?.model)
        assertTrue(reel?.isVideo ?: false)

        val link = message(
            media = listOf(
                MessageMedia(
                    type = "link",
                    url = "https://example.com/post",
                    previewUrl = "https://example.com/link.jpg",
                ),
            ),
        ).replyThumbnail()
        assertEquals("https://example.com/link.jpg", link?.model)
        assertFalse(link?.isVideo ?: true)

        val sticker = message(
            media = listOf(
                MessageMedia(type = "sticker", url = "https://example.com/s.webp"),
            ),
        ).replyThumbnail()
        assertEquals("https://example.com/s.webp", sticker?.model)
        assertFalse(sticker?.isVideo ?: true)

        val pending = message(
            localMedia = PendingMedia(uri = "content://media/1", kind = "image", fileName = "1.jpg"),
        ).replyThumbnail()
        assertEquals("content://media/1", pending?.model)
        assertFalse(pending?.isVideo ?: true)

        assertNull(
            message(
                media = listOf(MessageMedia(type = "image", url = "https://example.com/1.jpg", viewOnce = true)),
            ).replyThumbnail(),
        )
        assertNull(
            message(
                media = listOf(MessageMedia(type = "audio", url = "https://example.com/voice.m4a")),
            ).replyThumbnail(),
        )
        assertNull(message(text = "plain text").replyThumbnail())
    }

    @Test
    fun countsOnlyIncomingNonAdminArrivalsAfterPreviousNewest() {
        val m1 = message(id = "mid.1", sender = MessageSender.Them)
        val m2 = message(id = "mid.2", sender = MessageSender.Me)
        val m3 = message(id = "mid.3", sender = MessageSender.Them)
        val m4 = message(id = "mid.4", sender = MessageSender.Them, kind = ADMIN_KIND)
        val m5 = message(id = "mid.5", sender = MessageSender.Me)
        val m6 = message(id = "mid.6", sender = MessageSender.Them)
        val transcript = listOf(m1, m2, m3, m4, m5, m6)

        assertEquals(0, countNewArrivals(transcript, previousNewestId = null))
        assertEquals(0, countNewArrivals(listOf(m1, m2), previousNewestId = "mid.2"))
        assertEquals(
            0,
            countNewArrivals(
                listOf(message(id = "mid.0", sender = MessageSender.Them), m1, m2),
                previousNewestId = "mid.2",
            ),
        )
        assertEquals(2, countNewArrivals(transcript, previousNewestId = "mid.2"))
        assertEquals(0, countNewArrivals(transcript, previousNewestId = "mid.6"))
    }

    @Test
    fun transcriptRowLayoutComputesDividerClusterAndJoinTogether() {
        val base = at(2026, 11, 19, 20, 0)
        val m1 = message(id = "1", sender = MessageSender.Me, timestampMs = base)
        val m2 = message(id = "2", sender = MessageSender.Me, timestampMs = base + 60_000L)
        val m3 = message(id = "3", sender = MessageSender.Me, timestampMs = base + TIME_GAP_DIVIDER_MS + 60_000L)
        val jumbo = message(id = "4", text = "❤️", sender = MessageSender.Me, timestampMs = base + TIME_GAP_DIVIDER_MS + 70_000L)

        val l1 = transcriptRowLayout(null, m1, m2, zone)
        assertEquals(TranscriptDivider.Day, l1.divider)
        assertEquals(ClusterPosition.First, l1.clusterPosition)
        assertFalse(l1.joinedWithPrevious)

        val l2 = transcriptRowLayout(m1, m2, m3, zone)
        assertEquals(TranscriptDivider.None, l2.divider)
        assertEquals(ClusterPosition.Last, l2.clusterPosition)
        assertTrue(l2.joinedWithPrevious)

        val l3 = transcriptRowLayout(m2, m3, jumbo, zone)
        assertEquals(TranscriptDivider.TimeGap, l3.divider)
        assertEquals(ClusterPosition.Solo, l3.clusterPosition)
        assertFalse(l3.joinedWithPrevious)

        val l4 = transcriptRowLayout(m3, jumbo, null, zone)
        assertEquals(TranscriptDivider.None, l4.divider)
        assertEquals(ClusterPosition.Solo, l4.clusterPosition)
        assertFalse(l4.joinedWithPrevious)
    }

    @Test
    fun replyThumbnailUsesLocalPathWithoutDiskStatAndCarriesFallbackUrl() {
        val missingPath = "/nonexistent/cache/photo-123.jpg"
        val thumb = message(
            media = listOf(
                MessageMedia(
                    type = "image",
                    url = "https://example.com/p.jpg",
                    localPath = missingPath,
                ),
            ),
        ).replyThumbnail()

        assertEquals(File(missingPath), thumb?.model)
        assertEquals("https://example.com/p.jpg", thumb?.fallbackModel)
        assertFalse(thumb?.isVideo ?: true)
    }

    @Test
    fun messageContentTypeDistinguishesRowShapesAndSenders() {
        val admin = message(kind = ADMIN_KIND)
        val myText = message(sender = MessageSender.Me, text = "hi")
        val theirText = message(sender = MessageSender.Them, text = "hi")
        val jumbo = message(sender = MessageSender.Me, text = "🔥")
        val sticker = message(
            sender = MessageSender.Them,
            text = "",
            media = listOf(MessageMedia(type = "sticker", url = "https://example.com/s.webp")),
        )
        val voice = message(
            sender = MessageSender.Them,
            text = "",
            media = listOf(MessageMedia(type = "audio", url = "https://example.com/v.m4a")),
        )
        val photo = message(
            sender = MessageSender.Me,
            text = "",
            media = listOf(MessageMedia(type = "image", url = "https://example.com/p.jpg")),
        )
        val share = message(
            sender = MessageSender.Me,
            text = "",
            media = listOf(MessageMedia(type = "link", url = "https://example.com/post")),
        )

        assertEquals("admin", messageContentType(admin))
        assertEquals("Me:text", messageContentType(myText))
        assertEquals("Them:text", messageContentType(theirText))
        assertEquals("Me:jumbo", messageContentType(jumbo))
        assertEquals("Them:sticker", messageContentType(sticker))
        assertEquals("Them:voice", messageContentType(voice))
        assertEquals("Me:media", messageContentType(photo))
        assertEquals("Me:share", messageContentType(share))
    }
}
