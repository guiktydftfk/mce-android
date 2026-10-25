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
 */
object MobileForumParser {

    private val tidRegex = Regex("""tid=(\d+)""")
    private val replyRegex = Regex("""(\d+)\s*(?:\u56de\u590d|\u9605\u8bfb)""")

    fun parse(html: String, baseUrl: String, page: Int): ForumPage {
        val doc = Jsoup.parse(html, baseUrl)
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

        val meta = li.selectFirst("span.y")?.text()?.replace(Regex("""\s+"""), " ")?.trim()
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
     * Next page detection: explicit "next" anchor, any page=N link greater than current,
     * or a pagination container (touch template often loads more on scroll).
     */
    private fun hasNext(doc: Document, page: Int): Boolean {
        if (doc.select("a").any { it.text().contains("\u4e0b\u4e00\u9875") }) return true

        val numbers = doc.select("a[href*=page=]").mapNotNull {
            Regex("""page=(\d+)""").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }
        if (numbers.any { it > page }) return true

        return doc.select(".pg, .pagination, #autopage").isNotEmpty()
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "https://mce.msi.cn$src"
        else -> "https://mce.msi.cn/$src"
    }
}
