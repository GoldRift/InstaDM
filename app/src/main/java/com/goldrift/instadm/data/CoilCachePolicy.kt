package com.goldrift.instadm.data

import android.net.Uri
import coil.intercept.Interceptor
import coil.key.Keyer
import coil.request.ImageResult
import coil.request.Options
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

internal const val IMAGE_MEMORY_CACHE_PERCENT = 0.20
internal const val IMAGE_DISK_CACHE_DIR = "image_cache"
internal const val IMAGE_DISK_CACHE_MAX_BYTES = 100L * 1024L * 1024L

private val EPHEMERAL_CDN_PARAMS = setOf(
    "oh",
    "oe",
    "ccb",
    "edm",
    "_nc_ht",
    "_nc_cat",
    "_nc_oc",
    "_nc_ohc",
    "_nc_gid",
    "_nc_sid",
    "_nc_rid",
    "_nc_ad",
    "_nc_cid",
)

/**
 * Normalizes signed Instagram/Meta CDN URLs into stable cache keys so avatars
 * and inline thumbnails hit memory and disk caches after signed query params
 * (`oh`, `oe`, `_nc_*`) or edge POP subdomains rotate on inbox/thread refresh.
 * Transform parameters such as `stp` stay in the key so distinct crops never
 * collide. Returns null for non-Meta or non-HTTP URLs.
 */
internal fun cdnStableCacheKey(rawUrl: String): String? {
    val parsed = rawUrl.toHttpUrlOrNull() ?: return null
    val canonicalHost = canonicalMetaCdnHost(parsed.host.lowercase(Locale.US)) ?: return null
    val path = parsed.encodedPath
    if (path.isEmpty() || path == "/") return null

    val kept = ArrayList<Pair<String, String>>(parsed.querySize)
    for (index in 0 until parsed.querySize) {
        val name = parsed.queryParameterName(index)
        if (name in EPHEMERAL_CDN_PARAMS) continue
        kept.add(name to (parsed.queryParameterValue(index) ?: ""))
    }
    if (kept.isEmpty()) return "$canonicalHost$path"

    kept.sortWith(compareBy<Pair<String, String>>({ it.first }, { it.second }))
    val query = kept.joinToString("&") { (key, value) ->
        if (value.isEmpty()) key else "$key=$value"
    }
    return "$canonicalHost$path?$query"
}

private fun canonicalMetaCdnHost(host: String): String? {
    val isCdnInstagram = host == "cdninstagram.com" || host.endsWith(".cdninstagram.com")
    val isFbcdn = host == "fbcdn.net" || host.endsWith(".fbcdn.net")
    if (!isCdnInstagram && !isFbcdn) return null
    val isEdgePop = host.startsWith("scontent") ||
        (host.startsWith("instagram.") && host.endsWith(".fna.fbcdn.net"))
    return if (isEdgePop) "meta-scontent" else host
}

/**
 * Memory cache keyer for Meta CDN URIs. Keeping this as a [Keyer] rather than
 * setting `request.memoryCacheKey` lets Coil keep appending transformations
 * and request parameters automatically.
 */
internal object CdnMemoryKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? = cdnStableCacheKey(data.toString())
}

/** Assigns the same normalized key to Coil's disk cache for Meta CDN URLs. */
internal object CdnDiskCacheKeyInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        if (request.diskCacheKey != null) return chain.proceed(request)
        val rawUrl = when (val data = request.data) {
            is String -> data
            is Uri -> data.toString()
            else -> null
        }
        val stableKey = rawUrl?.let(::cdnStableCacheKey) ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .diskCacheKey(stableKey)
                .build(),
        )
    }
}
