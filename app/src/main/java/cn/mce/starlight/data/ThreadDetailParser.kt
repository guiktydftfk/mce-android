package cn.mce.starlight.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Thread detail parser (Discuz touch template).
 *
 * Content is split into ordered blocks (text / image) so that images appear
 * in their original position instead of being collected at the end.
 *
 * Image rules (from a working HarmonyOS client):
 *  - lazy images carry the real url in data-src / file / zoomfile
 *  - attachments may only be links: data/attachment/..., mod=attachment, mod=image
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

        val posts = blocks.mapNotNull { block -> parsePost(block, blocks.indexOf(block)) }

        val fid = Regex("""action=reply&(?:amp;)?fid=(\d+)""").find(html)
            ?.groupValues?.get(1)?.toIntOrNull()

        val rewardCount = doc.selectFirst("div.view_reward .reward_num span")
            ?.text()?.trim()?.toIntOrNull()

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
        val blocks = message?.let { parseBlocks(it) }.orEmpty()

        val plainText = blocks.filterIsInstance<ContentBlock.Text>()
            .joinToString("\n\n") { it.text }

        val date = el.selectFirst("span.datelinetimes")?.text()?.trim()
            ?: authi?.selectFirst("span.datelinetimes")?.text()?.trim()

        return Post(
            pid = pid,
            author = author,
            avatarUrl = avatar,
            contentHtml = message?.html().orEmpty(),
            contentText = plainText,
            images = blocks.filterIsInstance<ContentBlock.Image>().map { it.url },
            blocks = blocks,
            floorLabel = if (index == 0) "\u697c\u4e3b" else "${index + 1}\u697c",
            dateText = date,
            isOp = index == 0,
        )
    }

    /** Split message HTML into ordered text/image blocks. */
    private fun parseBlocks(element: Element): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val sb = StringBuilder()

        fun flush() {
            val text = sb.toString()
                .replace("\u00a0", " ")
                .replace(Regex("""[ \t]+\n"""), "\n")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
            if (text.isNotEmpty()) blocks.add(ContentBlock.Text(text))
            sb.setLength(0)
        }

        fun walk(node: Node) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "br" -> sb.append('\n')
                        "img" -> {
                            imageUrlOf(node)?.let { url ->
                                flush()
                                blocks.add(ContentBlock.Image(url))
                            }
                        }
                        "a" -> {
                            val href = node.attr("href").replace("&amp;", "&")
                            if (isImageAttachmentLink(href, node.text())) {
                                flush()
                                blocks.add(ContentBlock.Image(absolutize(href)))
                            } else {
                                node.childNodes().forEach(::walk)
                            }
                        }
                        "p", "div", "li", "tr", "h1", "h2", "h3", "h4", "blockquote" -> {
                            sb.append('\n')
                            node.childNodes().forEach(::walk)
                            sb.append('\n')
                        }
                        else -> node.childNodes().forEach(::walk)
                    }
                }
                else -> {}
            }
        }

        element.childNodes().forEach(::walk)
        flush()
        return blocks
    }

    /** Real url of an <img>, preferring lazy-loading attributes. */
    private fun imageUrlOf(img: Element): String? {
        val raw = sequenceOf("zoomfile", "file", "data-src", "data-original", "src")
            .map { img.attr(it) }
            .firstOrNull { v ->
                v.isNotBlank() &&
                    !v.contains("avatar.php") &&
                    !v.contains("filetype/") &&
                    !v.contains("smiley") &&
                    !v.contains("none.gif") &&
                    !v.startsWith("static/image")
            } ?: return null
        return absolutize(raw.replace("&amp;", "&"))
    }

    private fun isImageAttachmentLink(href: String, label: String): Boolean {
        val internal = href.contains("data/attachment") ||
            href.contains("mod=attachment") ||
            href.contains("mod=image")
        if (!internal) return false
        return imageExtRegex.containsMatchIn(href) || imageExtRegex.containsMatchIn(label)
    }

    /** Flat image list (used by list thumbnails). */
    fun collectImages(root: Element): List<String> {
        val result = LinkedHashSet<String>()
        root.select("img").forEach { img -> imageUrlOf(img)?.let { result.add(it) } }
        root.select("a[href]").forEach { a ->
            val href = a.attr("href").replace("&amp;", "&")
            if (isImageAttachmentLink(href, a.text())) result.add(absolutize(href))
        }
        return result.toList()
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "https://mce.msi.cn$src"
        else -> "https://mce.msi.cn/$src"
    }
}
