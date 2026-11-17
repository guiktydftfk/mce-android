package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * v0.4.8：在线会员 / 论坛在线统计。
 *
 * 手机模板没有在线会员页，这里走电脑版页面：
 *   `forum.php?mod=online&mobile=no`
 * 解析「共 N 人在线 - N 会员(0 隐身), N 位游客」与会员列表（uid + 昵称）。
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
        /** v0.4.8：页面原始统计行 */
        /** v0.4.8：隐身会员数 */
        val hiddenCount: Int? = null,
        /** v0.4.8：历史最高在线人数与日期 */
        val maxOnline: Int? = null,
        val maxOnlineDate: String? = null,
        /** v0.4.8：用户组图例（管理员 / 超级版主 / 版主 / 会员…） */
        val legend: List<String> = emptyList(),
        val rawStats: String? = null,
    )

    suspend fun load(): OnlinePage = withContext(Dispatchers.IO) {
        val urls = listOf(
            "${Net.BASE}/forum.php?mod=online&mobile=no",
            "${Net.BASE}/forum.php?mod=online",
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

    /** 解析在线会员页（提取为可测试函数） */
    internal fun parse(html: String, baseUrl: String): OnlinePage {
        val doc = Jsoup.parse(html, baseUrl)
        // v0.4.8：统计数字只在「在线会员」标题行里找，
        // 避免页脚「会员总数: 469764」被当成在线会员数。
        val caption = doc.selectFirst("h1, h2")
            ?.text()?.trim()
            ?.takeIf { it.contains("在线") }
            ?: "在线会员"
        // v0.4.8：统计行可能不在标题里（部分模板把「共 N 人在线」放在 bm 子标题），
        // 全文档找第一条包含「人在线」的短文本行。
        val statsLine = doc.select("h1, h2, p, div").map { it.text().trim() }
            .filter { it.contains("人在线") && it.length <= 200 }
            .maxByOrNull { it.length }
            ?: doc.text().lineSequence().firstOrNull { it.contains("人在线") }
            ?: ""
        val total = Regex("""共\s*(\d+)\s*人在线""").find(statsLine)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(\d+)\s*人在线""").find(statsLine)?.groupValues?.get(1)?.toIntOrNull()
        val memberCount = Regex("""(\d+)\s*(?:位)?会员\s*\(\s*(\d+)\s*隐身""")
            .find(statsLine)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(\d+)\s*(?:位)?会员""").find(statsLine)?.groupValues?.get(1)?.toIntOrNull()
        val guestCount = Regex("""(\d+)\s*位游客""").find(statsLine)?.groupValues?.get(1)?.toIntOrNull()
        val hiddenCount = Regex("""(\d+)\s*会员\s*\(\s*(\d+)\s*隐身""")
            .find(statsLine)?.groupValues?.get(2)?.toIntOrNull()
        val maxOnline = Regex("""最高记录是\s*(\d+)""").find(statsLine)?.groupValues?.get(1)?.toIntOrNull()
        val maxOnlineDate = Regex("""于\s*(\d{4}-[\d\-/]+)""").find(statsLine)?.groupValues?.get(1)?.trim()
        // 图例：Discuz 在线页固定组名，按出现顺序保留
        val legend = listOf("管理员", "超级版主", "版主", "会员", "禁止发言", "游客")
            .filter { statsLine.contains(it) }
        // 优先专用容器；找不到就退化为全页 uid 链接（不同模板结构差异大）
        val scope = doc.selectFirst("div.onlinelist, ul.onlinelist, div#onlinelist, div.bm, div#ct")
            ?: doc.body()
        val members = scope.select("a[href*=uid=]").mapNotNull { a ->
            val href = a.attr("href")
            val uid = Regex("""uid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = a.text().trim().trim('[', ']', ' ', '\u3000')
            if (name.isBlank() || name.length > 24) return@mapNotNull null
            if (name in setOf("登录", "注册", "找回密码", "退出")) return@mapNotNull null
            Member(
                name = name,
                uid = uid,
                avatar = "${Net.BASE}/uc_server/avatar.php?uid=$uid&size=small",
            )
        }.distinctBy { it.uid }.take(200)
        return OnlinePage(
            caption = caption,
            total = total,
            memberCount = memberCount,
            guestCount = guestCount,
            members = members,
            rawStats = statsLine.takeIf { it.isNotBlank() },
            hiddenCount = hiddenCount,
            maxOnline = maxOnline,
            maxOnlineDate = maxOnlineDate,
            legend = legend,
        )
    }
}
