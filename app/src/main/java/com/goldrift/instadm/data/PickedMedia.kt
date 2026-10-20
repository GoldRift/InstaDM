package com.goldrift.instadm.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.goldrift.instadm.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/** A picked or captured attachment, described before its bytes are uploaded. */
data class PickedMedia(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    /** Byte count reported by the provider, or -1 when unknown. */
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Int = 0,
) {
    val kind: String
        get() = MediaPolicy.kind(mimeType)

    val maxBytes: Long
        get() = MediaPolicy.maxBytes(mimeType)
}

/** A friendly, user-facing ingest failure. */
class MediaPickException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Reads a content URI into the byte array expected by the gomobile API.
 *
 * Providers may not report a size, so [read] enforces the limit while
 * streaming instead of trusting `OpenableColumns.SIZE`.
 */
class MediaReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun prepare(uri: Uri): PickedMedia = withContext(Dispatchers.IO) {
        CameraCapture.prune(appContext)
        try {
            val fileName = queryFileName(uri)
            val mimeType = MediaPolicy.normalizeMimeType(
                appContext.contentResolver.getType(uri),
                fileName,
            )
            if (!MediaPolicy.isAllowed(mimeType)) {
                throw MediaPickException(
                    appContext.getString(R.string.chat_media_unsupported, mimeType),
                )
            }

            val sizeBytes = querySize(uri)
            val limit = MediaPolicy.maxBytes(mimeType)
            if (sizeBytes > limit) {
                throw MediaPickException(
                    appContext.getString(
                        R.string.chat_media_too_large,
                        formatMediaBytes(sizeBytes),
                        formatMediaBytes(limit),
                    ),
                )
            }

            val metadata = probeMetadata(uri, mimeType)
            PickedMedia(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                width = metadata.width,
                height = metadata.height,
                durationMs = metadata.durationMs,
            )
        } catch (error: MediaPickException) {
            throw error
        } catch (error: Exception) {
            throw MediaPickException(
                appContext.getString(R.string.chat_media_unreadable),
                error,
            )
        }
    }

    suspend fun read(picked: PickedMedia): ByteArray = withContext(Dispatchers.IO) {
        val limit = picked.maxBytes
        try {
            val input = appContext.contentResolver.openInputStream(picked.uri)
                ?: throw MediaPickException(appContext.getString(R.string.chat_media_unreadable))

            input.use { stream ->
                val output = ByteArrayOutputStream(DEFAULT_BUFFER_BYTES)
                val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > limit) {
                        throw MediaPickException(
                            appContext.getString(
                                R.string.chat_media_too_large,
                                formatMediaBytes(total),
                                formatMediaBytes(limit),
                            ),
                        )
                    }
                    output.write(buffer, 0, count)
                }
                if (total == 0L) {
                    throw MediaPickException(appContext.getString(R.string.chat_media_empty))
                }
                output.toByteArray()
            }
        } catch (error: MediaPickException) {
            throw error
        } catch (error: Exception) {
            throw MediaPickException(
                appContext.getString(R.string.chat_media_unreadable),
                error,
            )
        }
    }

    private fun queryFileName(uri: Uri): String {
        var displayName: String? = null
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(0)
                }
            }
        }

        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "upload"
        return name.replace('/', '_').replace('\\', '_').take(120)
    }

    private fun querySize(uri: Uri): Long {
        var size = -1L
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    size = cursor.getLong(0)
                }
            }
        }
        return size
    }

    private fun probeMetadata(uri: Uri, mimeType: String): MediaMetadata {
        return when (MediaPolicy.kind(mimeType)) {
            "image" -> probeImage(uri)
            "video", "audio" -> probeAv(uri)
            else -> MediaMetadata()
        }
    }

    private fun probeImage(uri: Uri): MediaMetadata {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }
        return MediaMetadata(
            width = options.outWidth.coerceAtLeast(0),
            height = options.outHeight.coerceAtLeast(0),
        )
    }

    private fun probeAv(uri: Uri): MediaMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            MediaMetadata(
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0)
                    ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0)
                    ?: 0,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0)
                    ?: 0,
            )
        } catch (error: Exception) {
            MediaMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class MediaMetadata(
        val width: Int = 0,
        val height: Int = 0,
        val durationMs: Int = 0,
    )

    private companion object {
        const val DEFAULT_BUFFER_BYTES = 64 * 1024
    }
}

/** Camera captures are written here and shared through the app's FileProvider. */
data class CaptureTarget(val uri: Uri, val file: File)

object CameraCapture {
    private const val CAPTURE_DIR = "outgoing"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    fun newTarget(context: Context): CaptureTarget {
        val directory = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        val file = File(directory, "capture-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return CaptureTarget(uri, file)
    }

    fun delete(target: CaptureTarget) {
        runCatching { target.file.delete() }
    }

    /** Clears captured photos that a pending-send retry no longer needs. */
    fun prune(context: Context) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        File(context.cacheDir, CAPTURE_DIR)
            .listFiles()
            ?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    runCatching { file.delete() }
                }
            }
    }
}
