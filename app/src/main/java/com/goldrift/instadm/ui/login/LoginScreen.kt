package com.goldrift.instadm.ui.login

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.data.GoClient
import com.goldrift.instadm.data.canonicalCookieJson
import com.goldrift.instadm.data.missingCookies
import com.goldrift.instadm.data.parseCookieHeader
import com.goldrift.instadm.ui.components.InlineBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val LOGIN_URL = "https://www.instagram.com/accounts/login/"

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    signIn: suspend (String) -> JSONObject,
    onSignedIn: (JSONObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val loginError = stringResource(R.string.login_error)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var sessionCleared by remember { mutableStateOf(false) }
    var pageLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var validating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastAttempt by remember { mutableStateOf("") }

    fun maybeCapture(url: String, view: WebView) {
        if (validating || !isReadyUrl(url)) return

        val raw = CookieManager.getInstance().getCookie(url) ?: return
        val cookies = parseCookieHeader(raw)
        if (missingCookies(cookies).isNotEmpty()) return
        // Persist any cookies Instagram just set before the app is torn down.
        CookieManager.getInstance().flush()

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

    LaunchedEffect(Unit) {
        clearWebViewSession()
        sessionCleared = true
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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            errorMessage = null
                            lastAttempt = ""
                            webView?.reload()
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.login_reload),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            when {
                pageLoading -> {
                    val animatedPageProgress = animateFloatAsState(
                        targetValue = (pageProgress / 100f).coerceIn(0f, 1f),
                        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "loginPageProgress",
                    )
                    LinearWavyProgressIndicator(
                        progress = { animatedPageProgress.value },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                validating -> LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }

            Box(Modifier.weight(1f)) {
                if (!sessionCleared) {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                } else AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        if (viewContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                            WebView.setWebContentsDebuggingEnabled(true)
                        }
                        WebView(viewContext).apply {
                            // Compose wraps AndroidView children in WRAP_CONTENT by default, which
                            // measures WebView with AT_MOST and breaks 100vh/fixed modals.
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            // Match the browser UA used by pinned mautrix-meta.
                            settings.userAgentString = GoClient.userAgent()
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
                                    return !isAllowedLoginNavigation(target.scheme, target.host)
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
                                    pageProgress = newProgress
                                    pageLoading = newProgress < 100
                                }
                            }

                            webView = this
                            loadUrl(LOGIN_URL)
                        }
                    },
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = validating,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    enter = fadeIn(motion.defaultEffectsSpec()) +
                        slideInVertically(motion.defaultSpatialSpec()) { it / 2 } +
                        scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f),
                    exit = fadeOut(motion.fastEffectsSpec()) +
                        slideOutVertically(motion.fastSpatialSpec()) { it / 2 } +
                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.92f),
                ) {
                    Surface(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = stringResource(R.string.login_validating),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            var lastErrorMessage by remember { mutableStateOf(errorMessage) }
            if (errorMessage != null && errorMessage != lastErrorMessage) {
                SideEffect { lastErrorMessage = errorMessage }
            }
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = expandVertically(motion.defaultSpatialSpec()) +
                    fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) +
                    fadeOut(motion.defaultEffectsSpec()),
            ) {
                val message = errorMessage ?: lastErrorMessage ?: return@AnimatedVisibility
                InlineBanner(
                    text = message,
                    icon = Icons.Rounded.ErrorOutline,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    actionLabel = stringResource(R.string.login_retry),
                    onAction = {
                        errorMessage = null
                        lastAttempt = ""
                        webView?.reload()
                    },
                )
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

private val ALLOWED_LOGIN_HOSTS = listOf(
    "instagram.com",
    "facebook.com",
    "meta.com",
    "fbsbx.com",
)

internal fun isAllowedLoginNavigation(scheme: String?, host: String?): Boolean {
    if (!scheme.equals("https", ignoreCase = true)) return false
    val normalized = host?.lowercase().orEmpty()
    return ALLOWED_LOGIN_HOSTS.any { allowed ->
        normalized == allowed || normalized.endsWith(".$allowed")
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
