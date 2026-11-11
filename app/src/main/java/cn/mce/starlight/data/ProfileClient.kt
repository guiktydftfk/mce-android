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
        /** v0.4.1：积分明细（社区值/赞赏值/职务值/活动值/实践值/已使用） */
        val credits: Map<String, String> = emptyMap(),
    )

    private val FIELD_LABELS = listOf(
        "UID", "用户ID", "姓名", "年级", "学校", "专业", "生日", "来自", "个人网站", "血型", "QQ号", "微信号",
    )
    /** v0.4.1：积分明细字段顺序（网页端资料页 ul.user_box 里刚好是这些） */
    private val CREDIT_LABELS = listOf("社区值", "赞赏值", "职务值", "活动值", "实践值", "已使用")

    /**
     * v0.4.7：多数据源取「精确积分」。
     * 手机版资料页会把大额积分四舍五入成「1.4万」，这里依次尝试：
     * 电脑版资料页 → 积分明细页（电脑）→ 积分明细页（手机），
     * 每个页面取「积分」后面最大的合法数字（规避页面里其它小数字）。
     */
    private suspend fun exactPoints(): Int? = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "home.php?mod=space&do=profile&mobile=no" to true,
            "home.php?mod=spacecp&ac=credit&mobile=no" to true,
            "home.php?mod=spacecp&ac=credit" to false,
        )
        for ((path, desktop) in candidates) {
            val html = runCatching {
                GenericPageClient.get(
                    Net.absolutize(path),
                    userAgent = if (desktop) Net.DESKTOP_UA else null,
                    isolateCookies = true,
                )
            }.getOrNull() ?: continue
            val value = Regex("""积分[^\d]{0,12}([\d,]{2,})""").findAll(html)
                .mapNotNull { it.groupValues[1].replace(",", "").toIntOrNull() }
                .filter { it > 0 }
                .maxOrNull()
            if (value != null) return@withContext value
        }
        null
    }

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
            // v0.4.1：记录管理身份，帖子页据此判断是否补抓电脑版管理条
            cn.mce.starlight.core.SessionStore.updateStaff(staff)
            val level = ProfileCommon.level(doc, text)
            val labels = listOf("主题", "回帖", "积分", "好友", "威望")
            // v0.4.7：先结构化解析（避免整页正则误命中「积分 1」），取不到再退回整页正则
            var stats = ProfileCommon.statsFromDoc(doc, labels).ifEmpty { ProfileCommon.stats(text, labels) }
            // v0.4.7：手机版大额积分显示为「1.4万」这类近似值（换算成 14000，与真实 13661 不符）。
            // 只要页面上出现近似单位或积分缺失，就用多数据源取精确值：电脑版资料页 → 积分明细页（电脑/手机）。
            val approximated = Regex("""积分\s*[:：]?\s*[\d.,]+\s*[万千]""").containsMatchIn(text)
            if (approximated || stats["积分"] == null) {
                exactPoints()?.let { stats = stats + ("积分" to it.toString()) }
            }
            val fields = ProfileCommon.fieldsFromBox(doc, text, FIELD_LABELS)
            // v0.4.1：积分明细（社区值/赞赏值/…）也在 ul.user_box 里
            val creditBox = ProfileCommon.boxKv(doc)
            val credits = CREDIT_LABELS.mapNotNull { label ->
                creditBox[label]?.takeIf { it.isNotBlank() }?.let { label to it }
            }.toMap()

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
                credits = credits,
            )
        }.getOrNull()
    }

    private fun absolutize(href: String): String = Net.absolutize(href)
}
