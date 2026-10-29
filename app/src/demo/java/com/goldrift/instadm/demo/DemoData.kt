package com.goldrift.instadm.demo

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sin

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val ASSET = "file:///android_asset/demo/"

/** A fictional person in the demo dataset. */
internal data class DemoPerson(
    val fbid: Long,
    val name: String,
    val username: String,
    val isViewer: Boolean = false,
)

/** Everything the demo backend and seeder need for one process. */
internal class DemoSeed(
    val mailbox: JSONObject,
    val fullThreads: Map<String, JSONObject>,
    val metaThreads: Map<String, JSONObject>,
    val olderPages: Map<String, JSONObject>,
    val replies: Map<String, DemoPerson>,
)

private class ThreadSpec(
    val id: String,
    val longId: String,
    val title: String,
    val isGroup: Boolean,
    val participants: List<DemoPerson>,
    val messages: List<JSONObject>,
    val viewerWatermarkMs: Long,
    val otherWatermarkMs: Long,
    val olderMessages: List<JSONObject> = emptyList(),
    val markedUnread: Boolean = false,
) {
    val other: DemoPerson get() = participants.first { !it.isViewer }
    val lastActivityMs: Long get() = messages.last().getLong("timestamp_ms")
}

/** Fictional conversations and generated media, with no real account data. */
internal object DemoData {
    private const val VIEWER_FBID = 900_000_000_000_001L

    private val viewer = DemoPerson(VIEWER_FBID, "Alex Rivera", "alex.rivera", isViewer = true)
    private val maya = DemoPerson(200_000_000_000_001L, "Maya Chen", "maya.chen")
    private val dev = DemoPerson(200_000_000_000_002L, "Dev Patel", "dev.patel")
    private val sofia = DemoPerson(200_000_000_000_003L, "Sofia Rossi", "sofia.rossi")
    private val priya = DemoPerson(200_000_000_000_004L, "Priya Raman", "priya.raman")
    private val marcus = DemoPerson(200_000_000_000_005L, "Marcus Lee", "marcus.lee")
    private val noah = DemoPerson(200_000_000_000_006L, "Noah Kim", "noah.kim")

    fun build(now: Long): DemoSeed {
        val specs = listOf(
            mayaThread(now),
            designThread(now),
            devThread(now),
            sofiaThread(now),
            supperThread(now),
            noahThread(now),
        )

        val mailbox = JSONObject()
            .put("viewer", viewerJson())
            .put(
                "threads",
                array(specs.map { spec ->
                    meta(spec).put("last_message", lastMessage(spec.messages.last()))
                }),
            )
            .put("has_next_page", false)
            .put("end_cursor", "")

        val full = specs.associate { spec ->
            spec.id to JSONObject()
                .put("thread", meta(spec))
                .put("messages", array(spec.messages))
                .put("read_receipts", receipts(spec))
                .put("has_more_older", spec.olderMessages.isNotEmpty())
                .put("oldest_cursor", olderCursor(spec))
        }
        val metaOnly = specs.associate { spec ->
            spec.id to JSONObject()
                .put("thread", meta(spec))
                .put("messages", JSONArray())
                .put("has_more_older", spec.olderMessages.isNotEmpty())
                .put("oldest_cursor", olderCursor(spec))
        }
        val older = specs
            .filter { it.olderMessages.isNotEmpty() }
            .associate { spec ->
                spec.id to JSONObject()
                    .put("thread_id", spec.id)
                    .put("messages", array(spec.olderMessages))
                    .put("has_more_older", false)
                    .put("oldest_cursor", "")
            }
        val replies = specs.associate { it.id to it.other }
        return DemoSeed(mailbox, full, metaOnly, older, replies)
    }

    private fun mayaThread(now: Long): ThreadSpec {
        val yesterday = message(
            "demo_maya_1",
            maya,
            now - 27 * HOUR,
            "last night was so fun",
        )
        val again = message(
            "demo_maya_2",
            viewer,
            now - 26 * HOUR,
            "we should do it again soon",
        )
        val tonight = message(
            "demo_maya_3",
            maya,
            now - 95 * MINUTE,
            "hey! are we still on for tonight?",
        )
        val found = message(
            "demo_maya_4",
            viewer,
            now - 90 * MINUTE,
            "absolutely. I found that little place on 5th",
            reply = quote(tonight),
        )
        val lights = message(
            "demo_maya_5",
            maya,
            now - 88 * MINUTE,
            "the one with the string lights?",
            reactions = listOf(reaction("😂", viewer, now - 87 * MINUTE)),
        )
        val perfect = message(
            "demo_maya_6",
            viewer,
            now - 85 * MINUTE,
            "that's the one ✨",
            reactions = listOf(reaction("❤️", maya, now - 84 * MINUTE)),
        )
        val terrace = message(
            "demo_maya_7",
            maya,
            now - 80 * MINUTE,
            kind = "image",
            media = listOf(image("terrace.jpg", 1200, 1500)),
        )
        val eight = message("demo_maya_8", maya, now - 45 * MINUTE, "8pm?")
        val voice = message(
            "demo_maya_9",
            maya,
            now - 35 * MINUTE,
            kind = "audio",
            media = listOf(audio("voice_note.m4a", 4_200, waveform(34))),
        )
        val older = listOf(
            message("demo_maya_old_1", maya, now - 50 * HOUR, "still thinking about that place"),
            message("demo_maya_old_2", viewer, now - 49 * HOUR, "same. next week?"),
        )
        return ThreadSpec(
            id = "demo_maya",
            longId = "17800000000000001",
            title = "Maya Chen",
            isGroup = false,
            participants = listOf(viewer, maya),
            messages = listOf(yesterday, again, tonight, found, lights, perfect, terrace, eight, voice),
            viewerWatermarkMs = now - 50 * MINUTE,
            otherWatermarkMs = now,
            olderMessages = older,
        )
    }

    private fun designThread(now: Long): ThreadSpec {
        val deck = message(
            "demo_design_1",
            dev,
            now - 4 * HOUR,
            "did you get a chance to look at the deck?",
        )
        val skim = message(
            "demo_design_2",
            viewer,
            now - 210 * MINUTE,
            "skimming it now — the intro slide is great",
        )
        val heart = message(
            "demo_design_3",
            sofia,
            now - 3 * HOUR,
            kind = "sticker",
            media = listOf(sticker("sticker_heart.webp", "heart sticker")),
        )
        val palette = message(
            "demo_design_4",
            maya,
            now - 150 * MINUTE,
            "the new palette is 🔥",
            reactions = listOf(reaction("👍", dev, now - 149 * MINUTE)),
        )
        val confetti = message(
            "demo_design_5",
            dev,
            now - 2 * HOUR,
            kind = "gif",
            media = listOf(gif("confetti.mp4", "post_cover.jpg", 480, 480)),
        )
        return ThreadSpec(
            id = "demo_design",
            longId = "17800000000000002",
            title = "Design crew",
            isGroup = true,
            participants = listOf(viewer, maya, dev, sofia),
            messages = listOf(deck, skim, heart, palette, confetti),
            viewerWatermarkMs = now - 200 * MINUTE,
            otherWatermarkMs = now,
        )
    }

    private fun devThread(now: Long): ThreadSpec {
        val shots = message(
            "demo_dev_1",
            viewer,
            now - 6 * HOUR,
            "here are the shots from the shoot",
            kind = "image",
            media = listOf(
                image("studio.jpg", 1200, 900),
                image("street.jpg", 1200, 1600),
            ),
        )
        val great = message(
            "demo_dev_2",
            dev,
            now - 330 * MINUTE,
            "these are great, the light is perfect",
        )
        val clip = message(
            "demo_dev_3",
            dev,
            now - 5 * HOUR,
            kind = "video",
            media = listOf(video("clip.mp4", "street.jpg", 720, 1280, 5_000)),
        )
        val vibe = message(
            "demo_dev_4",
            viewer,
            now - 270 * MINUTE,
            "this is the vibe",
            kind = "reel",
            media = listOf(reel("reel_cover.jpg", "DEMO123", "demo_reel_1", 1080, 1920)),
        )
        val once = message(
            "demo_dev_5",
            dev,
            now - 4 * HOUR,
            kind = "image",
            media = listOf(
                JSONObject()
                    .put("type", "image")
                    .put("view_once", true)
                    .put("view_mode", "viewed"),
            ),
        )
        val more = message(
            "demo_dev_6",
            viewer,
            now - 228 * MINUTE,
            "more coming tonight",
        )
        return ThreadSpec(
            id = "demo_dev",
            longId = "17800000000000003",
            title = "Dev Patel",
            isGroup = false,
            participants = listOf(viewer, dev),
            messages = listOf(shots, great, clip, vibe, once, more),
            viewerWatermarkMs = now,
            otherWatermarkMs = now,
        )
    }

    private fun sofiaThread(now: Long): ThreadSpec {
        val photos = message(
            "demo_sofia_1",
            sofia,
            now - 28 * HOUR,
            "the photos came out so good 📸",
        )
        val notes = message(
            "demo_sofia_2",
            viewer,
            now - 27 * HOUR,
            "design notes for the launch",
            kind = "link",
            media = listOf(link("https://example.com/design-notes", "post_cover.jpg", 1200, 800)),
        )
        val thanks = message(
            "demo_sofia_3",
            viewer,
            now - 26 * HOUR,
            "you're the best",
        )
        val voice = message(
            "demo_sofia_4",
            sofia,
            now - 1450 * MINUTE,
            kind = "audio",
            media = listOf(audio("voice_note.m4a", 6_200, waveform(46))),
        )
        return ThreadSpec(
            id = "demo_sofia",
            longId = "17800000000000004",
            title = "Sofia Rossi",
            isGroup = false,
            participants = listOf(viewer, sofia),
            messages = listOf(photos, notes, thanks, voice),
            viewerWatermarkMs = now - 1470 * MINUTE,
            otherWatermarkMs = now,
        )
    }

    private fun supperThread(now: Long): ThreadSpec {
        val table = message(
            "demo_supper_1",
            priya,
            now - 52 * HOUR,
            "table for six on friday?",
        )
        val booked = message(
            "demo_supper_2",
            viewer,
            now - 51 * HOUR,
            "booked it",
        )
        val dessert = message(
            "demo_supper_3",
            marcus,
            now - 50 * HOUR,
            "I'll bring dessert",
        )
        return ThreadSpec(
            id = "demo_supper",
            longId = "17800000000000005",
            title = "Supper club",
            isGroup = true,
            participants = listOf(viewer, priya, marcus),
            messages = listOf(table, booked, dessert),
            viewerWatermarkMs = now - 3030 * MINUTE,
            otherWatermarkMs = now,
            markedUnread = true,
        )
    }

    private fun noahThread(now: Long): ThreadSpec {
        val files = message(
            "demo_noah_1",
            noah,
            now - 4 * DAY,
            "sent you the files",
        )
        val thanks = message(
            "demo_noah_2",
            viewer,
            now - 4 * DAY + 5 * MINUTE,
            "got them, thanks!",
        )
        return ThreadSpec(
            id = "demo_noah",
            longId = "17800000000000006",
            title = "Noah Kim",
            isGroup = false,
            participants = listOf(viewer, noah),
            messages = listOf(files, thanks),
            viewerWatermarkMs = now,
            otherWatermarkMs = now,
        )
    }

    private fun meta(spec: ThreadSpec): JSONObject = JSONObject()
        .put("id", spec.id)
        .put("thread_id", spec.longId)
        .put("thread_fbid", spec.id)
        .put("thread_key", spec.id.hashCode().toLong())
        .put("subtype", if (spec.isGroup) "IG_GROUP" else "IG_ONLY_ONE_TO_ONE")
        .put("is_group", spec.isGroup)
        .put("title", spec.title)
        .put("avatar_url", "")
        .put("participants", array(spec.participants.map(::personJson)))
        .put("folder", "PRIMARY")
        .put("system_folder", "INBOX")
        .put("marked_as_unread", spec.markedUnread)
        .put("is_muted", false)
        .put("is_pinned", false)
        .put("last_activity_ms", spec.lastActivityMs)

    private fun receipts(spec: ThreadSpec): JSONArray = JSONArray().apply {
        if (spec.viewerWatermarkMs > 0) {
            put(
                JSONObject()
                    .put("participant_fbid", VIEWER_FBID)
                    .put("watermark_ms", spec.viewerWatermarkMs)
                    .put("from_viewer", true),
            )
        }
        if (spec.otherWatermarkMs > 0) {
            put(
                JSONObject()
                    .put("participant_fbid", spec.other.fbid)
                    .put("watermark_ms", spec.otherWatermarkMs),
            )
        }
    }

    private fun olderCursor(spec: ThreadSpec): String =
        if (spec.olderMessages.isEmpty()) "" else "demo-older-${spec.id}"

    private fun viewerJson(): JSONObject = personJson(viewer)
        .put("ig_id", VIEWER_FBID.toString())

    private fun personJson(person: DemoPerson): JSONObject = JSONObject()
        .put("ig_id", person.fbid.toString())
        .put("fbid", person.fbid)
        .put("username", person.username)
        .put("name", person.name)
        .put("avatar_url", "")
        .put("is_viewer", person.isViewer)

    private fun lastMessage(message: JSONObject): JSONObject = JSONObject()
        .put("id", message.getString("id"))
        .put("timestamp_ms", message.getLong("timestamp_ms"))
        .put("text", message.optString("text"))
        .put("kind", message.optString("kind"))
        .put("is_from_viewer", message.optBoolean("is_from_viewer"))
        .put("sender_fbid", message.optLong("sender_fbid"))

    private fun message(
        id: String,
        sender: DemoPerson,
        timestampMs: Long,
        text: String = "",
        kind: String = "text",
        media: List<JSONObject> = emptyList(),
        reactions: List<JSONObject> = emptyList(),
        reply: JSONObject? = null,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("offline_threading_id", id)
        .put("sender_fbid", sender.fbid)
        .put("sender_name", sender.name)
        .put("is_from_viewer", sender.isViewer)
        .put("timestamp_ms", timestampMs)
        .put("kind", kind)
        .put("text", text)
        .put("seen", sender.isViewer)
        .put("media_count", media.size)
        .apply {
            if (media.isNotEmpty()) put("media", array(media))
            if (reactions.isNotEmpty()) put("reactions", array(reactions))
            reply?.let { put("replied_to_message", it) }
        }

    private fun quote(message: JSONObject): JSONObject = JSONObject()
        .put("id", message.getString("id"))
        .put("text", message.optString("text"))
        .put("sender_name", message.optString("sender_name"))
        .put("is_from_viewer", message.optBoolean("is_from_viewer"))
        .put("kind", message.optString("kind"))
        .put("media_count", message.optInt("media_count"))

    private fun reaction(emoji: String, sender: DemoPerson, at: Long): JSONObject = JSONObject()
        .put("emoji", emoji)
        .put("sender_fbid", sender.fbid)
        .put("is_from_viewer", sender.isViewer)
        .put("timestamp_ms", at)

    private fun image(name: String, width: Int, height: Int): JSONObject = JSONObject()
        .put("type", "image")
        .put("url", ASSET + name)
        .put("preview_url", ASSET + name)
        .put("width", width)
        .put("height", height)

    private fun video(
        name: String,
        preview: String,
        width: Int,
        height: Int,
        durationMs: Int,
    ): JSONObject = JSONObject()
        .put("type", "video")
        .put("url", ASSET + name)
        .put("preview_url", ASSET + preview)
        .put("width", width)
        .put("height", height)
        .put("duration_ms", durationMs)

    private fun gif(name: String, preview: String, width: Int, height: Int): JSONObject = JSONObject()
        .put("type", "gif")
        .put("url", ASSET + name)
        .put("preview_url", ASSET + preview)
        .put("width", width)
        .put("height", height)

    private fun sticker(name: String, alt: String): JSONObject = JSONObject()
        .put("type", "sticker")
        .put("url", ASSET + name)
        .put("preview_url", ASSET + name)
        .put("alt_text", alt)

    private fun audio(name: String, durationMs: Int, waveform: JSONArray): JSONObject = JSONObject()
        .put("type", "audio")
        .put("url", ASSET + name)
        .put("duration_ms", durationMs)
        .put("waveform", waveform)

    private fun reel(
        cover: String,
        shortcode: String,
        targetId: String,
        width: Int,
        height: Int,
    ): JSONObject = JSONObject()
        .put("type", "reel")
        .put("preview_url", ASSET + cover)
        .put("width", width)
        .put("height", height)
        .put("shortcode", shortcode)
        .put("target_id", targetId)

    private fun link(url: String, cover: String, width: Int, height: Int): JSONObject = JSONObject()
        .put("type", "link")
        .put("url", url)
        .put("target_url", url)
        .put("preview_url", ASSET + cover)
        .put("width", width)
        .put("height", height)

    private fun waveform(points: Int): JSONArray = JSONArray().apply {
        repeat(points) { index ->
            put(0.22 + (sin(index * 0.62) + 1.0) * 0.32)
        }
    }

    private fun array(items: List<JSONObject>): JSONArray = JSONArray().apply {
        items.forEach(::put)
    }
}
