package cn.mce.starlight.core

/**
 * v0.4.7：HTML 实体解码（渲染层/解析层共用兜底）。
 *
 * 背景：站点部分帖子标题在数据里是双重转义（`&amp;#129402;`），甚至有 astral 码点
 * （emoji）。Jsoup 的 unescape 在个别环境下对超 BMP 数字实体不生效，这里用自带的
 * 数字实体解码（支持 `&#129402;` 与 `&#x1F97A;`），再用 Jsoup 兜底命名实体。
 */
object HtmlText {
    private val decRegex = Regex("""&#(?:[xX]([0-9a-fA-F]+)|(\d+));""")
    private val named = mapOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&nbsp;" to " ",
    )

    fun decode(text: String?): String {
        if (text.isNullOrEmpty() || !text.contains('&')) return text.orEmpty()
        var out: String = text
        repeat(2) {
            val next = decodeOnce(out)
            if (next == out) return out
            out = next
        }
        return out
    }

    private fun decodeOnce(input: String): String {
        var s = input
        // 1) 数字实体（含 astral codepoint）
        s = decRegex.replace(s) { m ->
            val code = m.groupValues[1].ifBlank { m.groupValues[2] }
                .toIntOrNull(if (m.groupValues[1].isNotBlank()) 16 else 10) ?: return@replace m.value
            if (code <= 0 || code > 0x10FFFF) return@replace m.value
            runCatching { String(Character.toChars(code)) }.getOrDefault(m.value)
        }
        // 2) 常见命名实体
        named.forEach { (k, v) -> s = s.replace(k, v) }
        // 3) 其它命名实体交给 Jsoup（取不到就保持原样）
        if (s.contains('&')) {
            runCatching { org.jsoup.parser.Parser.unescapeEntities(s, false) }.getOrNull()?.let { s = it }
        }
        return s
    }
}
