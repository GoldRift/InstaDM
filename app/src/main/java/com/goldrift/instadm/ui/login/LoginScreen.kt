package com.goldrift.instadm.ui.login

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.data.canonicalCookieJson
import com.goldrift.instadm.data.missingCookies
import com.goldrift.instadm.data.parseCookieHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val LOGIN_URL =
    "https://www.instagram.com/accounts/login/?next=%2F&source=mobile_nav"

/*
 * Instagram's mobile-web login page renders with a zero-height root in Android
 * WebView (the form is in the DOM but clipped), so this uses a desktop Chrome
 * UA. At phone widths Instagram shows a short landing with a "Log in" button
 * before the normal username/password form.
 */
private const val LOGIN_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/141.0.0.0 Safari/537.36"
private const val COOKIE_POLL_MS = 500L
private const val LOGIN_POLL_LIMIT_MS = 10 * 60 * 1000L

private val BLOCKED_PATHS = listOf(
    "/accounts/login",
    "/accounts/emailsignup",
    "/emailsignup",
    "/challenge",
    "/checkpoint",
    "/consent",
    "/privacy/consent",
    "/accounts/suspended",
)

/**
 * Hosts Instagram's own login page and hands the resulting session to the Go
 * core. The WebView is only a cookie source: no JavaScript bridge is installed
 * and no scripts are injected into the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    signIn: suspend (String) -> JSONObject,
    onSignedIn: (JSONObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val loginError = stringResource(R.string.login_error)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var validating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastAttempt by remember { mutableStateOf("") }

    fun maybeCapture(url: String, view: WebView) {
        if (validating || !isReadyUrl(url)) return

        CookieManager.getInstance().flush()
        val raw = CookieManager.getInstance().getCookie(url) ?: return
        val cookies = parseCookieHeader(raw)
        if (missingCookies(cookies).isNotEmpty()) return

        val cookiesJson = canonicalCookieJson(cookies)
        if (cookiesJson == lastAttempt) return
        lastAttempt = cookiesJson

        validating = true
        errorMessage = null
        scope.launch {
            val result = runCatching { signIn(cookiesJson) }
            result.onSuccess { mailbox ->
                validating = false
                onSignedIn(mailbox)
            }
            result.onFailure { error ->
                validating = false
                errorMessage = error.message
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.trim()
                    ?.take(240)
                    ?.takeIf { it.isNotEmpty() }
                    ?: loginError
            }
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        val deadline = SystemClock.elapsedRealtime() + LOGIN_POLL_LIMIT_MS
        while (isActive && SystemClock.elapsedRealtime() < deadline) {
            delay(COOKIE_POLL_MS)
            maybeCapture(view.url.orEmpty(), view)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                actions = {
                    IconButton(
                        onClick = {
                            errorMessage = null
                            lastAttempt = ""
                            webView?.reload()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.login_reload),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (pageLoading || validating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Box(Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = LOGIN_USER_AGENT
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    pageLoading = true
                                    errorMessage = null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    pageLoading = false
                                    val loaded = view ?: return
                                    maybeCapture(url.orEmpty(), loaded)
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean,
                                ) {
                                    val loaded = view ?: return
                                    maybeCapture(url.orEmpty(), loaded)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val target = request?.url ?: return false
                                    return target.scheme != "https" ||
                                        !isInstagramHost(target.host.orEmpty())
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        pageLoading = false
                                        errorMessage = loginError
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?,
                                ) {
                                    if (request?.isForMainFrame == true &&
                                        (errorResponse?.statusCode ?: 0) >= 400
                                    ) {
                                        pageLoading = false
                                        errorMessage = loginError
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageLoading = newProgress < 100
                                }
                            }

                            webView = this
                            loadUrl(LOGIN_URL)
                        }
                    },
                )

                if (validating) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.login_validating),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            errorMessage?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = {
                                errorMessage = null
                                lastAttempt = ""
                                webView?.reload()
                            },
                        ) {
                            Text(stringResource(R.string.login_retry))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.login_footer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun isInstagramHost(host: String): Boolean =
    host == "instagram.com" || host.endsWith(".instagram.com")

private fun isReadyUrl(url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme != "https") return false
    if (!isInstagramHost(uri.host.orEmpty())) return false
    val path = uri.path.orEmpty().lowercase()
    return BLOCKED_PATHS.none { path.startsWith(it) }
}
