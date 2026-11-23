package cn.mce.starlight.core

/**
 * v0.4.8：私信里的网址识别。
 *
 * 私信正文是纯文本，之前整段不可点。现在把 http(s):// 与 www. 开头的链接识别出来，
 * 在气泡里显示为可点击（下划线 + 主题色），点击用系统浏览器打开。
 *
 * 与帖子正文的识别规则保持一致，并额外去掉尾部标点，避免把
 * 「https://example.com。」里的句号当成链接的一部分。
 */
object PmLinkify {

    data class Link(
        /** 可直接打开的完整 URL（www. 会自动补 https://） */
        val url: String,
        /** 在原文中的起始下标 */
        val start: Int,
        /** 在原文中的结束下标（不含） */
        val endExclusive: Int,
    )

    private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"'()（）\[\]【】<>，。；：！？、]+)""")

    private const val TRAILING = ".,;:!?、。，；：！？)）]】}>\"'"

    fun find(text: String): List<Link> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<Link>()
        urlRegex.findAll(text).forEach { m ->
            var raw = m.value
            while (raw.isNotEmpty() && raw.last() in TRAILING) raw = raw.dropLast(1)
            if (raw.length <= 4) return@forEach
            val url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            out += Link(url = url, start = m.range.first, endExclusive = m.range.first + raw.length)
        }
        return out
    }

    /** 是否包含可点击链接（UI 用来决定是否走 ClickableText） */
    fun hasLinks(text: String): Boolean = find(text).isNotEmpty()
}
