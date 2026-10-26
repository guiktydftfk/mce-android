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
