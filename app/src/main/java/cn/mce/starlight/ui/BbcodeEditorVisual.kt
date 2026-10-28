package cn.mce.starlight.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * v0.4.1：编辑框「所见即所得」——
 *
 * 文本本身仍是 BBCode（不改存储、不改发布内容），但编辑时**把 BBCode 标签整段隐藏**，
 * 只显示文字效果：加粗/斜体/下划线/颜色/链接，以及 **左对齐/居中/右对齐（实时显示）**。
 *
 * 隐藏标签会改变可见文本长度，这里实现了原文↔显示文本的 [OffsetMapping]，
 * 光标/选区/输入法仍然操作原文偏移。
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

    /** 去掉标签后的显示文本 + 内文样式 + 段落对齐 */
    private fun buildDisplay(raw: String, tags: List<Pair<Int, Int>>): AnnotatedString {
        var bold = 0
        var italic = 0
        var underline = 0
        var color: Color? = null
        var url = false
        var align: TextAlign? = null

        fun spanStyle(): SpanStyle? {
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
                    appendChunk(chunk, spanStyle(), align)
                }
                val tagText = raw.substring(tagStart, tagEnd)
                val closing = tagText.startsWith("[/")
                val match = tagRegex.matchEntire(tagText)
                if (match != null) {
                    val tag = match.groupValues[2].lowercase()
                    val arg = match.groupValues[3].trim().removePrefix("=").trim().lowercase()
                    when (tag) {
                        "b" -> bold = if (closing) (bold - 1).coerceAtLeast(0) else bold + 1
                        "i" -> italic = if (closing) (italic - 1).coerceAtLeast(0) else italic + 1
                        "u" -> underline = if (closing) (underline - 1).coerceAtLeast(0) else underline + 1
                        "color" -> color = if (closing) null else parseColor(arg)
                        "url" -> url = !closing
                        "align" -> align = if (closing) null else textAlignOf(arg)
                        // Discuz 也支持 [center]/[right]/[left] 简写
                        "center", "right", "left" -> align = if (closing) null else textAlignOf(tag)
                        else -> Unit
                    }
                }
                position = tagEnd
            }
            if (position < raw.length) {
                appendChunk(raw.substring(position), spanStyle(), align)
            }
        }
    }

    /** 同一段文字同时带「行内样式 + 段落对齐」时，Compose 只支持分别 withStyle，这里嵌套处理 */
    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendChunk(
        chunk: String,
        span: SpanStyle?,
        align: TextAlign?,
    ) {
        val spanStyle = span ?: SpanStyle()
        val paragraph = align?.let { ParagraphStyle(textAlign = it) }
        if (paragraph == null) {
            withStyle(spanStyle) { append(chunk) }
        } else {
            withStyle(paragraph) { withStyle(spanStyle) { append(chunk) } }
        }
    }
    private fun textAlignOf(value: String): TextAlign? = when (value.trim().lowercase()) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        "left" -> TextAlign.Start
        else -> null
    }

    private fun parseColor(raw: String): Color? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return runCatching { Color(android.graphics.Color.parseColor(v)) }.getOrNull()
    }

    /**
     * 原文（含标签）↔ 显示文本（去标签）的偏移映射。
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
