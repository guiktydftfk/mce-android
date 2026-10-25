package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 签到（k_misign 插件）——按接口逻辑实现：
 *   状态：GET plugin.php?id=k_misign:sign      （.info .item 三个数值 + 是否已签到）
 *   签到：GET plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=xxx
 *   排行：GET plugin.php?id=k_misign:sign&operation=list（今日） / &op=month（本月） / &op=zong（总）
 */
object SigninClient {
    private const val BASE = "https://mce.msi.cn"
    private const val SIGN_PAGE = "$BASE/plugin.php?id=k_misign:sign"

    data class RankEntry(val username: String, val value: String, val uid: Int?)

    data class State(
        val todayRank: String? = null,
        val continuous: String? = null,
        val total: String? = null,
        val level: String? = null,
        val alreadySigned: Boolean = false,
        val formhash: String? = null,
        val ranks: List<RankEntry> = emptyList(),
        val notice: String? = null,
    )

    suspend fun load(rankType: String = "today"): State = withContext(Dispatchers.IO) {
        val html = GenericPageClient.get(SIGN_PAGE)
        val doc = Jsoup.parse(html, SIGN_PAGE)

        // 三个统计块：<div class="item"><div>今日排名</div><div>188</div></div>
        val stats = doc.select("div.info div.item").mapNotNull { item ->
            val parts = item.select("div")
            if (parts.size >= 2) parts[0].text().trim() to parts[1].text().trim() else null
        }.toMap()

        fun stat(vararg keys: String): String? =
            stats.entries.firstOrNull { e -> keys.any { e.key.contains(it) } }?.value

        val alreadySigned = doc.selectFirst("a.btn_visited") != null ||
            doc.select("a, span, button").any { it.text().trim() == "已签到" }

        val formhash = Regex("""operation=qiandao[^"']*?formhash=([a-z0-9]+)""").find(html)
            ?.groupValues?.get(1)
            ?: Regex("""name="formhash"\s+value="([a-z0-9]+)"""").find(html)?.groupValues?.get(1)

        val level = Regex("""签到等级\s*Lv\s*(\d+)""").find(doc.text())?.groupValues?.get(1)

        val ranks = runCatching { loadRanks(rankType) }.getOrDefault(emptyList())

        State(
            todayRank = stat("今日排名"),
            continuous = stat("连续签到"),
            total = stat("累计签到"),
            level = level,
            alreadySigned = alreadySigned,
            formhash = formhash,
            ranks = ranks,
        )
    }

    /** 排行榜 */
    private fun loadRanks(rankType: String): List<RankEntry> {
        val extra = when (rankType) {
            "month" -> "&op=month"
            "total" -> "&op=zong"
            else -> ""
        }
        val html = GenericPageClient.get("$BASE/plugin.php?id=k_misign:sign&operation=list$extra")
        val doc = Jsoup.parse(html, BASE)

        val entries = doc.select("li, tr, div.item").mapNotNull { row ->
            val link = row.selectFirst("a[href*=space]") ?: return@mapNotNull null
            val name = link.text().trim()
            if (name.isBlank()) return@mapNotNull null
            val digits = Regex("""\d+""").find(row.text().replace(name, ""))?.value
            val uid = Regex("""uid=(\d+)""").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            RankEntry(name, digits ?: "-", uid)
        }.distinctBy { it.username }

        return entries.take(20)
    }

    /** 执行签到（实测返回：<root><![CDATA[今日已签]]></root> 或成功文案） */
    suspend fun signIn(state: State): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val hash = state.formhash ?: return@withContext (false to "未获取到 formhash")
        runCatching {
            val raw = GenericPageClient.get(
                "$BASE/plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=$hash",
            )
            val text = raw.substringAfter("<![CDATA[").substringBefore("]]>").ifBlank { raw }.trim()
            when {
                text.contains("已签") -> true to "今天已签到"
                text.contains("失败") || text.contains("错误") -> false to text.take(30)
                else -> true to (text.take(30).ifBlank { "签到成功" })
            }
        }.getOrDefault(false to "网络错误")
    }
}
