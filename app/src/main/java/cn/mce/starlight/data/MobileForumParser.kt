package cn.mce.starlight.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Thread list parser (Discuz touch template): forumdisplay + guide.
 *
 * li.subjects_<N>.mtb
 *   a.avatar > img          author avatar
 *   span.th_author > a      author
 *   span.y                  "date / N replies"
 *   a.th_item[h1]           title (href contains tid)
 *   span.threadtypes        type label
 *
 * v0.3.3 批 3：
 *  - 新增接受 [Document] 的重载，搜索页等已经解析过 DOM 的调用方不再重复 `Jsoup.parse`
 *  - 上/下一页判定只扫 `a[href*=page=]`（旧实现遍历全页所有锚点 + "有分页容器就算有下一页"的恒真兜底）
 *  - 循环内正则提升为常量
 */
object MobileForumParser {

    private val tidRegex = Regex("""tid=(\d+)""")
    private val replyRegex = Regex("""(\d+)\s*(?:\u56de\u590d|\u9605\u8bfb)""")
    private val whitespaceRegex = Regex("""\s+""")
    private val pageRegex = Regex("""page=(\d+)""")

    fun parse(html: String, baseUrl: String, page: Int): ForumPage =
        parse(Jsoup.parse(html, baseUrl), page)

    /** 复用已解析的 [doc]（避免同一份 HTML 解析两遍） */
    fun parse(doc: Document, page: Int): ForumPage {
        val items = doc.select("li[class*=subjects_]")
            .ifEmpty { doc.select("ul#alist > li") }

        val threads = items.mapNotNull { parseItem(it) }
        return ForumPage(
            threads = threads,
            page = page,
            hasNextPage = hasNext(doc, page),
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

        val meta = li.selectFirst("span.y")?.text()?.replace(whitespaceRegex, " ")?.trim()
        val avatar = li.selectFirst("a.avatar img, .th_authi img")
            ?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?.let { absolutize(it) }

        return ThreadSummary(
            id = tid,
            title = title.trim(),
            author = li.selectFirst("span.th_author")?.text()?.trim()?.takeIf { it.isNotBlank() },
            avatarUrl = avatar,
            replies = meta?.let { replyRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() },
            metaText = meta,
            typeLabel = li.selectFirst("span.threadtypes")?.text()?.trim()?.takeIf { it.isNotBlank() },
            summary = li.selectFirst("a.th_item p")?.text()?.trim()?.takeIf { it.isNotBlank() },
            forumName = li.selectFirst("a[href*=forumdisplay]")?.text()?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * 是否有下一页：只检查页码锚点里有没有 `page > 当前页`。
     * （旧实现：`doc.select("a")` 全页遍历 + 每锚点编译正则，最后还有"页面有 .pg 容器就 true"的恒真兜底）
     */
    private fun hasNext(doc: Document, page: Int): Boolean =
        doc.select("a[href*=page=]").any { link ->
            val n = pageRegex.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            n != null && n > page
        }

    private fun absolutize(src: String): String = Net.absolutize(src)
}
