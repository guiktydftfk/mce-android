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
        /** v0.4.1：我的资料 —— 自定义头衔 / 个人签名 / 邮箱状态 */
        val customTitle: String? = null,
        val signature: String? = null,
        val emailStatus: String? = null,
        /** v0.4.1：活跃概况（用户组/在线时间/注册时间/最后访问/注册IP/上次访问IP/上次活动时间/上次发表时间/所在时区） */
        val active: Map<String, String> = emptyMap(),
    )

    private val FIELD_LABELS = listOf(
        "性别", "生日", "家乡", "居住地", "学历", "学校", "年级", "专业", "姓名", "QQ号", "微信号",
    )

    /** v0.4.1：活跃概况字段（顺序即展示顺序） */
    private val ACTIVE_LABELS = listOf(
        "用户组", "在线时间", "注册时间", "最后访问", "注册IP", "上次访问 IP", "上次活动时间",
        "上次发表时间", "所在时区",
    )
    private val labelValueCache = mutableMapOf<String, Regex>()
    private fun labelValueRegex(label: String): Regex = synchronized(labelValueCache) {
        labelValueCache.getOrPut(label) {
            Regex("""$label\s*[:：]?\s*([^\s|]{1,40})(?:\s|\||$)""")
        }
    }

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

            // v0.4.1：自己的“我的资料”补抓电脑版详情页（手机模板没有活跃概况/签名/头衔）
            val selfUid = cn.mce.starlight.core.SessionStore.uid
            val isSelf = (uid == null || uid == selfUid) && (text.contains("\u9000\u51fa") || html.contains("action=logout"))
            var detailText = text
            if (isSelf && realUid > 0) {
                val desktopUrl = "${Net.BASE}/home.php?mod=space&uid=$realUid&do=profile&mobile=no"
                runCatching {
                    GenericPageClient.get(desktopUrl, userAgent = Net.DESKTOP_UA, isolateCookies = true)
                }.getOrNull()?.let { desktopHtml ->
                    detailText = Jsoup.parse(desktopHtml, desktopUrl).text()
                }
            }
            val active = ACTIVE_LABELS.mapNotNull { label ->
                labelValueRegex(label).find(detailText)?.groupValues?.get(1)
                    ?.trim()?.takeIf { it.isNotBlank() }?.let { label to it }
            }.toMap()
            val customTitle = Regex("""自定义头衔\s*[:：]?\s*(\S{1,20})""")
                .find(detailText)?.groupValues?.get(1)
            val signature = Regex("""个人签名\s*[:：]?\s*(.{2,60}?)(?=\s*统计信息|\s*好友数|$)""")
                .find(detailText)?.groupValues?.get(1)?.trim()
            val emailStatus = Regex("""邮箱状态\s*[:：]?\s*(\S{1,10})""")
                .find(detailText)?.groupValues?.get(1)

            Profile(
                uid = realUid,
                name = name,
                avatarUrl = avatar,
                group = group,
                stats = stats,
                fields = fields,
                recentThreads = threads,
                isSelf = isSelf,
                customTitle = customTitle,
                signature = signature,
                emailStatus = emailStatus,
                active = active,
            )
        }.getOrNull()
    }

    private fun absolutize(src: String): String = Net.absolutize(src)
}
