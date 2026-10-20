package com.goldrift.instadm.ui.login

import android.webkit.CookieManager
import android.webkit.WebStorage

/** Clears the WebView's Instagram session after the stored session is removed. */
fun clearWebViewSession() {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
    }
    WebStorage.getInstance().deleteAllData()
}
