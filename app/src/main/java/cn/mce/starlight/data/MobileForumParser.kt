package cn.mce.starlight.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 帖子列表解析器（Discuz 触屏模板）。
 * 同时适配 forumdisplay（版块列表）与 guide（看帖中心）。
 *
 * 实测结构：
 *   li.subjects_<N>.mtb
 *     a.avatar > img            作者头像
 *     span.th_author > a        作者
 *     span.y                    "2022-6-3 / 1338回复"
 *     a.th_item[h1]             标题（href 含 tid）
 *     span.threadtypes          分类标签
 */
object MobileForumParser {

    private val tidRegex = Regex("""tid=(\d+)""")
    private val replyRegex = Regex("""(\d+)\s*(?:回复|阅读)""")

    fun parse(html: String, baseUrl: String, page: Int): ForumPage {
        val doc = Jsoup.parse(html, baseUrl)
        val items = doc.select("li[class*=subjects_]")
            .ifEmpty { doc.select("ul#alist > li") }

        val threads = items.mapNotNull { parseItem(it) }
        return ForumPage(
            threads = threads,
            page = page,
            hasNextPage = doc.select("a").any { it.text().contains("下一页") },
        )
    }

    private fun parseItem(li: Element): ThreadSummary? {
        val link = li.selectFirst("a.th_item")
            ?: li.selectFirst("a[href*=viewthread]")
            ?: return null
        val href = link.attr("href")
        val tid = tidRegex.find(href)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val title = (link.selectFirst("h1") ?: link.selectFirst("h2") ?: link).text().trim()
        if (title.isBlank()) return null

        val meta = li.selectFirst("span.y")?.text()?.replace(Regex("""\s+"""), " ")?.trim()
        val avatar = li.selectFirst("a.avatar img, .th_authi img")
            ?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?.let { absolutize(it) }

        return ThreadSummary(
            id = tid,
            title = title.removePrefix("[置顶]").trim(),
            author = li.selectFirst("span.th_author")?.text()?.trim()?.takeIf { it.isNotBlank() },
            avatarUrl = avatar,
            replies = meta?.let { replyRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() },
            metaText = meta,
            typeLabel = li.selectFirst("span.threadtypes")?.text()?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "https://mce.msi.cn$src"
        else -> "https://mce.msi.cn/$src"
    }
}
