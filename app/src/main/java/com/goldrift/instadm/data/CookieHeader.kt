package com.goldrift.instadm.data

/** Cookies Instagram requires before a session can be used. */
val REQUIRED_COOKIES = listOf("sessionid", "csrftoken", "ds_user_id")

/**
 * Parses the `name=value; name=value` header returned by Android's
 * `CookieManager`. Values may contain `=`, so only the first separator is used.
 */
fun parseCookieHeader(raw: String): Map<String, String> {
    var header = raw.trim()
    if (header.startsWith("cookie:", ignoreCase = true)) {
        header = header.substring("cookie:".length)
    }

    val cookies = LinkedHashMap<String, String>()
    for (part in header.split(';')) {
        val entry = part.trim()
        if (entry.isEmpty()) continue
        val separator = entry.indexOf('=')
        if (separator <= 0) continue
        val name = entry.substring(0, separator).trim()
        if (name.isEmpty()) continue
        cookies[name] = entry.substring(separator + 1).trim()
    }
    return cookies
}

/** Returns the required cookie names that are absent or blank. */
fun missingCookies(cookies: Map<String, String>): List<String> =
    REQUIRED_COOKIES.filter { cookies[it].isNullOrBlank() }

/**
 * Serializes a cookie map as a flat, key-sorted JSON object. Go's
 * `ParseCookies` accepts this form and Go's map marshaler emits the same order,
 * so exported cookies compare stably with what was captured.
 */
fun canonicalCookieJson(cookies: Map<String, String>): String =
    cookies.toSortedMap().entries.joinToString(separator = ",", prefix = "{", postfix = "}") {
        (name, value) -> "\"${jsonEscape(name)}\":\"${jsonEscape(value)}\""
    }

private fun jsonEscape(value: String): String {
    val out = StringBuilder(value.length + 8)
    for (char in value) {
        when (char) {
            '\\' -> out.append("\\\\")
            '"' -> out.append("\\\"")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (char < ' ') {
                out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
            } else {
                out.append(char)
            }
        }
    }
    return out.toString()
}
