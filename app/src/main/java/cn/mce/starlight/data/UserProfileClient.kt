package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 个人空间页（home.php?mod=space&uid=X）。
 * 实测（touch 模板）：
 *   div.user_info / h2 昵称，img[src*=avatar.php] 头像，.xg1 用户组
 *   div.info .item 统计（主题/回帖/积分…）
 *   ul 列表里指向 viewthread 的链接 = 最近主题
 *   dt/dd 或 li 里「性别 / 生日 / 家乡 / 居住地」等资料
 */
object UserProfileClient {
    private const val BASE = Net.BASE
    // v0.3.3 批 3：正则常量 + 按标签动态编译的正则做缓存（标签只有固定几个）
    private val uidRegex = Regex("""uid=(\d+)""")
    private val usernameTitleRegex = Regex("""^(.{1,24}?)的个人资料""")
    private val groupRegex = Regex("""用户组[:：]?\s*([^\s|]{1,20})""")
    private val tidRegex = Regex("""tid=(\d+)""")
    private val labelNumberCache = mutableMapOf<String, Regex>()
    private val labelTextCache = mutableMapOf<String, Regex>()
    private fun labelNumberRegex(label: String): Regex = synchronized(labelNumberCache) {
        labelNumberCache.getOrPut(label) { Regex("""$label\s*[:：]?\s*(\d+)""") }
    }
    private fun labelTextRegex(label: String): Regex = synchronized(labelTextCache) {
        labelTextCache.getOrPut(label) { Regex("""$label\s*[:：]?\s*([^\s|]{1,24})""") }
    }

    data class Profile(
        val uid: Int,
        val name: String,
        val avatarUrl: String?,
        val group: String?,
        val stats: Map<String, String>,
        val fields: Map<String, String>,
        val recentThreads: List<Pair<Int, String>>,
        val isSelf: Boolean,
    )

    private val FIELD_LABELS = listOf(
        "性别", "生日", "家乡", "居住地", "学历", "学校", "年级", "专业", "姓名", "QQ号", "微信号",
    )

    suspend fun load(uid: Int?): Profile? = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (uid == null) "${Net.BASE}/home.php?mod=space&do=profile"
            else "${Net.BASE}/home.php?mod=space&uid=$uid&do=profile"
            val html = GenericPageClient.get(url)
            cn.mce.starlight.core.DebugDump.dump("space_${uid ?: 0}", html)
            val doc = Jsoup.parse(html, url)
            val text = doc.text()

            val realUid = uid ?: uidRegex.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val avatar = doc.selectFirst("img[src*=avatar.php]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)

            val name = ProfileCommon.username(doc) ?: "用户$realUid"
            val group = ProfileCommon.group(doc, text)
            val stats = ProfileCommon.stats(
                text,
                listOf("主题", "回帖", "积分", "好友", "威望", "在线时间"),
            )
            // v0.4.1：优先按 ul.user_box 结构化字段解析（旧正则把「QQ号」截成「号」）
            val fields = ProfileCommon.fieldsFromBox(doc, text, FIELD_LABELS)

            val threads = doc.select("a[href*=viewthread]").mapNotNull { a ->
                val tid = tidRegex.find(a.attr("href"))
                    ?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                val title = a.text().trim()
                if (title.length < 4) null else tid to title
            }.distinctBy { it.first }.take(10)

            Profile(
                uid = realUid,
                name = name,
                avatarUrl = avatar,
                group = group,
                stats = stats,
                fields = fields,
                recentThreads = threads,
                isSelf = text.contains("\u9000\u51fa") || html.contains("action=logout"),
            )
        }.getOrNull()
    }

    private fun absolutize(src: String): String = Net.absolutize(src)
}
