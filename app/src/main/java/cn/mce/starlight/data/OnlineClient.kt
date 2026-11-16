package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * v0.4.8：在线会员 / 论坛在线统计。
 *
 * 手机模板没有在线会员页，这里走电脑版页面：
 *   `forum.php?mod=online&mobile=no`
 * 解析「共 N 人在线 - N 会员, N 位游客」与会员列表（uid + 昵称）。
 */
object OnlineClient {

    data class Member(
        val name: String,
        val uid: Int,
        val avatar: String? = null,
        /** 用户组 / 所在版块等附加信息（能解析到就展示） */
        val extra: String? = null,
    )

    data class OnlinePage(
        val caption: String,
        val total: Int?,
        val memberCount: Int?,
        val guestCount: Int?,
        val members: List<Member>,
    )

    suspend fun load(): OnlinePage = withContext(Dispatchers.IO) {
        val urls = listOf(
            "$BASE_NET/forum.php?mod=online&mobile=no",
            "$BASE_NET/forum.php?mod=online",
        )
        for (url in urls) {
            val html = runCatching {
                GenericPageClient.get(url, userAgent = Net.DESKTOP_UA, isolateCookies = true)
            }.getOrNull() ?: continue
            val page = parse(html, url)
            if (page.members.isNotEmpty() || page.total != null) return@withContext page
        }
        OnlinePage("在线会员", null, null, null, emptyList())
    }

    private const val BASE_NET = Net.BASE

    /** 解析在线会员页（提取为可测试函数） */
    internal fun parse(html: String, baseUrl: String): OnlinePage {
        val doc = Jsoup.parse(html, baseUrl)
        val text = doc.text()
        val total = Regex("""(\d+)\s*人在线""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val memberCount = Regex("""(\d+)\s*(?:位)?会员""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val guestCount = Regex("""(\d+)\s*(?:位)?游客""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val caption = doc.selectFirst("h1, h2")
            ?.text()?.trim()
            ?.takeIf { it.contains("在线") }
            ?: "在线会员"
        // 优先专用容器；找不到就退化为全页 uid 链接（不同模板结构差异大）
        val scope = doc.selectFirst("div.onlinelist, ul.onlinelist, div#onlinelist, div.bm, div#ct")
            ?: doc.body()
        val members = scope.select("a[href*=uid=]").mapNotNull { a ->
            val href = a.attr("href")
            val uid = Regex("""uid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = a.text().trim()
            if (name.isBlank() || name.length > 24) return@mapNotNull null
            if (name in setOf("登录", "注册", "找回密码", "退出")) return@mapNotNull null
            Member(
                name = name,
                uid = uid,
                avatar = "$BASE_NET/uc_server/avatar.php?uid=$uid&size=small",
            )
        }.distinctBy { it.uid }.take(200)
        return OnlinePage(caption, total, memberCount, guestCount, members)
    }
}
