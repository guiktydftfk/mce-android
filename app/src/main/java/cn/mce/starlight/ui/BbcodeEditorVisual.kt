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
 * 文本本身仍是 BBCode（不改存储、不改发布内容），但编辑时把
 * `[b]…[/b]`、`[i]…[/i]`、`[u]…[/u]`、`[color=#xxxxxx]…[/color]`、`[url]…[/url]`
 * 包裹的文字**直接按效果显示**，标签本身用灰色提示。
 *
 * 这样选中文字点「加粗/斜体/下划线/颜色」后，内容框里立刻能看到效果，
 * 不必再去「发帖预览」确认。
 *
 * 文本长度保持不变（[OffsetMapping.Identity]），光标/选区/输入法行为都不受影响。
 */
class BbcodeVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!raw.contains('[')) return TransformedText(text, OffsetMapping.Identity)

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

        val builder = buildAnnotatedString {
            var position = 0
            for (match in tagRegex.findAll(raw)) {
                if (match.range.first > position) {
                    val chunk = raw.substring(position, match.range.first)
                    val style = currentStyle()
                    if (style == null) append(chunk) else withStyle(style) { append(chunk) }
                }
                // 标签本身灰色弱化显示
                withStyle(tagStyle) { append(match.value) }
                val closing = match.groupValues[1] == "/"
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
                position = match.range.last + 1
            }
            if (position < raw.length) {
                val chunk = raw.substring(position)
                val style = currentStyle()
                if (style == null) append(chunk) else withStyle(style) { append(chunk) }
            }
        }
        return TransformedText(builder, OffsetMapping.Identity)
    }

    private fun parseColor(raw: String): Color? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return runCatching { Color(android.graphics.Color.parseColor(v)) }.getOrNull()
    }

    private companion object {
        val tagRegex = Regex("""\[(/?)([a-zA-Z]+)([^\]]*)]""")

        /** 标签提示样式：灰色弱化，不改变字号，避免排版跳动 */
        val tagStyle = SpanStyle(color = Color(0xFF7A828C))
    }
}
