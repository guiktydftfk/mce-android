package cn.mce.starlight.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

/**
 * 会话层：登录态判定 + 当前用户信息解析。
 *
 * 判定策略（按可靠性排序）：
 *  1. Cookie 里是否出现 auth（Discuz 登录后的鉴权 cookie）——最可靠，与页面跳转无关
 *  2. 页面里是否存在"退出"链接
 *
 * 用户信息：
 *  用户名 ← "退出"链接周围的 `home.php?mod=space&uid=` 链接文字
 *  头像   ← 用解析出的 uid 直接拼 UC 头像地址（避免抓到楼主头像）
 */
object SessionStore {
    var isLoggedIn by mutableStateOf(false)
        private set
    var username by mutableStateOf<String?>(null)
        private set
    var avatarUrl by mutableStateOf<String?>(null)
        private set
    var uid by mutableStateOf<Int?>(null)
        private set

    private const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 " +
            "Mobile/15E148 Safari/604.1"

    suspend fun probeLoginState() = withContext(Dispatchers.IO) {
        val cookie = WebCookies.cookieHeader()

        if (cookie.isNullOrBlank() || !WebCookies.hasAuthCookie()) {
            isLoggedIn = false
            username = null
            avatarUrl = null
            uid = null
            return@withContext
        }

        val html = runCatching { fetch("https://mce.msi.cn/forum.php", cookie) }.getOrNull()
        if (html == null) {
            // 网络异常时退化：只信 Cookie
            isLoggedIn = true
            return@withContext
        }

        val doc = Jsoup.parse(html, "https://mce.msi.cn/")

        // 退出链接 = 已登录的硬证据
        val hasLogout = doc.selectFirst("a[href*=action=logout]") != null
        isLoggedIn = hasLogout || WebCookies.hasAuthCookie()

        if (!isLoggedIn) {
            username = null
            avatarUrl = null
            uid = null
            return@withContext
        }

        // 找当前用户的空间链接（排除公共入口与"空间"字样）
        val userLink = doc.select("a[href*=home.php?mod=space]")
            .firstOrNull { a ->
                val text = a.text().trim()
                text.isNotBlank() &&
                    !text.contains("空间") &&
                    !text.contains("消息") &&
                    a.attr("href").contains("uid=")
            }
        val userUid = userLink?.attr("href")
            ?.let { Regex("""uid=(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

        username = userLink?.text()?.trim()
        uid = userUid
        avatarUrl = userUid?.let {
            "https://mce.msi.cn/uc_server/avatar.php?uid=$it&size=middle"
        }
    }

    fun clearLocal() {
        isLoggedIn = false
        username = null
        avatarUrl = null
        uid = null
    }

    private fun fetch(url: String, cookie: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Cookie", cookie)
        }
        try {
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
