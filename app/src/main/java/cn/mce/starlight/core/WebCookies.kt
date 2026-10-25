package cn.mce.starlight.core

import android.webkit.CookieManager

/**
 * WebView 与原生请求之间的 Cookie 桥接。
 *
 * Android 的优势：WebView 的 Cookie 存在 CookieManager 里，
 * 原生 HttpURLConnection 只需要带上同一个 Cookie 头，
 * 登录态就自动共享了（不像 iOS 需要手动同步 WKHTTPCookieStore）。
 */
object WebCookies {
    private const val BASE_URL = "https://mce.msi.cn/"

    fun cookieHeader(url: String = BASE_URL): String? =
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }

    /** 是否已登录：Discuz 登录后会下发 auth 类 cookie */
    fun hasAuthCookie(): Boolean {
        val cookies = cookieHeader() ?: return false
        return cookies.contains("auth=") || cookies.contains("saltkey=")
    }

    /** 从 CookieManager 解析用户名（Discuz 的 cookietime 里没有，兜底用页面解析） */
    fun clear() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
