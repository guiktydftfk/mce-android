package cn.mce.starlight.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * v0.4.0：编辑框「所见即所得」——
 *
 * 文本本身仍是 BBCode（不改存储、不改发布内容），但编辑时**把 BBCode 标签整段隐藏**，
 * 只显示文字效果：`[b]粗[/b]` 在框里就是「粗」（加粗），颜色块直接着色，链接带下划线。
 *
 * 因为隐藏标签会改变可见文本长度，这里实现了原文↔显示文本的 [OffsetMapping]：
 * 光标/选区/输入法仍然操作原文偏移，删除/输入行为与普通文本框一致。
 */
class BbcodeVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!raw.contains('[')) return TransformedText(text, OffsetMapping.Identity)
        val tags = tagRegex.findAll(raw).map { it.range.first to it.range.last + 1 }.toList()
        if (tags.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val display = buildDisplay(raw, tags)
        return TransformedText(display, TagOffsetMapping(tags, raw.length, display.length))
    }

    /** 去掉标签后的显示文本 + 内文样式 */
    private fun buildDisplay(raw: String, tags: List<Pair<Int, Int>>): AnnotatedString {
        var bold = 0
        var italic = 0
        var underline = 0
        var color: Color? = null
        var url = false

        fun currentStyle(): SpanStyle? {
            if (bold == 0 && italic == 0 && underline == 0 && color == null && !url) return null
            return SpanStyle(
                fontWeight = if (bold > 0) FontWeight.Bold else null,
                fontStyle = if (italic > 0) FontStyle.Italic else null,
                textDecoration = if (underline > 0 || url) TextDecoration.Underline else null,
                color = color ?: if (url) Color(0xFF4A90D9) else Color.Unspecified,
            )
        }

        return buildAnnotatedString {
            var position = 0
            tags.forEach { (tagStart, tagEnd) ->
                if (tagStart > position) {
                    val chunk = raw.substring(position, tagStart)
                    val style = currentStyle()
                    if (style == null) append(chunk) else withStyle(style) { append(chunk) }
                }
                val tagText = raw.substring(tagStart, tagEnd)
                val closing = tagText.startsWith("[/")
                val match = tagRegex.matchEntire(tagText)
                if (match != null) {
                    val tag = match.groupValues[2].lowercase()
                    val arg = match.groupValues[3].trim().removePrefix("=").trim()
                    when (tag) {
                        "b" -> bold = if (closing) (bold - 1).coerceAtLeast(0) else bold + 1
                        "i" -> italic = if (closing) (italic - 1).coerceAtLeast(0) else italic + 1
                        "u" -> underline = if (closing) (underline - 1).coerceAtLeast(0) else underline + 1
                        "color" -> color = if (closing) null else parseColor(arg)
                        "url" -> url = !closing
                        else -> Unit
                    }
                }
                position = tagEnd
            }
            if (position < raw.length) {
                val chunk = raw.substring(position)
                val style = currentStyle()
                if (style == null) append(chunk) else withStyle(style) { append(chunk) }
            }
        }
    }

    private fun parseColor(raw: String): Color? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return runCatching { Color(android.graphics.Color.parseColor(v)) }.getOrNull()
    }

    /**
     * 原文（含标签）↔ 显示文本（去标签）的偏移映射。
     *
     * 规则（与系统文本框常用做法一致）：
     *  - 原文中落在标签内部的偏移 → 映射到标签起点之前的显示位置（光标不会停在隐藏区域里）；
     *  - 显示文本中恰好处于「被删标签」边界时 → 映射到标签之后的原文位置（点击/输入都落在正确文字处）。
     */
    private class TagOffsetMapping(
        private val tags: List<Pair<Int, Int>>,
        private val originalLength: Int,
        private val displayLength: Int,
    ) : OffsetMapping {

        override fun originalToTransformed(offset: Int): Int {
            val target = offset.coerceIn(0, originalLength)
            var removed = 0
            for ((tagStart, tagEnd) in tags) {
                when {
                    target >= tagEnd -> removed += tagEnd - tagStart
                    target > tagStart -> return (tagStart - removed).coerceIn(0, displayLength)
                    else -> break
                }
            }
            return (target - removed).coerceIn(0, displayLength)
        }

        override fun transformedToOriginal(offset: Int): Int {
            val target = offset.coerceIn(0, displayLength)
            var removed = 0
            for ((tagStart, tagEnd) in tags) {
                val visibleTagStart = tagStart - removed
                when {
                    target > visibleTagStart -> removed += tagEnd - tagStart
                    // 恰好落在被隐藏标签的位置：光标放到标签之后，避免"卡在隐藏区域"
                    target == visibleTagStart -> return tagEnd.coerceIn(0, originalLength)
                    else -> break
                }
            }
            return (target + removed).coerceIn(0, originalLength)
        }
    }

    private companion object {
        val tagRegex = Regex("""\[(/?)([a-zA-Z]+)([^\]]*)]""")
    }
}
