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
    private val pageNumRegex = Regex("""[?&]page=(\d+)""")
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

        // v0.3.7：移动模板里 <h1> 是版块名（如"综合交流"），真题面在 div.thname/#myshares 的链接里
        val title = doc.selectFirst("div.thname a")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("#myshares a")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: doc.title().substringBefore(" - ").trim().takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: ""

        val blocks = blockSelectors
            .map { doc.select(it) }
            .firstOrNull { it.isNotEmpty() }
            ?: emptyList()

        val posts = blocks.mapNotNull { block -> parsePost(block, blocks.indexOf(block), page) }

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
            // v0.3.8：手机模板没有「下一页」链接也没有 page=N 分页（是 ajax「加载更多」），
            // 旧判定恒为 false → 多页帖被当成最后一页，回帖后看不到新楼。
            // 这里用「本页是否满 10 楼」推断还有下一页（手机模板每页 10 楼），
            // 并保留原有页码链接/文字判定作为兜底。
            hasNextPage = posts.size >= 10 ||
                doc.select("a[href*=page=]").any { a ->
                    val p = pageNumRegex.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    p > page
                } ||
                doc.select("a").any { it.text().contains("\u4e0b\u4e00\u9875") },
            fid = fid,
            formhash = Net.formhashOf(html),
            rewardCount = rewardCount,
            poll = parsePoll(doc),
        )
    }

    // v0.3.3：帖子内投票解析
    private val pollVoteCountRegex = Regex("""(\d+)\s*票""")
    private val pollOptionIndexRegex = Regex("""^\d+[.、]\s*""")
    private val pollMaxObjRegex = Regex("""max_obj\s*=\s*(\d+)""")
    private val pollPercentRegex = Regex("""width\s*:\s*([\d.]+)%""")
    private val pollVotersRegex = Regex("""(\d+)\s*投票人数""")
    private val pollTotalVotesRegex = Regex("""(\d+)\s*累计投票数""")

    /**
     * Discuz touch 模板投票：
     *  - `form#poll` 的 action/formhash + `input[name=pollanswers[]]`（radio/checkbox，value=选项 id）
     *  - 选项文本在 `.poll_text > .poll_op_name`："8票 1. 基于安卓的系统"
     *  - 结果条比例在 `div.poll_bar > div.poll_ct` 的 `style="width:42%"`
     *  - 参与人数/累计票数在 `div.ss_data`："64 浏览人数 5 投票人数 5 累计投票数"
     *  - 多选上限来自页面脚本 `var max_obj = N`（Discuz 用 0 表示不限）
     *  - 已投票时没有 input、选项带 `.poll_done`，并有 `div.poll_note4`「您已经投过票」
     */
    private fun parsePoll(doc: Document): PollData? {
        val form = doc.selectFirst("form#poll") ?: doc.selectFirst("form[name=poll]")
        val list = doc.selectFirst("div.poll_list")
        if (form == null && list == null) return null

        val scope: Element = form ?: doc
        val options = scope.select("div.poll_text").mapNotNull { el ->
            val nameEl = el.selectFirst(".poll_op_name") ?: return@mapNotNull null
            val raw = nameEl.text().trim()
            if (raw.isBlank()) return@mapNotNull null
            val input = el.selectFirst("input[name^=pollanswers]")
            val id = input?.attr("value")?.trim().orEmpty()
            val votes = pollVoteCountRegex.find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val label = pollOptionIndexRegex.replace(raw.replace(pollVoteCountRegex, "").trim(), "").trim()
            if (label.isBlank()) return@mapNotNull null
            val percent = el.selectFirst("div.poll_bar div.poll_ct, div.poll_ct")
                ?.attr("style")
                ?.let { pollPercentRegex.find(it)?.groupValues?.get(1)?.toFloatOrNull() }
            PollOption(
                id = id.ifBlank { "voted_${el.siblingIndex()}" },
                label = label,
                votes = votes,
                percent = percent,
                chosen = input?.hasAttr("checked") == true,
            )
        }.distinctBy { it.id.ifBlank { it.label } }
        if (options.isEmpty()) return null

        val firstInput = form?.selectFirst("input[name^=pollanswers]")
        val multi = firstInput?.attr("type").equals("checkbox", ignoreCase = true)
        val maxObj = doc.select("script").joinToString("\n") { it.data() }
            .let { pollMaxObjRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 1
        val note = doc.selectFirst("div.poll_note4")?.text().orEmpty()
        val closed = note.contains("已结束") || note.contains("已关闭") || note.contains("投票关闭")
        val voted = !closed && (form == null || list == null ||
            firstInput == null ||
            doc.selectFirst(".poll_done") != null ||
            doc.selectFirst("div.poll_note4") != null)

        // 参与人数 / 累计票数（散落在首楼正文的 ss_data 里）
        val ssData = doc.selectFirst("div.ss_data")?.text().orEmpty()
        val totalVoters = pollVotersRegex.find(ssData)?.groupValues?.get(1)?.toIntOrNull()
        val totalVotes = pollTotalVotesRegex.find(ssData)?.groupValues?.get(1)?.toIntOrNull()

        return PollData(
            action = form?.attr("action")?.replace("&amp;", "&").orEmpty(),
            formhash = form?.selectFirst("input[name=formhash]")?.attr("value").orEmpty(),
            multi = multi,
            // Discuz 用 max_obj=0 表示「不限项数」，旧实现压成 1 导致多选只能选一项
            maxChoices = when {
                !multi -> 1
                maxObj <= 0 -> Int.MAX_VALUE
                else -> maxObj
            },
            options = options,
            voted = voted,
            viewVoteUrl = doc.selectFirst("a[href*=action=viewvote]")
                ?.attr("href")?.replace("&amp;", "&")?.let(Net::absolutize),
            closed = closed,
            totalVoters = totalVoters,
            totalVotes = totalVotes,
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

    private fun parsePost(el: Element, index: Int, page: Int = 1): Post? {
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
                    // v0.3.8：去重 key 带上样式/段落类型，避免「同文本不同颜色」被吞掉一段
                    is ContentBlock.Text -> "t:" + block.text + "|" + block.spans.hashCode() + "|" + block.kind
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
            // v0.3.9：统一显示数字楼层（1楼/2楼…），1 楼不再显示成「楼主」
            floorLabel = "${(page - 1) * 10 + index + 1}\u697c",
            dateText = date,
            isOp = index == 0,
        )
    }

    // ---------------- v0.3.8：正文样式（彩色文字/加粗/引用块） ----------------

    /** 当前累积的文字样式（HTML 标签栈合并结果） */
    private data class RunStyle(
        val color: Int? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val sizeEm: Float? = null,
    ) {
        fun mergeFrom(el: Element): RunStyle {
            var s = this
            val tag = el.tagName().lowercase()
            when (tag) {
                "b", "strong" -> s = s.copy(bold = true)
                "i", "em" -> s = s.copy(italic = true)
                "u", "ins" -> s = s.copy(underline = true)
                "s", "strike", "del" -> s = s.copy(strike = true)
                "font" -> {
                    CssColor.parse(el.attr("color"))?.let { s = s.copy(color = it) }
                    el.attr("size").trim().toIntOrNull()?.let { size ->
                        if (size in 1..7) s = s.copy(sizeEm = FONT_SIZE_EM[size - 1])
                    }
                }
            }
            // <span style="color:#f00;font-weight:bold"> 之类（列表页模板常见，正文也可能出现）
            val styleAttr = el.attr("style")
            if (styleAttr.isNotBlank()) {
                STYLE_COLOR_REGEX.find(styleAttr)?.let { m ->
                    CssColor.parse(m.groupValues[1])?.let { s = s.copy(color = it) }
                }
                if (styleAttr.contains("font-weight", true) &&
                    (styleAttr.contains("bold", true) || Regex("""font-weight\s*:\s*[6-9]00""").containsMatchIn(styleAttr))
                ) {
                    s = s.copy(bold = true)
                }
                if (styleAttr.contains("font-style", true) && styleAttr.contains("italic", true)) {
                    s = s.copy(italic = true)
                }
                if (styleAttr.contains("line-through", true)) s = s.copy(strike = true)
                if (styleAttr.contains("underline", true)) s = s.copy(underline = true)
            }
            return s
        }
    }

    /** 一段同一样式的原始区间（偏移基于规范化前的 buffer） */
    private data class Run(val start: Int, val end: Int, val style: RunStyle)

    /** CSS 颜色解析：支持 #rgb/#rrggbb、rgb()、常见命名色；未知/inherit/transparent 返回 null（沿用主题色） */
    private object CssColor {
        private val named: Map<String, Int> = mapOf(
            "red" to 0xFFFF0000.toInt(),
            "green" to 0xFF008000.toInt(),
            "blue" to 0xFF0000FF.toInt(),
            "black" to 0xFF000000.toInt(),
            "white" to 0xFFFFFFFF.toInt(),
            "gray" to 0xFF808080.toInt(),
            "grey" to 0xFF808080.toInt(),
            "silver" to 0xFFC0C0C0.toInt(),
            "fuchsia" to 0xFFFF00FF.toInt(),
            "magenta" to 0xFFFF00FF.toInt(),
            "purple" to 0xFF800080.toInt(),
            "yellow" to 0xFFFFFF00.toInt(),
            "orange" to 0xFFFFA500.toInt(),
            "gold" to 0xFFFFD700.toInt(),
            "pink" to 0xFFFFC0CB.toInt(),
            "cyan" to 0xFF00FFFF.toInt(),
            "aqua" to 0xFF00FFFF.toInt(),
            "lime" to 0xFF00FF00.toInt(),
            "navy" to 0xFF000080.toInt(),
            "teal" to 0xFF008080.toInt(),
            "olive" to 0xFF808000.toInt(),
            "maroon" to 0xFF800000.toInt(),
            "brown" to 0xFFA52A2A.toInt(),
            "darkred" to 0xFF8B0000.toInt(),
            "darkgreen" to 0xFF006400.toInt(),
            "darkblue" to 0xFF00008B.toInt(),
            "royalblue" to 0xFF4169E1.toInt(),
            "sienna" to 0xFFA0522D.toInt(),
            "indigo" to 0xFF4B0082.toInt(),
            "crimson" to 0xFFDC143C.toInt(),
            "tomato" to 0xFFFF6347.toInt(),
            "orchid" to 0xFFDA70D6.toInt(),
        )

        fun parse(raw: String?): Int? {
            val v = raw?.trim()?.lowercase().orEmpty()
            if (v.isBlank() || v == "inherit" || v == "transparent" || v == "currentcolor") return null
            if (v.startsWith("#")) {
                val hex = v.substring(1)
                return when (hex.length) {
                    3 -> {
                        val r = hex[0].digitToIntOrNull(16) ?: return null
                        val g = hex[1].digitToIntOrNull(16) ?: return null
                        val b = hex[2].digitToIntOrNull(16) ?: return null
                        (0xFF000000.toInt()) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
                    }
                    6 -> hex.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
                    else -> null
                }
            }
            if (v.startsWith("rgb")) {
                val nums = Regex("""(\d{1,3})""").findAll(v).map { it.groupValues[1].toIntOrNull() ?: 0 }.toList()
                if (nums.size >= 3) {
                    val (r, g, b) = nums.take(3).map { it.coerceIn(0, 255) }
                    return (0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
                return null
            }
            return named[v]
        }
    }

    private val STYLE_COLOR_REGEX = Regex("""color\s*:\s*([#\w(),. ]+)""")
    private val FONT_SIZE_EM = floatArrayOf(0.7f, 0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f)

    /** Split message HTML into ordered text/image blocks. */
    private fun parseBlocks(element: Element): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val sb = StringBuilder()
        val runs = mutableListOf<Run>()
        var currentKind = TextKind.Paragraph

        fun flush() {
            val raw = sb.toString()
            val pending = runs.toList()
            runs.clear()
            sb.setLength(0)
            if (raw.isBlank()) return
            runCatching {
                // 1) 规范化（与旧实现等价：[ \t]+\n → \n、\n{3,} → \n\n、trim）同时生成删除掩码
                val keep = BooleanArray(raw.length) { true }
                trailingSpaceNewlineRegex.findAll(raw).forEach { m ->
                    for (i in m.range) keep[i] = false
                    keep[m.range.last] = true // 保留换行符
                }
                multiNewlineRegex.findAll(raw).forEach { m ->
                    var seen = 0
                    for (i in m.range) {
                        if (raw[i] == '\n') {
                            seen++
                            if (seen > 2) keep[i] = false
                        }
                    }
                }
                // 2) 生成 clean 文本 + raw→clean 偏移映射
                val clean = StringBuilder(raw.length)
                val map = IntArray(raw.length + 1) { 0 }
                for (i in raw.indices) {
                    map[i] = clean.length
                    if (keep[i]) clean.append(if (raw[i] == '\u00a0') ' ' else raw[i])
                }
                map[raw.length] = clean.length
                var start = 0
                var end = clean.length
                while (start < end && clean[start].isWhitespace()) start++
                while (end > start && clean[end - 1].isWhitespace()) end--
                val text = clean.substring(start, end)
                if (text.isEmpty()) return@runCatching
                // 3) 重新映射样式区间，丢弃空区间并合并相邻同样式
                val spans = mutableListOf<TextSpan>()
                for (r in pending) {
                    val st = r.style
                    // 没有任何样式信息的 run 不必生成 span
                    if (st.color == null && !st.bold && !st.italic && !st.underline &&
                        !st.strike && st.sizeEm == null
                    ) {
                        continue
                    }
                    val s = (map[r.start.coerceIn(0, raw.length)] - start).coerceIn(0, text.length)
                    val e = (map[r.end.coerceIn(0, raw.length)] - start).coerceIn(0, text.length)
                    if (e <= s) continue
                    spans.add(
                        TextSpan(
                            start = s,
                            end = e,
                            color = st.color,
                            bold = st.bold,
                            italic = st.italic,
                            underline = st.underline,
                            strike = st.strike,
                            sizeEm = st.sizeEm,
                        ),
                    )
                }
                blocks.add(
                    ContentBlock.Text(
                        text = text,
                        spans = spans,
                        kind = currentKind,
                    ),
                )
            }.onFailure {
                // 兜底：任何解析异常都退回纯文本，绝不整页失败
                val text = raw.replace(trailingSpaceNewlineRegex, "\n")
                    .replace(multiNewlineRegex, "\n\n")
                    .trim()
                if (text.isNotEmpty()) blocks.add(ContentBlock.Text(text))
            }
        }

        fun walk(node: Node, style: RunStyle) {
            when (node) {
                is TextNode -> {
                    val s = sb.length
                    sb.append(node.text())
                    val e = sb.length
                    if (e > s) runs.add(Run(s, e, style))
                }
                is Element -> {
                    // 投票块由 PollCard 单独渲染，不要混进正文文字里
                    // v0.3.7：移动模板里投票首楼正文还夹着 ss_data（浏览数/投票人数/累计票数）与
                    // ss_line（"投票项目"）等结构文字，旧实现只过滤 poll_* 导致正文出现一堆残句。
                    if (node.id() == "poll" ||
                        node.classNames().any {
                            it.startsWith("poll_") || it == "ss_data" || it == "ss_line" ||
                                it == "polls" || it == "poll_list"
                        }
                    ) {
                        return
                    }
                    val next = style.mergeFrom(node)
                    // v0.3.8：引用块单独成段（灰色左边条渲染），不混进正文
                    val isQuote = node.tagName().lowercase() == "blockquote" ||
                        node.classNames().any { it == "quote" || it == "blockquote" }
                    if (isQuote) {
                        flush()
                        val prev = currentKind
                        currentKind = TextKind.Quote
                        node.childNodes().forEach { walk(it, next) }
                        flush()
                        currentKind = prev
                        return
                    }
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
                                node.childNodes().forEach { walk(it, next) }
                            }
                        }
                        "p", "div", "li", "tr", "h1", "h2", "h3", "h4" -> {
                            sb.append('\n')
                            node.childNodes().forEach { walk(it, next) }
                            sb.append('\n')
                        }
                        else -> node.childNodes().forEach { walk(it, next) }
                    }
                }
                else -> {}
            }
        }

        element.childNodes().forEach { walk(it, RunStyle()) }
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
