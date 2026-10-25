package cn.mce.starlight.core

import android.webkit.CookieManager

/**
 * Cookie 访问入口：优先使用自建 CookieJar（不依赖 WebView），
 * 同时尽量把 CookieManager 里已有的 Cookie 合并进来（兼容旧版本登录态）。
 */
object WebCookies {
    private const val BASE_URL = "https://mce.msi.cn/"

    fun cookieHeader(url: String = BASE_URL): String? {
        val jar = CookieJar.header(url)
        val legacy = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        return when {
            jar.isNullB() && legacy.isNullB() -> null
            jar.isNullB() -> legacy
            legacy.isNullB() -> jar
            else -> "$jar; $legacy"
        }
    }

    fun hasAuthCookie(): Boolean {
        if (CookieJar.hasAuth()) return true
        val legacy = runCatching { CookieManager.getInstance().getCookie(BASE_URL) }.getOrNull()
            ?: return false
        return legacy.contains("auth=") || legacy.contains("saltkey=")
    }

    /** 把响应 Set-Cookie 写入 CookieJar（登录/会话的关键） */
    fun saveFromHeaders(headers: List<String>) {
        CookieJar.saveFromHeaders(headers)
        runCatching {
            headers.forEach { CookieManager.getInstance().setCookie(BASE_URL, it) }
            CookieManager.getInstance().flush()
        }
    }

    fun clear() {
        CookieJar.clear()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    private fun String?.isNullB(): Boolean = this.isNullOrBlank()
}
