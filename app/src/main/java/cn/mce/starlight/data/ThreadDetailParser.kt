package cn.mce.starlight.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 帖子详情解析器（Discuz 触屏模板）。
 *
 * 实测结构：
 *   div.plc[id=pid<digits>]
 *     div.view_authi
 *       a.avatars > img                     头像
 *       span.view_author > a                作者
 *       span.datelinetimes                  时间
 *     div.message                           正文（可能含 img）
 *
 * 兼容性：不同模板/权限下节点名可能不同，因此按多种选择器回退。
 */
object ThreadDetailParser {

    private val pidRegex = Regex("""pid(\d+)""")

    private val blockSelectors = listOf(
        "div.plc[id^=pid]",
        "div.plc",
        "div.viewpost",
        "div[id^=post_]",
    )

    fun parse(html: String, baseUrl: String, tid: Int, page: Int): ThreadDetail {
        val doc = Jsoup.parse(html, baseUrl)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().substringBefore(" - ").trim()

        val blocks = blockSelectors
            .map { doc.select(it) }
            .firstOrNull { it.isNotEmpty() }
            ?: emptyList()

        val posts = blocks.mapIndexedNotNull { index, el -> parsePost(el, index) }

        val replies = Regex("""(\d+)\s*回复""")
            .find(doc.selectFirst("span.z")?.text().orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull()

        return ThreadDetail(
            tid = tid,
            title = title,
            replies = replies,
            posts = posts,
            page = page,
            hasNextPage = doc.select("a").any { it.text().contains("下一页") },
        )
    }

    private fun parsePost(el: Element, index: Int): Post? {
        val pid = pidRegex.find(el.id())?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

        val authi = el.selectFirst("div.view_authi")
            ?: el.selectFirst(".view_authi")
            ?: el.selectFirst(".plc .pi")

        val author = authi?.selectFirst("span.view_author a")?.text()?.trim()
            ?: authi?.select("a[href*=space]")?.firstOrNull { it.text().isNotBlank() }?.text()?.trim()
            ?: "匿名"

        val avatar = authi?.selectFirst("a.avatars img, img")?.attr("src")
            ?.takeIf { it.isNotBlank() }?.let { absolutize(it) }

        val message = el.selectFirst("div.message")
            ?: el.selectFirst(".message")

        val contentHtml = message?.html().orEmpty()
        val contentText = message?.text()?.trim().orEmpty()
        val images = message?.select("img")?.mapNotNull { img ->
            img.attr("src").takeIf { it.isNotBlank() }?.let { absolutize(it) }
        }.orEmpty()

        val date = el.selectFirst("span.datelinetimes")?.text()?.trim()
            ?: authi?.selectFirst("span.datelinetimes")?.text()?.trim()

        return Post(
            pid = pid,
            author = author,
            avatarUrl = avatar,
            contentHtml = contentHtml,
            contentText = contentText,
            images = images,
            floorLabel = if (index == 0) "楼主" else "${index + 1}楼",
            dateText = date,
            isOp = index == 0,
        )
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "https://mce.msi.cn$src"
        else -> "https://mce.msi.cn/$src"
    }
}
