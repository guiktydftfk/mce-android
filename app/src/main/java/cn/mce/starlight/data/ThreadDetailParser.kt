package cn.mce.starlight.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Thread detail parser (Discuz touch template).
 *
 * Image rules learned from a working HarmonyOS client:
 *  - lazy images: real url is in data-src / file / zoomfile (src is a 1x1 placeholder)
 *  - attachments may only exist as links: <a href="data/attachment/.../x.jpg">点击查看</a>
 *    or <a href="forum.php?mod=attachment&aid=..">x.png</a> or mod=image
 *  - smilies / none.gif / filetype icons are not content images
 */
object ThreadDetailParser {

    private val pidRegex = Regex("""pid(\d+)""")
    private val imageExtRegex = Regex("""\.(jpe?g|png|gif|webp|bmp)(\?|$)""", RegexOption.IGNORE_CASE)

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

        val posts = blocks.mapNotNull { block ->
            parsePost(block, blocks.indexOf(block))
        }

        val fid = Regex("""action=reply&(?:amp;)?fid=(\d+)""").find(html)
            ?.groupValues?.get(1)?.toIntOrNull()

        val rewardCount = doc.selectFirst("div.view_reward .reward_num span")
            ?.text()?.trim()?.toIntOrNull()

        val firstPid = Regex("""id="pid(\d+)"""").find(html)?.groupValues?.get(1)

        return ThreadDetail(
            tid = tid,
            title = title,
            replies = null,
            posts = posts,
            page = page,
            hasNextPage = doc.select("a").any { it.text().contains("\u4e0b\u4e00\u9875") },
            fid = fid,
            formhash = Regex("""name="formhash"\s+value="([a-z0-9]+)"""").find(html)?.groupValues?.get(1),
            rewardCount = rewardCount,
            rewardUrl = firstPid?.let { "forum.php?mod=misc&action=viewratings&tid=$tid&pid=$it" },
        )
    }

    private fun parsePost(el: Element, index: Int): Post? {
        val pid = pidRegex.find(el.id())?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

        val authi = el.selectFirst("div.view_authi")
            ?: el.selectFirst(".view_authi")
            ?: el.selectFirst(".plc .pi")

        val author = authi?.selectFirst("span.view_author a")?.text()?.trim()
            ?: authi?.select("a[href*=space]")?.firstOrNull { it.text().isNotBlank() }?.text()?.trim()
            ?: "\u533f\u540d"

        val avatar = authi?.selectFirst("a.avatars img, img")?.attr("src")
            ?.takeIf { it.isNotBlank() }?.let(::absolutize)

        val message = el.selectFirst("div.message") ?: el.selectFirst(".message")

        val contentHtml = message?.html().orEmpty()
        val contentText = message?.let { htmlToText(it) }.orEmpty()

        val date = el.selectFirst("span.datelinetimes")?.text()?.trim()
            ?: authi?.selectFirst("span.datelinetimes")?.text()?.trim()

        return Post(
            pid = pid,
            author = author,
            avatarUrl = avatar,
            contentHtml = contentHtml,
            contentText = contentText,
            images = message?.let { collectImages(it) }.orEmpty(),
            floorLabel = if (index == 0) "\u697c\u4e3b" else "${index + 1}\u697c",
            dateText = date,
            isOp = index == 0,
        )
    }

    /** Collect content images (lazy attrs + attachment links + data/attachment links). */
    fun collectImages(root: Element): List<String> {
        val result = LinkedHashSet<String>()

        root.select("img").forEach { img ->
            val candidate = sequenceOf("zoomfile", "file", "data-src", "data-original", "src")
                .map { img.attr(it) }
                .firstOrNull { v ->
                    v.isNotBlank() &&
                        !v.contains("avatar.php") &&
                        !v.contains("filetype/") &&
                        !v.contains("smiley") &&
                        !v.contains("none.gif") &&
                        !v.startsWith("static/image")
                }
            candidate?.let { result.add(absolutize(it.replace("&amp;", "&"))) }
        }

        // Attachment links: href may be data/attachment/... , mod=attachment, mod=image
        root.select("a[href]").forEach { a ->
            val href = a.attr("href").replace("&amp;", "&")
            val label = a.text().trim()

            val isInternalAttachment = href.contains("data/attachment") ||
                href.contains("mod=attachment") ||
                href.contains("mod=image")
            if (!isInternalAttachment) return@forEach

            val hrefIsImage = imageExtRegex.containsMatchIn(href)
            val labelIsImage = imageExtRegex.containsMatchIn(label)
            val hasImageIcon = a.selectFirst("img[src*=filetype/image]") != null ||
                a.parents().any { it.selectFirst("img[src*=filetype/image]") != null }

            if (hrefIsImage || labelIsImage || hasImageIcon) {
                result.add(absolutize(href))
            }
        }

        return result.toList()
    }

    /** Convert message HTML into readable text, keeping line breaks. */
    private fun htmlToText(element: Element): String {
        val sb = StringBuilder()

        fun walk(node: Node) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    val tag = node.tagName().lowercase()
                    when (tag) {
                        "br" -> { sb.append('\n'); return }
                        "img" -> return
                        "p", "div", "li", "tr", "h1", "h2", "h3", "h4", "blockquote" -> {
                            sb.append('\n')
                            node.childNodes().forEach(::walk)
                            sb.append('\n')
                            return
                        }
                        else -> node.childNodes().forEach(::walk)
                    }
                }
                else -> {}
            }
        }

        element.childNodes().forEach(::walk)
        return sb.toString()
            .replace("\u00a0", " ")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "https://mce.msi.cn$src"
        else -> "https://mce.msi.cn/$src"
    }
}
