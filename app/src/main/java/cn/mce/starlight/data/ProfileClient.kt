package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 个人资料页解析（home.php?mod=space&do=profile）。
 * 输出头像、昵称、用户组、统计（主题/回帖/积分）、资料字段、入口链接。
 */
object ProfileClient {
    private const val BASE = "https://mce.msi.cn"
    private const val URL = "$BASE/home.php?mod=space&do=profile"

    data class Profile(
        val avatarUrl: String?,
        val username: String?,
        val group: String?,
        val stats: Map<String, String>,
        val fields: Map<String, String>,
        val entries: List<Pair<String, String>>,
    )

    private val FIELD_LABELS = listOf("UID", "姓名", "年级", "学校", "专业", "生日", "来自", "个人网站", "血型")

    suspend fun load(): Profile? = withContext(Dispatchers.IO) {
        runCatching {
            val html = GenericPageClient.get(URL)
            val doc = Jsoup.parse(html, URL)
            val text = doc.text()

            val avatar = doc.selectFirst("img[src*=avatar.php]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)

            val username = Regex("""^(.{1,24}?)的个人资料""").find(doc.title())?.groupValues?.get(1)?.trim()
                ?: doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.length in 1..24 }

            val group = Regex("""用户组[:：]?\s*([^\s|]{1,20})""").find(text)?.groupValues?.get(1)
                ?: doc.selectFirst(".grouptitle, .xw1")?.text()?.trim()

            val stats = mutableMapOf<String, String>()
            listOf("主题", "回帖", "积分", "好友", "威望").forEach { label ->
                Regex("""$label\s*[:：]?\s*(\d+)""").find(text)?.groupValues?.get(1)?.let { stats[label] = it }
            }

            val fields = mutableMapOf<String, String>()
            FIELD_LABELS.forEach { label ->
                val m = Regex("""$label\s*[:：]?\s*([^\s|]{1,24})""").find(text)
                m?.groupValues?.get(1)?.trim()?.let { if (it.isNotBlank()) fields[label] = it }
            }

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
            )
        }.getOrNull()
    }

    private fun absolutize(href: String): String {
        val h = href.replace("&amp;", "&")
        return when {
            h.startsWith("//") -> "https:$h"
            h.startsWith("http") -> h
            h.startsWith("/") -> "$BASE$h"
            else -> "$BASE/$h"
        }
    }
}
