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
    // v0.3.3 批 3：每帖/每楼都会用到的正则提升为常量
    private val fidRegex = Regex("""action=reply&(?:amp;)?fid=(\d+)""")
    private val uidRegex = Regex("""uid=(\d+)""")
    private val trailingSpaceNewlineRegex = Regex("""[ \t]+\n""")
    private val multiNewlineRegex = Regex("""\n{3,}""")

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

        val fid = fidRegex.find(html)
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
            formhash = Net.formhashOf(html),
            rewardCount = rewardCount,
        )
    }

    /**
     * 轻量首图提取：只解析一次 DOM，取第一张正文图（不做楼层/分块解析）。
     * 供列表卡片缩略图使用 —— 旧实现为了拿一个图片 URL 会跑完整楼层解析，
     * 首屏 8 张卡片就等于 8 次全楼层解析，纯浪费 CPU。
     */
    fun firstImageUrl(html: String, baseUrl: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        val containers = blockSelectors
            .map { doc.select(it) }
            .firstOrNull { it.isNotEmpty() }
            ?: listOf(doc)

        for (container in containers) {
            // 1) 正文容器内的图片（相册/附件图也常在这里）
            val scope = container.selectFirst("div.message") ?: container.selectFirst(".message")
            scope?.select("img")?.forEach { img -> imageUrlOf(img)?.let { return it } }
            // 2) 同楼层其余图片（message 之外的相册/附件容器）
            container.select("img").forEach { img -> imageUrlOf(img)?.let { return it } }
            // 3) 纯链接形式的附件图
            container.select("a[href]").forEach { a ->
                val href = a.attr("href").replace("&amp;", "&")
                if (isImageAttachmentLink(href, a.text())) {
                    return absolutize(href).replace(" ", "%20")
                }
            }
        }
        return null
    }

    private fun parsePost(el: Element, index: Int): Post? {
        val pid = pidRegex.find(el.id())?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

        val authi = el.selectFirst("div.view_authi")
            ?: el.selectFirst(".view_authi")
            ?: el.selectFirst(".plc .pi")

        val authorLink = authi?.selectFirst("span.view_author a, a[href*=space][href*=uid=]")
        val author = authi?.selectFirst("span.view_author a")?.text()?.trim()
            ?: authi?.select("a[href*=space]")?.firstOrNull { it.text().isNotBlank() }?.text()?.trim()
            ?: "\u533f\u540d"

        val avatar = authi?.selectFirst("a.avatars img, img")?.attr("src")
            ?.takeIf { it.isNotBlank() }?.let(::absolutize)

        val authorUid = uidRegex.find(authorLink?.attr("href").orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull()
        val message = el.selectFirst("div.message") ?: el.selectFirst(".message")

        // 关键修复：相册图/附件图不在 div.message 里，而在同级的 .attach_list / .img_item 中，
        // 只扫 message 会整张丢失（历史上"部分帖子图片看不到"的通病）。
        val contentBlocks = message?.let { parseBlocks(it) }.orEmpty()
        val outsideImages = collectImagesOutsideMessage(el, message)
        val blocks = (contentBlocks + outsideImages.map { ContentBlock.Image(it) })
            .distinctBy { block ->
                when (block) {
                    is ContentBlock.Text -> "t:" + block.text
                    is ContentBlock.Image -> "i:" + block.url
                }
            }

        val plainText = blocks.filterIsInstance<ContentBlock.Text>()
            .joinToString("\n\n") { it.text }

        val date = el.selectFirst("span.datelinetimes")?.text()?.trim()
            ?: authi?.selectFirst("span.datelinetimes")?.text()?.trim()

        return Post(
            pid = pid,
            uid = authorUid,
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
                .replace(trailingSpaceNewlineRegex, "\n")
                .replace(multiNewlineRegex, "\n\n")
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
                            imageUrlOf(node, allowSmiley = true)?.let { url ->
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
    private fun imageUrlOf(img: Element, allowSmiley: Boolean = false): String? {
        val raw = sequenceOf("zoomfile", "file", "data-src", "data-original", "src")
            .map { img.attr(it) }
            .firstOrNull { v ->
                val isStaticSmiley = v.startsWith("static/image") && v.contains("/smiley/")
                v.isNotBlank() &&
                    !v.contains("avatar.php") &&
                    !v.contains("filetype/") &&
                    (allowSmiley || !v.contains("smiley")) &&
                    !v.contains("none.gif") &&
                    // 模板静态图（含表情）默认当装饰过滤；正文表情（allowSmiley）保留
                    (!v.startsWith("static/image") || (allowSmiley && isStaticSmiley))
            } ?: return null
        // 表情文件名里带空格（red dragon (12).jpg），URL 需要 %20
        return absolutize(raw.replace("&amp;", "&")).replace(" ", "%20")
    }

    private fun isImageAttachmentLink(href: String, label: String): Boolean {
        val internal = href.contains("data/attachment") ||
            href.contains("mod=attachment") ||
            href.contains("mod=image")
        if (!internal) return false
        return imageExtRegex.containsMatchIn(href) || imageExtRegex.containsMatchIn(label)
    }

    /**
     * 收集帖子容器里、message 之外的图片（相册/附件/图床等）。
     * 头像、表情、占位图、模板图标会被 imageUrlOf 的规则过滤掉。
     */
    private fun collectImagesOutsideMessage(container: Element, message: Element?): List<String> {
        val result = LinkedHashSet<String>()

        container.select("img").forEach { img ->
            // message 内的图片已经按块解析过，这里跳过避免重复
            val insideMessage = message != null && img.parents().any { it === message } ||
                (message != null && img.parents().contains(message))
            if (insideMessage) return@forEach
            imageUrlOf(img)?.let { result.add(it) }
        }

        container.select("a[href]").forEach { a ->
            val insideMessage = message != null && (a.parents().contains(message) || a.parents().any { it === message })
            if (insideMessage) return@forEach
            val href = a.attr("href").replace("&amp;", "&")
            if (isImageAttachmentLink(href, a.text()) || imageExtRegex.containsMatchIn(href)) {
                // 只收站内图，避免把普通外链当成配图
                if (href.contains("mod=image") || href.contains("mod=attachment") ||
                    href.contains("data/attachment") || href.contains("from=album")
                ) {
                    result.add(absolutize(href))
                }
            }
        }
        return result.toList()
    }


    private fun absolutize(src: String): String = Net.absolutize(src)
}
