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
    // v0.4.6 修复：旧实现同时匹配「N 回复」和「N 阅读」→ 未开放回复/无人回复的帖子会把阅读数当回复数
    private val replyRegex = Regex("""(?:\u56de\u590d[:：]?\s*(\d+)|(\d+)\s*\u56de\u590d)""")
    // v0.4.6：阅读数单独解析，不再混进回复数
    private val viewsRegex = Regex("""(\d+)\s*\u9605\u8bfb""")
    private val whitespaceRegex = Regex("""\s+""")
    private val pageRegex = Regex("""page=(\d+)""")

    /** v0.4.7：站点偶发双重转义（&amp;#129402; → 文本里留下 &#129402;），这里再解一次实体 */
    private fun decodeEntities(s: String?): String? {
        if (s == null || !s.contains('&')) return s
        return runCatching { org.jsoup.parser.Parser.unescapeEntities(s, false) }.getOrDefault(s)
    }

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

    /** 只解析「回复」数；「阅读」数不算回复数（v0.4.6 修复） */
    private fun repliesOf(meta: String?): Int? {
        val m = meta?.let { replyRegex.find(it) } ?: return null
        return m.groupValues[1].ifBlank { m.groupValues[2] }.toIntOrNull()
    }

    private fun parseItem(li: Element): ThreadSummary? {
        val link = li.selectFirst("a.th_item")
            ?: li.selectFirst("a[href*=viewthread]")
            ?: return null
        val href = link.attr("href")
        val tid = tidRegex.find(href)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val title = decodeEntities((link.selectFirst("h1") ?: link.selectFirst("h2") ?: link).text().trim()) ?: ""
        if (title.isBlank()) return null

        val meta = li.selectFirst("span.y")?.text()?.replace(whitespaceRegex, " ")?.trim()
        val avatar = li.selectFirst("a.avatar img, .th_authi img")
            ?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?.let { absolutize(it) }

        return ThreadSummary(
            id = tid,
            title = title.trim(),
            author = decodeEntities(li.selectFirst("span.th_author")?.text()?.trim())?.takeIf { it.isNotBlank() },
            avatarUrl = avatar,
            replies = repliesOf(meta),
            views = meta?.let { viewsRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() },
            metaText = meta,
            typeLabel = decodeEntities(li.selectFirst("span.threadtypes")?.text()?.trim())?.takeIf { it.isNotBlank() },
            summary = decodeEntities(li.selectFirst("a.th_item p")?.text()?.trim())?.takeIf { it.isNotBlank() },
            forumName = decodeEntities(li.selectFirst("a[href*=forumdisplay]")?.text()?.trim())?.takeIf { it.isNotBlank() },
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
