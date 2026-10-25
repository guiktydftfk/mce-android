package cn.mce.starlight.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 帖子详情解析器（Discuz 触屏模板）。
 *
 * 实测要点：
 *  - 楼层：div.plc[id=pid<数字>]
 *  - 作者：span.view_author > a ；时间：span.datelinetimes
 *  - 图片：正文里的附件是**链接**（forum.php?mod=attachment&aid=...）
 *          以及懒加载 <img file=/zoomfile=/data-src=...>
 *  - 回复表单：forum.php?mod=post&action=reply&fid=53&tid=...
 *  - 打赏：div.view_reward（打赏人数）
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
            val index = blocks.indexOf(block)
            parsePost(block, index, doc)
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
            hasNextPage = doc.select("a").any { it.text().contains("下一页") },
            fid = fid,
            formhash = Regex("""name="formhash"\s+value="([a-z0-9]+)"""")
                .find(html)?.groupValues?.get(1),
            rewardCount = rewardCount,
            rewardUrl = firstPid?.let {
                "$baseUrl".substringBefore("forum.php") +
                    "forum.php?mod=misc&action=viewratings&tid=$tid&pid=$it"
            },
        )
    }

    private fun parsePost(el: Element, index: Int, doc: Document): Post? {
        val pid = pidRegex.find(el.id())?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

        val authi = el.selectFirst("div.view_authi")
            ?: el.selectFirst(".view_authi")
            ?: el.selectFirst(".plc .pi")

        val author = authi?.selectFirst("span.view_author a")?.text()?.trim()
            ?: authi?.select("a[href*=space]")?.firstOrNull { it.text().isNotBlank() }?.text()?.trim()
            ?: "匿名"

        val avatar = authi?.selectFirst("a.avatars img, img")?.attr("src")
            ?.takeIf { it.isNotBlank() }?.let(::absolutize)

        val message = el.selectFirst("div.message") ?: el.selectFirst(".message")

        val contentHtml = message?.html().orEmpty()
        val contentText = message?.text()?.trim().orEmpty()

        val date = el.selectFirst("span.datelinetimes")?.text()?.trim()
            ?: authi?.selectFirst("span.datelinetimes")?.text()?.trim()

        return Post(
            pid = pid,
            author = author,
            avatarUrl = avatar,
            contentHtml = contentHtml,
            contentText = contentText,
            images = message?.let { collectImages(it) }.orEmpty(),
            floorLabel = if (index == 0) "楼主" else "${index + 1}楼",
            dateText = date,
            isOp = index == 0,
        )
    }

    /** 收集正文图片：懒加载属性 + 附件链接 */
    fun collectImages(root: Element): List<String> {
        val result = LinkedHashSet<String>()

        root.select("img").forEach { img ->
            val candidate = sequenceOf("file", "zoomfile", "data-src", "data-original", "src")
                .map { it to img.attr(it) }
                .firstOrNull { (_, v) ->
                    v.isNotBlank() &&
                        !v.contains("avatar.php") &&
                        !v.contains("filetype/") &&
                        !v.contains("smiley") &&
                        !v.contains("common/none") &&
                        !v.startsWith("static/image")
                }
            candidate?.let { (_, v) -> result.add(absolutize(decode(v))) }
        }

        root.select("a[href*=mod=attachment]").forEach { a ->
            val href = a.attr("href")
            val text = a.text()
            val looksImage = imageExtRegex.containsMatchIn(text) || imageExtRegex.containsMatchIn(href)
            // 附件常以「图标 + 链接」呈现；图标为 image.gif 即为图片附件
            val hasImageIcon = a.parents().any { parent ->
                parent.selectFirst("img[src*=filetype/image]") != null
            } || a.selectFirst("img[src*=filetype/image]") != null
            if (looksImage || hasImageIcon) {
                result.add(absolutize(decode(href)))
            }
        }

        return result.toList()
    }

    private fun decode(s: String): String = s.replace("&amp;", "&")

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "https://mce.msi.cn$src"
        else -> "https://mce.msi.cn/$src"
    }
}
