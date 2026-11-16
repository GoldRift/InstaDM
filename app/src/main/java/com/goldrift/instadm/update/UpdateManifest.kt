package com.goldrift.instadm.update

import org.json.JSONObject

data class UpdateApk(val url: String, val size: Long, val sha256: String)

data class UpdateManifest(
    val version: String,
    val versionCode: Long,
    val notes: String,
    val apks: Map<String, UpdateApk>,
) {
    /** The device's own ABI preference order decides, so arm64 wins over a 32-bit fallback. */
    fun apkFor(supportedAbis: List<String>): UpdateApk? =
        supportedAbis.firstNotNullOfOrNull { apks[it] }

    fun isNewerThan(installedVersionCode: Long): Boolean = versionCode > installedVersionCode

    companion object {
        const val URL =
            "https://github.com/GoldRift/InstaDM/releases/latest/download/latest.json"

        private val SHA256 = Regex("[0-9a-fA-F]{64}")

        /** Null on any malformed input; a bad APK entry is dropped, not repaired. */
        fun parse(json: String): UpdateManifest? = runCatching {
            val root = JSONObject(json)
            val version = root.opt("version") as? String
            if (version.isNullOrBlank()) return null
            val versionCode = (root.opt("versionCode") as? Number)?.toLong()
            if (versionCode == null || versionCode <= 0) return null
            val notes = when (val raw = root.opt("notes")) {
                null, JSONObject.NULL -> ""
                is String -> raw
                else -> return null
            }
            val entries = root.optJSONObject("apks") ?: return null
            val apks = entries.keys().asSequence()
                .mapNotNull { abi ->
                    val apk = entries.optJSONObject(abi)?.let(::parseApk)
                        ?: return@mapNotNull null
                    abi to apk
                }
                .toMap()
            if (apks.isEmpty()) return null
            UpdateManifest(version, versionCode, notes, apks)
        }.getOrNull()

        private fun parseApk(json: JSONObject): UpdateApk? {
            val url = json.opt("url") as? String ?: return null
            if (!url.startsWith("https://")) return null
            val size = (json.opt("size") as? Number)?.toLong() ?: return null
            if (size <= 0) return null
            val sha256 = json.opt("sha256") as? String ?: return null
            if (!SHA256.matches(sha256)) return null
            return UpdateApk(url, size, sha256.lowercase())
        }
    }
}
