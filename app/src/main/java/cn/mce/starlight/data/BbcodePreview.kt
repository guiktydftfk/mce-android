package cn.mce.starlight.data

/**
 * 发帖预览用的 BBCode 轻量渲染模型（v0.4.0）。
 *
 * 纯逻辑、不依赖 Compose/Android，方便 JVM 单测；UI 层再把 [BbSegment] 转成 AnnotatedString。
 *
 * 支持（与发帖编辑器工具栏一致）：
 *  - 行内样式：`[b] [i] [u] [color=#RRGGBB] [url]…[/url] [url=…]…[/url]`
 *  - 行内/整行图片：`[img]url[/img]`、`[attach]aid[/attach]`
 *  - 其余标签（quote/code/hide/size/align 等）只去掉标签、保留文字，避免预览里出现裸 BBCode
 */
object BbcodePreview {

    sealed interface Block {
        data class Text(val segments: List<BbSegment>, val align: String? = null) : Block

        /** [attachAid] 非空表示是本地刚上传的图片（用本地 Uri 预览）；否则 [src] 是图片地址 */
        data class Image(val src: String, val attachAid: String? = null) : Block

        /** 视频：预览页展示为"视频卡片"（真正播放由论坛网页播放器负责） */
        data class Video(val src: String) : Block
    }

    data class BbSegment(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        /** #RRGGBB；null = 默认正文色 */
        val color: String? = null,
        /** 链接地址；null = 非链接 */
        val url: String? = null,
    )

    private val tagRegex = Regex("""\[(/?)([a-zA-Z]+)([^\]]*)\]""")
    private val attachLineRegex = Regex("""^\s*\[attach](\d+)\[/attach]\s*$""")
    private val mediaAnyRegex = Regex("""\[media[^\]]*](.+?)\[/media]""", RegexOption.IGNORE_CASE)
    private val alignOpenRegex = Regex("""\[(align=(left|center|right)|left|center|right)]""", RegexOption.IGNORE_CASE)
    private val alignCloseRegex = Regex("""\[/(align|left|center|right)]""", RegexOption.IGNORE_CASE)
    private val imgLineRegex = Regex("""^\s*\[img](.+?)\[/img]\s*$""", RegexOption.IGNORE_CASE)
    private val imgAnyRegex = Regex("""\[img](.+?)\[/img]""", RegexOption.IGNORE_CASE)
    private val attachAnyRegex = Regex("""\[attach]\d+\[/attach]""", RegexOption.IGNORE_CASE)

    fun render(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        var currentAlign: String? = null
        text.split('\n').forEach { rawLine ->
            var line = rawLine.trimEnd()
            // 对齐标签可能单独成行，也可能和正文同一行（单行 [align=center]x[/align] 也要生效）
            val openMatch = alignOpenRegex.find(line)
            val lineAlign = if (openMatch != null) {
                val g = openMatch.groupValues[1]
                (if (g.contains('=')) g.substringAfter('=') else g).lowercase()
            } else {
                currentAlign
            }
            line = line.replace(alignOpenRegex, "")
            val closesAlign = alignCloseRegex.containsMatchIn(line)
            if (closesAlign) line = line.replace(alignCloseRegex, "")
            if (openMatch != null) currentAlign = lineAlign

            // v0.4.0：视频/图片标签可能被 [color]/[align] 等包裹在同一行，
            // 所以先把它们整段抽成独立块，剩余文字再按行内样式渲染。
            mediaAnyRegex.findAll(line).forEach { m ->
                val src = m.groupValues[1].trim()
                if (src.isNotBlank()) blocks.add(Block.Video(src))
            }
            var work = line.replace(mediaAnyRegex, "")
            attachAnyRegex.findAll(work).forEach { m ->
                val aid = Regex("""\d+""").find(m.value)?.value
                if (!aid.isNullOrBlank()) blocks.add(Block.Image("", aid))
            }
            work = work.replace(attachAnyRegex, "")
            imgAnyRegex.findAll(work).forEach { m ->
                val src = m.groupValues[1].trim()
                if (src.isNotBlank()) blocks.add(Block.Image(src))
            }
            work = work.replace(imgAnyRegex, "")
            val cleaned = work.trim()
            if (cleaned.isNotEmpty()) {
                val segments = renderInline(cleaned)
                if (segments.isNotEmpty()) blocks.add(Block.Text(segments, lineAlign))
            }
            if (closesAlign) currentAlign = null
        }
        return blocks
    }
    /** 行内 BBCode → 带样式的文字片段；未知标签只丢弃标签本身、保留内部文字 */
    fun renderInline(line: String): List<BbSegment> {
        if (line.isEmpty()) return emptyList()
        val out = mutableListOf<BbSegment>()
        var bold = 0
        var italic = 0
        var underline = 0
        var color: String? = null
        // [url=xxx] 用显式地址；[url]xxx[/url] 无参数时用内部文字当地址（网页编辑器常见写法）
        var urlOpen = false
        var urlArg: String? = null
        var sb = StringBuilder()

        fun flush() {
            if (sb.isEmpty()) {
                if (urlOpen && urlArg == null) urlArg = null
                return
            }
            val text = sb.toString()
            out.add(
                BbSegment(
                    text = text,
                    bold = bold > 0,
                    italic = italic > 0,
                    underline = underline > 0,
                    color = color,
                    url = if (urlOpen) (urlArg ?: text) else null,
                ),
            )
            sb = StringBuilder()
        }

        var cursor = 0
        tagRegex.findAll(line).forEach { m ->
            val before = line.substring(cursor, m.range.first)
            if (before.isNotEmpty()) sb.append(before)
            cursor = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2].lowercase()
            val arg = m.groupValues[3].trim().removePrefix("=").trim()
            when (tag) {
                "b" -> {
                    flush()
                    bold = if (closing) (bold - 1).coerceAtLeast(0) else bold + 1
                }
                "i" -> {
                    flush()
                    italic = if (closing) (italic - 1).coerceAtLeast(0) else italic + 1
                }
                "u" -> {
                    flush()
                    underline = if (closing) (underline - 1).coerceAtLeast(0) else underline + 1
                }
                "color" -> {
                    flush()
                    color = if (closing) null else normalizeColor(arg)
                }
                "url" -> {
                    flush()
                    if (closing) {
                        urlOpen = false
                        urlArg = null
                    } else {
                        urlOpen = true
                        urlArg = arg.ifBlank { null }
                    }
                }
                // 其余标签：去掉标签本身，保留文字（quote/code/hide/size/align/font/img/attach 等）
                else -> Unit
            }
        }
        if (cursor < line.length) sb.append(line.substring(cursor))
        flush()
        return out
    }

    private fun normalizeColor(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return if (v.startsWith("#") && (v.length == 4 || v.length == 7)) v else null
    }
}
