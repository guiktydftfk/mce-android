package cn.mce.starlight.data

import org.jsoup.nodes.Document

/**
 * 个人资料页的公共字段解析（v0.3.3 批 6 收敛）。
 *
 * `ProfileClient`（自己资料页）与 `UserProfileClient`（他人空间）原本各抄了一份
 * 「标题里的昵称 / 用户组 / 统计数字 / 资料字段」解析逻辑，现统一到这里。
 */
object ProfileCommon {
    private val usernameTitleRegex = Regex("""^(.{1,24}?)的个人资料""")
    private val groupRegex = Regex("""用户组[:：]?\s*([^\s|]{1,20})""")
    private val labelNumberCache = mutableMapOf<String, Regex>()
    private val labelTextCache = mutableMapOf<String, Regex>()

    private fun labelNumberRegex(label: String): Regex = synchronized(labelNumberCache) {
        labelNumberCache.getOrPut(label) { Regex("""$label\s*[:：]?\s*(\d+)""") }
    }

    private fun labelTextRegex(label: String): Regex = synchronized(labelTextCache) {
        labelTextCache.getOrPut(label) { Regex("""$label\s*[:：]?\s*([^\s|]{1,24})""") }
    }

    /** 昵称：优先「X的个人资料」标题，其次页面主标题 */
    fun username(doc: Document): String? =
        usernameTitleRegex.find(doc.title())?.groupValues?.get(1)?.trim()
            ?: doc.selectFirst("h1, h2, .user_name, .xs2")?.text()?.trim()
                ?.takeIf { it.length in 1..24 }

    /** 用户组：「用户组：Lv.4」或样式类 */
    fun group(doc: Document, text: String): String? =
        groupRegex.find(text)?.groupValues?.get(1)
            ?: doc.selectFirst(".grouptitle, .xw1")?.text()?.trim()

    /**
     * 管理身份（v0.3.10）：论坛里的「管理员 / 超级版主 / 版主」。
     *
     * 只看用户组字段与身份徽标元素（`.grouptitle/.xw1/.userstatus/i.mod` 等），
     * **不**在整页文本里找关键词 —— 否则帖子里出现「版主」二字就会把人误判成版主。
     * 普通用户返回 null，界面保持原样。
     */
    fun staffTitle(doc: Document, text: String): String? {
        val group = group(doc, text)
        val badges = doc.select("i.mod, em.mod, span.mod, .grouptitle, .xw1, .userstatus, .user_group")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        val hay = (listOfNotNull(group) + badges).joinToString(" ")
        // 「超级版主」要在「版主」之前判断，否则会被子串先命中
        return STAFF_TOKENS.firstOrNull { hay.contains(it) }
    }

    private val STAFF_TOKENS = listOf("\u7ba1\u7406\u5458", "\u8d85\u7ea7\u7248\u4e3b", "\u7248\u4e3b")

    /** 统计数字（主题 / 回帖 / 积分…） */
    fun stats(text: String, labels: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        labels.forEach { label ->
            labelNumberRegex(label).find(text)?.groupValues?.get(1)?.let { result[label] = it }
        }
        return result
    }

    /** 资料字段（姓名 / 学校 / 生日…，值取标签后到下一个分隔符之间的文本） */
    fun fields(text: String, labels: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        labels.forEach { label ->
            labelTextRegex(label).find(text)?.groupValues?.get(1)?.trim()
                ?.let { if (it.isNotBlank()) result[label] = it }
        }
        return result
    }
}
