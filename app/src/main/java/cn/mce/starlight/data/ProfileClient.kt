package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 个人资料页解析（home.php?mod=space&do=profile）。
 * 输出头像、昵称、用户组、统计（主题/回帖/积分）、资料字段、入口链接。
 */
object ProfileClient {
    private const val BASE = Net.BASE
    // v0.3.3 批 3：正则常量 + 按标签动态编译的正则做缓存
    private val usernameTitleRegex = Regex("""^(.{1,24}?)的个人资料""")
    private val groupRegex = Regex("""用户组[:：]?\s*([^\s|]{1,20})""")
    private val labelNumberCache = mutableMapOf<String, Regex>()
    private val labelTextCache = mutableMapOf<String, Regex>()
    private fun labelNumberRegex(label: String): Regex = synchronized(labelNumberCache) {
        labelNumberCache.getOrPut(label) { Regex("""$label\s*[:：]?\s*(\d+)""") }
    }
    private fun labelTextRegex(label: String): Regex = synchronized(labelTextCache) {
        labelTextCache.getOrPut(label) { Regex("""$label\s*[:：]?\s*([^\s|]{1,24})""") }
    }
    private const val URL = "$BASE/home.php?mod=space&do=profile"

    data class Profile(
        val avatarUrl: String?,
        val username: String?,
        val group: String?,
        val stats: Map<String, String>,
        val fields: Map<String, String>,
        val entries: List<Pair<String, String>>,
        /** v0.3.10：管理员 / 超级版主 / 版主；普通用户为 null（界面不变） */
        val staff: String? = null,
        /** v0.4.1：用户等级（与用户组分开显示） */
        val level: String? = null,
    )

    private val FIELD_LABELS = listOf("UID", "姓名", "年级", "学校", "专业", "生日", "来自", "个人网站", "血型")

    suspend fun load(): Profile? = withContext(Dispatchers.IO) {
        runCatching {
            val html = GenericPageClient.get(URL)
            // v0.4.1 复核用：临时把资料页 HTML 落盘（验证后关闭）
            cn.mce.starlight.core.DebugDump.dump("profile", html)
            val doc = Jsoup.parse(html, URL)
            val text = doc.text()

            val avatar = doc.selectFirst("img[src*=avatar.php]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)

            val username = ProfileCommon.username(doc)
            val group = ProfileCommon.group(doc, text)
            val staff = ProfileCommon.staffTitle(doc, text)
            val level = ProfileCommon.level(doc, text)
            val stats = ProfileCommon.stats(text, listOf("主题", "回帖", "积分", "好友", "威望"))
            val fields = ProfileCommon.fields(text, FIELD_LABELS)

            val entries = doc.select("a[href]").mapNotNull { a ->
                val href = a.attr("href")
                val name = a.text().trim()
                val relevant = href.contains("do=thread") || href.contains("do=reply") ||
                    href.contains("do=favorite") || href.contains("do=notice") ||
                    href.contains("do=friend") || href.contains("do=credit") ||
                    href.contains("do=medal") || href.contains("do=trade")
                if (!relevant || name.isBlank() || name.length > 12) return@mapNotNull null
                name to absolutize(href)
            }.distinctBy { it.second }

            Profile(
                avatarUrl = avatar,
                username = username,
                group = group,
                stats = stats,
                fields = fields,
                entries = entries,
                staff = staff,
                level = level,
            )
        }.getOrNull()
    }

    private fun absolutize(href: String): String = Net.absolutize(href)
}
