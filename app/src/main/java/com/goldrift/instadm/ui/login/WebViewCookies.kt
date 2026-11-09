package com.goldrift.instadm.ui.login

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Clears the WebView's Instagram session and suspends until the removal lands.
 * Cookie removal is asynchronous, and a login page loaded before it completes
 * would still send the previous account's `sessionid` and log straight back in.
 */
suspend fun clearWebViewSession() {
    suspendCancellableCoroutine { continuation ->
        CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
    }
    WebStorage.getInstance().deleteAllData()
    CookieManager.getInstance().flush()
}
