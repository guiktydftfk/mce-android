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
 * 关键修正：
 *  - 登录态：只认"论坛页里存在退出链接"（Cookie 里 auth 存在但页面没退出链接 → 视为未登录）
 *  - 用户名：从 **个人资料页 home.php?mod=space&do=profile** 解析
 *    （登录后该页就是当前用户，不会误取列表里第一个帖子的作者）
 *  - 头像：从资料页的头像 img 取，或用资料页解析出的 uid 拼 UC 地址
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

    private const val BASE = "https://mce.msi.cn"

    suspend fun probeLoginState() = withContext(Dispatchers.IO) {
        val cookie = WebCookies.cookieHeader()
        if (cookie.isNullOrBlank()) {
            clearLocal()
            return@withContext
        }

        // 1) 用论坛首页判断是否登录（"退出"链接是最可靠的证据）
        val forumHtml = runCatching { fetch("$BASE/forum.php", cookie) }.getOrNull()
        val loggedIn = forumHtml != null && hasLogoutLink(forumHtml)

        if (!loggedIn) {
            // Cookie 可能残留；页面没有退出链接就按未登录处理
            clearLocal()
            return@withContext
        }
        isLoggedIn = true

        // 2) 从个人资料页取当前用户信息
        val profileHtml = runCatching {
            fetch("$BASE/home.php?mod=space&do=profile", cookie)
        }.getOrNull()

        if (profileHtml == null) {
            username = null
            avatarUrl = null
            uid = null
            return@withContext
        }

        val doc = Jsoup.parse(profileHtml, BASE)

        val titleName = Regex("""^(.{1,24}?)的个人资料""")
            .find(doc.title())?.groupValues?.get(1)?.trim()

        val headingName = doc.select("h1, h2")
            .map { it.text().trim() }
            .firstOrNull { it.isNotBlank() && it.length <= 24 && !it.contains("资料") }

        username = titleName ?: headingName

        val avatarImg = doc.selectFirst("img[src*=avatar.php]")?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?.replace("&amp;", "&")
            ?.let(::absolutize)

        uid = avatarImg?.let {
            Regex("""uid=(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        avatarUrl = avatarImg ?: uid?.let { "$BASE/uc_server/avatar.php?uid=$it&size=middle" }
    }

    fun clearLocal() {
        isLoggedIn = false
        username = null
        avatarUrl = null
        uid = null
    }

    private fun hasLogoutLink(html: String): Boolean =
        html.contains("action=logout") || html.contains("action=logout")

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }

    private fun fetch(url: String, cookie: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
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
