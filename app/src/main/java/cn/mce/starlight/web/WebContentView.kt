package cn.mce.starlight.web

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import cn.mce.starlight.core.SiteScope
import cn.mce.starlight.core.WebCookies

/**
 * WebView 内容层：只用于登录、签到、插件页。
 *
 * 登录态变化的检测不看 URL（Discuz 登录成功后的跳转目标不固定），
 * 而是盯 Cookie 里的 auth：出现 = 刚登录成功，消失 = 刚退出。
 * 一旦变化就通知上层关闭 WebView 并回到原生界面。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebContentView(
    url: String,
    modifier: Modifier = Modifier,
    onAuthStateChanged: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var pageTitle by remember { mutableStateOf("MCE星光班") }
    var canGoBack by remember { mutableStateOf(false) }

    val authAtStart = remember { WebCookies.hasAuthCookie() }
    var lastAuth by remember { mutableStateOf(authAtStart) }
    val notifier = rememberUpdatedState(onAuthStateChanged)

    fun checkAuthChanged() {
        runCatching { CookieManager.getInstance().flush() }
        val now = WebCookies.hasAuthCookie()
        if (now != lastAuth) {
            lastAuth = now
            notifier.value?.invoke()
        }
    }

    val webView = remember {
        WebView(context).apply {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = "$userAgentString MCEAndroid/0.1"
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target: Uri = request.url
                    return if (SiteScope.isInternal(target)) {
                        false
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, target).addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK,
                                ),
                            )
                        }
                        true
                    }
                }

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String?,
                    isReload: Boolean,
                ) {
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    canGoBack = view.canGoBack()
                    checkAuthChanged()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    if (!title.isNullOrBlank()) pageTitle = title
                }
            }

            loadUrl(url)
        }
    }

    BackHandler(enabled = canGoBack) { webView.goBack() }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            },
        )
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
