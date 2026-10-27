package cn.mce.starlight.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.mce.starlight.data.GenericPageClient
import cn.mce.starlight.data.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 会话层：登录态判定 + 当前用户信息解析。
 *
 * 【解析时机原则】
 *  - 网络失败 ≠ 未登录：只有「页面成功取回且证据表明是游客」才清理本地登录态，
 *    否则一次超时就会把用户踢下线（历史 bug：重装/弱网后莫名弹登录框）。
 *  - 登录证据：页面含 `action=logout`，或能打开个人资料页（不是登录表单）。
 *
 * v0.3.3 批 6：
 *  - 探活改走 [GenericPageClient]（自带逐跳保存 Set-Cookie）。旧实现自己手写
 *    `HttpURLConnection` + `instanceFollowRedirects=true`，会把资料页刷新的 Cookie 全丢掉，
 *    与"登录了却是游客"是同一类坑；同时删掉重复的 UA/BASE/absolutize。
 *  - 删掉未使用的 `loadProfileInfo()` 与只写不读的 `lastProbe`。
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

    private val usernameTitleRegex = Regex("""^(.{1,24}?)的个人资料""")
    private val uidRegex = Regex("""uid=(\d+)""")
    private const val PROFILE_URL = "https://mce.msi.cn/home.php?mod=space&do=profile"

    suspend fun probeLoginState(): Boolean? = withContext(Dispatchers.IO) {
        val cookie = WebCookies.cookieHeader()
        if (cookie.isNullOrBlank()) {
            clearLocal()
            return@withContext false
        }

        val profileHtml = runCatching { GenericPageClient.get(PROFILE_URL) }.getOrNull()
            ?: return@withContext null // 网络失败：保持现状，不误判踢人

        val doc = Jsoup.parse(profileHtml, Net.BASE)
        // 移动版资料页标题并不一定是「X的个人资料」，不能只看标题。
        // 判定证据：登录表单 = 游客；含 spacecp/退出 链接 = 已登录。
        val looksLikeLoginPage = doc.selectFirst("input[name=password]") != null &&
            doc.selectFirst("input[name=username]") != null
        if (looksLikeLoginPage) {
            clearLocal()
            return@withContext false
        }

        val memberEvidence = hasLogoutEvidence(profileHtml) || profileHtml.contains("spacecp") ||
            profileHtml.contains("action=logout")
        if (!memberEvidence) return@withContext null // 无法判断，保持现状

        isLoggedIn = true
        username = usernameTitleRegex.find(doc.title())?.groupValues?.get(1)?.trim()
            ?: doc.select("h1, h2").map { it.text().trim() }
                .firstOrNull { it.isNotBlank() && it.length <= 24 && !it.contains("\u8d44\u6599") }
        val avatarImg = doc.selectFirst("img[src*=avatar.php]")?.attr("src")
            ?.takeIf { it.isNotBlank() }?.replace("&amp;", "&")?.let(Net::absolutize)
        uid = avatarImg?.let { uidRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: doc.selectFirst("a[href*=uid=]")?.attr("href")
                ?.let { uidRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        avatarUrl = avatarImg ?: uid?.let { "${Net.BASE}/uc_server/avatar.php?uid=$it&size=middle" }
        true
    }

    fun clearLocal() {
        isLoggedIn = false
        username = null
        avatarUrl = null
        uid = null
        // v0.3.10：退出登录清掉首页缓存，避免下个账号看到上个账号的列表
        FeedCache.clear()
    }

    private fun hasLogoutEvidence(html: String): Boolean =
        html.contains("action=logout") || html.contains("\u9000\u51fa\u767b\u5f55")
}
