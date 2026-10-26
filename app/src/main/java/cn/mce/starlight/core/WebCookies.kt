package cn.mce.starlight.core

import android.webkit.CookieManager

/**
 * Cookie 访问入口：
 *  - 以自建 CookieJar 为准（不依赖 WebView）
 *  - **绝不能把 CookieManager 里的同名 Cookie 拼进来**：WebView 时代常残留游客
 *    的 auth/saltkey，同名重复会让服务端判定会话无效 → 表现为"登录了却是游客"。
 *  - 只有 CookieJar 完全为空时，才退回使用 CookieManager（兼容旧数据）。
 */
object WebCookies {
    private const val BASE_URL = "https://mce.msi.cn/"

    fun cookieHeader(url: String = BASE_URL): String? {
        val jar = CookieJar.header(url)
        if (!jar.isNullOrBlank()) return jar
        return legacyCookie(url)
    }

    fun hasAuthCookie(): Boolean {
        if (CookieJar.hasAuth()) return true
        val legacy = legacyCookie(BASE_URL) ?: return false
        return legacy.contains("auth=") || legacy.contains("saltkey=")
    }

    /** 把响应 Set-Cookie 写入 CookieJar（登录/会话的关键），CookieManager 仅做镜像 */
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

    private fun legacyCookie(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
}
