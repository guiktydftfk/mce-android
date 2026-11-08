package cn.mce.starlight.ui

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/**
 * v0.4.4：内置 WebView 页面（举报 / 编辑帖子等需要网页表单的操作）。
 * 打开前把 App 的登录 Cookie 注入 WebView，保证进去就是已登录状态。
 */
@Composable
fun WebPageScreen(
    url: String,
    title: String,
    onMenu: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    runCatching {
                        cn.mce.starlight.core.CookieJar.all().forEach { (k, v) ->
                            cm.setCookie("https://mce.msi.cn/", "$k=$v; path=/")
                        }
                        cm.flush()
                    }
                    loadUrl(url)
                }
            },
        )
        ImmersiveTopBar(
            title = title,
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}