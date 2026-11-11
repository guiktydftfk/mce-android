package cn.mce.starlight.core

/**
 * v0.4.7：渲染层兜底解码 HTML 实体。
 *
 * 背景：站点部分帖子标题在数据里是双重转义（`&amp;#129402;`），解析层解码后
 * 若命中旧缓存（FeedCache 存的是解码前的字符串），仍会显示成 `&#129402;` 原文。
 * 这里在渲染前再解一次，保证旧缓存数据也能正确显示（emoji 等）。
 */
object HtmlText {
    private fun unescape(s: String): String =
        runCatching { org.jsoup.parser.Parser.unescapeEntities(s, false) ?: s }.getOrDefault(s)

    fun decode(text: String?): String {
        if (text.isNullOrEmpty() || !text.contains('&')) return text.orEmpty()
        var out = text
        // 最多两轮：兼容 &amp;#129402; 这类双重转义
        repeat(2) {
            val next = unescape(out)
            if (next == out) return out
            out = next
        }
        return out
    }
}
