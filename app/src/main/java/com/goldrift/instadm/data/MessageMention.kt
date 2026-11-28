package com.goldrift.instadm.data

import org.json.JSONArray
import org.json.JSONObject

object MentionTypes {
    const val PERSON = "person"
    const val EVERYONE = "everyone"
}

/**
 * One mention span inside a message's text. [offset] and [length] are in UTF-16
 * code units, matching both Instagram's wire format and Kotlin [String] indices.
 */
data class MessageMention(
    val offset: Int,
    val length: Int,
    val type: String = MentionTypes.PERSON,
    val fbid: Long = 0L,
)

fun parseMentions(array: JSONArray?): List<MessageMention> {
    if (array == null || array.length() == 0) return emptyList()
    val result = ArrayList<MessageMention>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val offset = item.optInt("offset", -1)
        val length = item.optInt("length", 0)
        if (offset < 0 || length <= 0) continue
        val type = item.optString("type").trim().ifEmpty { MentionTypes.PERSON }
        result += MessageMention(
            offset = offset,
            length = length,
            type = type,
            fbid = item.optLong("fbid"),
        )
    }
    return result.sortedBy { it.offset }
}

fun parseMentionsJson(raw: String?): List<MessageMention> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { parseMentions(JSONArray(raw)) }.getOrDefault(emptyList())
}

fun mentionsToJson(mentions: List<MessageMention>): String {
    if (mentions.isEmpty()) return ""
    val array = JSONArray()
    mentions.sortedBy { it.offset }.forEach { mention ->
        if (mention.offset < 0 || mention.length <= 0) return@forEach
        val obj = JSONObject()
            .put("offset", mention.offset)
            .put("length", mention.length)
            .put("type", mention.type)
        if (mention.fbid != 0L) {
            obj.put("fbid", mention.fbid)
        }
        array.put(obj)
    }
    return if (array.length() == 0) "" else array.toString()
}
