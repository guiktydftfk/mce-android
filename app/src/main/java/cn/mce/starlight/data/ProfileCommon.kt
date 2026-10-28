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
    /** v0.4.1：用户等级（网页端资料页「用户等级 / 等级: Lv.x」）；与用户组分开解析，两者互不覆盖 */
    private val levelRegex = Regex("""(?:用户)?等级[:：]?\s*([^\s|]{1,24})""")
    private val lvRegex = Regex("""Lv\.?\s*\d+""", RegexOption.IGNORE_CASE)
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
        // 实测：资料页头部 <div class="usernames"><i>用户组</i><i>Lv.4</i>，按「非 Lv 的那一个 i」取完整用户组（不再被空格截断）
        doc.select("div.usernames i, .usernames i").map { it.text().trim() }
            .firstOrNull { it.isNotBlank() && !it.matches(lvRegex) }
            ?: doc.selectFirst(".grouptitle, .xw1")?.text()?.trim()
            ?: groupRegex.find(text)?.groupValues?.get(1)

    /** v0.4.1：用户等级（Lv.x / 星星等级等；与用户组分开解析，两者互不覆盖） */
    fun level(doc: Document, text: String): String? {
        // 实测资料页结构：<div class="usernames"><span>昵称</span><i>用户组</i><i>Lv.4</i></div>
        doc.select("div.usernames i, .usernames i").map { it.text().trim() }
            .firstOrNull { it.matches(lvRegex) || it.startsWith("Lv", ignoreCase = true) }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        levelRegex.find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 24 }
            ?.let { return it }
        return doc.selectFirst(".user_level, .level")?.text()?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 24 }
    }

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

    /**
     * v0.4.1：从资料页的 `ul.user_box` 结构化字段取值。
     * 实测结构：<li><span>QQ号</span><p>3065537005</p></li>
     * （旧的整页正则按 "QQ" 匹配会截到「号」字，导致 QQ/微信显示成「号」）
     */
    fun boxKv(doc: org.jsoup.nodes.Document): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        doc.select("ul.user_box li, .user_box li").forEach { li ->
            val label = (li.selectFirst("span") ?: li.selectFirst("b") ?: li.selectFirst("em"))
                ?.text()?.trim()
            val value = (li.selectFirst("p") ?: li.selectFirst("a"))?.text()?.trim()
            if (!label.isNullOrBlank() && !value.isNullOrBlank() &&
                label.length <= 10 && value.length <= 64
            ) {
                out.putIfAbsent(label, value)
            }
        }
        return out
    }

    /** v0.4.1：资料字段优先按 `ul.user_box` 结构化解析，取不到再退回整页正则 */
    fun fieldsFromBox(doc: org.jsoup.nodes.Document, text: String, labels: List<String>): Map<String, String> {
        val box = boxKv(doc)
        val fallback = fields(text, labels)
        val out = LinkedHashMap<String, String>()
        labels.forEach { label ->
            val hit = box.entries.firstOrNull { (k, _) ->
                k == label || k == label + "号" || k + "号" == label
            }?.value
            val value = hit ?: fallback[label]
            if (!value.isNullOrBlank()) out[label] = value
        }
        return out
    }

    /** v0.4.1：站点字段「积分」对外显示为「星光值」（解析/提交仍用原字段） */
    fun displayStatName(name: String): String = when (name) {
        "积分" -> "星光值"
        else -> name
    }

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
