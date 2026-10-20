package com.goldrift.instadm

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

/**
 * Supplies the Coil image loader used for inline thumbnails. Instagram's CDN
 * expects the same browser fingerprint as the Go downloader, so every request
 * carries those headers.
 */
class InstaDmApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Accept", "image/avif,image/webp,*/*")
                            .header("User-Agent", MEDIA_USER_AGENT)
                            .header("Sec-Fetch-Dest", "image")
                            .header("Sec-Fetch-Mode", "no-cors")
                            .header("Sec-Fetch-Site", "cross-site")
                            .header("sec-ch-ua", MEDIA_SEC_CH_UA)
                            .header("sec-ch-ua-platform", MEDIA_SEC_CH_PLATFORM)
                            .build(),
                    )
                }
                .build()
        }
        .crossfade(true)
        .build()

    private companion object {
        const val MEDIA_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36"
        const val MEDIA_SEC_CH_UA =
            "\"Chromium\";v=\"141\", \"Google Chrome\";v=\"141\", \"Not-A.Brand\";v=\"99\""
        const val MEDIA_SEC_CH_PLATFORM = "\"Linux\""
    }
}
