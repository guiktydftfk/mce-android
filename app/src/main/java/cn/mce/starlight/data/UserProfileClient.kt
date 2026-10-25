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
    private const val BASE = "https://mce.msi.cn"

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

    private val FIELD_LABELS = listOf("性别", "生日", "家乡", "居住地", "学历", "学校", "年级", "专业", "姓名", "QQ", "微信")

    suspend fun load(uid: Int?): Profile? = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (uid == null) "$BASE/home.php?mod=space&do=profile"
            else "$BASE/home.php?mod=space&uid=$uid&do=profile"
            val html = GenericPageClient.get(url)
            cn.mce.starlight.core.DebugDump.dump("space_${uid ?: 0}", html)
            val doc = Jsoup.parse(html, url)
            val text = doc.text()

            val realUid = uid ?: Regex("""uid=(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val avatar = doc.selectFirst("img[src*=avatar.php]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)

            val name = Regex("""^(.{1,24}?)的个人资料""").find(doc.title())?.groupValues?.get(1)?.trim()
                ?: doc.selectFirst("h1, h2, .user_name, .xs2")?.text()?.trim()
                ?.takeIf { it.length in 1..24 }
                ?: "用户$realUid"

            val group = Regex("""用户组[:：]?\s*([^\s|]{1,20})""").find(text)?.groupValues?.get(1)
                ?: doc.selectFirst(".grouptitle, .xw1")?.text()?.trim()

            val stats = mutableMapOf<String, String>()
            listOf("主题", "回帖", "积分", "好友", "威望", "在线时间").forEach { label ->
                Regex("""$label\s*[:：]?\s*(\d+)""").find(text)?.groupValues?.get(1)?.let { stats[label] = it }
            }

            val fields = mutableMapOf<String, String>()
            FIELD_LABELS.forEach { label ->
                Regex("""$label\s*[:：]?\s*([^\s|]{1,24})""").find(text)?.groupValues?.get(1)
                    ?.trim()?.let { if (it.isNotBlank()) fields[label] = it }
            }

            val threads = doc.select("a[href*=viewthread]").mapNotNull { a ->
                val tid = Regex("""tid=(\d+)""").find(a.attr("href"))
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

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }
}
