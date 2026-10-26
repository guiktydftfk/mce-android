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
 * 【解析时机原则】
 *  - 网络失败 ≠ 未登录：只有「页面成功取回且证据表明是游客」才清理本地登录态，
 *    否则一次超时就会把用户踢下线（历史 bug：重装/弱网后莫名弹登录框）。
 *  - 登录证据：页面含 `action=logout`，或能打开个人资料页（标题含"的个人资料"且不是登录页）。
 */
object SessionStore {
    /** true=已登录，false=确定未登录，null=网络失败无法判断 */
    var lastProbe: Boolean? = null
        private set

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

    suspend fun probeLoginState(): Boolean? = withContext(Dispatchers.IO) {
        val cookie = WebCookies.cookieHeader()
        if (cookie.isNullOrBlank()) {
            lastProbe = false
            clearLocal()
            return@withContext false
        }

        val forumHtml = runCatching { fetch("$BASE/forum.php", cookie) }.getOrNull()
        forumHtml?.let { DebugDump.dump("probe_forum", it) }

        // 网络失败：保持现状，不清理、不判定
        if (forumHtml == null) {
            lastProbe = null
            return@withContext null
        }

        if (hasLogoutEvidence(forumHtml)) {
            isLoggedIn = true
            lastProbe = true
            loadProfileInfo(cookie)
            return@withContext true
        }

        // 论坛页没有退出链接：再用个人资料页确认（有些模板不在首页放退出入口）
        val profileHtml = runCatching { fetch("$BASE/home.php?mod=space&do=profile", cookie) }.getOrNull()
        profileHtml?.let { DebugDump.dump("probe_profile", it) }
        if (profileHtml == null) {
            lastProbe = null
            return@withContext null
        }

        val doc = Jsoup.parse(profileHtml, BASE)
        // 移动版资料页标题并不一定是「X的个人资料」，不能只看标题。
        // 判定证据：登录表单 = 游客；含 spacecp/退出 链接 = 已登录。
        val looksLikeLoginPage = doc.selectFirst("input[name=password]") != null &&
            doc.selectFirst("input[name=username]") != null
        val memberEvidence = hasLogoutEvidence(profileHtml) || profileHtml.contains("spacecp") ||
            profileHtml.contains("action=logout")

        if (looksLikeLoginPage || !memberEvidence) {
            // 只有确实拿到登录页时才清本地；其他情况视为无法判断，保持现状
            if (looksLikeLoginPage) {
                lastProbe = false
                clearLocal()
                return@withContext false
            }
            lastProbe = null
            return@withContext null
        }

        isLoggedIn = true
        lastProbe = true
        username = Regex("""^(.{1,24}?)的个人资料""")
            .find(doc.title())?.groupValues?.get(1)?.trim()
            ?: doc.select("h1, h2").map { it.text().trim() }
                .firstOrNull { it.isNotBlank() && it.length <= 24 && !it.contains("\u8d44\u6599") }
        val avatarImg = doc.selectFirst("img[src*=avatar.php]")?.attr("src")
            ?.takeIf { it.isNotBlank() }?.replace("&amp;", "&")?.let(::absolutize)
        uid = avatarImg?.let { Regex("""uid=(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        avatarUrl = avatarImg ?: uid?.let { "$BASE/uc_server/avatar.php?uid=$it&size=middle" }
        true
    }

    /** 已确认登录后补全用户信息（失败不影响登录态） */
    private fun loadProfileInfo(cookie: String) {
        val profileHtml = runCatching { fetch("$BASE/home.php?mod=space&do=profile", cookie) }.getOrNull()
            ?: return
        val doc = Jsoup.parse(profileHtml, BASE)
        username = Regex("""^(.{1,24}?)的个人资料""")
            .find(doc.title())?.groupValues?.get(1)?.trim()
            ?: doc.select("h1, h2").map { it.text().trim() }
                .firstOrNull { it.isNotBlank() && it.length <= 24 && !it.contains("\u8d44\u6599") }
        val avatarImg = doc.selectFirst("img[src*=avatar.php]")?.attr("src")
            ?.takeIf { it.isNotBlank() }?.replace("&amp;", "&")?.let(::absolutize)
        uid = avatarImg?.let { Regex("""uid=(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        avatarUrl = avatarImg ?: uid?.let { "$BASE/uc_server/avatar.php?uid=$it&size=middle" }
    }

    fun clearLocal() {
        isLoggedIn = false
        username = null
        avatarUrl = null
        uid = null
    }

    private fun hasLogoutEvidence(html: String): Boolean =
        html.contains("action=logout") || html.contains("\u9000\u51fa\u767b\u5f55")

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
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
