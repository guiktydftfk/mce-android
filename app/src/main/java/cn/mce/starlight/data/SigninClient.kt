package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Sign-in (k_misign plugin) - implemented against real endpoints:
 *   state:  GET plugin.php?id=k_misign:sign
 *   sign:   GET plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=xxx
 *   ranks:  GET plugin.php?id=k_misign:sign&operation=list&op=(today|month|zong)&page=N&format=ajax
 *
 * 排行版页由 JS 分页（每页 200 条）：本月/总排行有 2 页以上，旧实现只解析第一页并 take(30)，
 * 因此「排行榜显示不完整」。现按页抓取，由 UI 触底加载下一页。
 */
object SigninClient {
    private const val BASE = Net.BASE
    private const val SIGN_PAGE = "$BASE/plugin.php?id=k_misign:sign"

    /** 服务端每页条数（实测 200）；返回满页即认为可能有下一页 */
    private const val PAGE_SIZE = 200

    /** 最多抓多少页，避免总排行无限翻页 */
    const val MAX_PAGES = 20

    // v0.3.3 批 3：循环内正则全部提升为常量（排行榜一页 200 行，旧实现每行编译 4~5 个 Regex）
    private val uidRegex = Regex("""uid=(\d+)""")
    private val monthDaysRegex = Regex("""月天数\s*(\d+)""")
    private val firstDayRegex = Regex("""(\d+)\s*天""")
    private val rewardRegex = Regex("""上次奖励\s*([\d.]+)""")
    private val firstIntRegex = Regex("""(\d+)""")
    private val formhashFromQiandaoRegex = Regex("""operation=qiandao[^"']*?formhash=([a-z0-9]+)""")
    private val levelRegex = Regex("""Lv\s*(\d+)""")

    /** 带标签的数字："今日排名 25" / "今日排名：第 25" 等；label 只有 3 个固定值，做缓存 */
    private val labelRegexCache = mutableMapOf<String, List<Regex>>()
    private fun labelPatterns(label: String): List<Regex> = synchronized(labelRegexCache) {
        labelRegexCache.getOrPut(label) {
            listOf(
                Regex("""$label\s*[:：]?\s*(?:第\s*)?(\d+)"""),
                Regex("""$label[^\d]{0,8}(\d+)"""),
            )
        }
    }

    data class RankEntry(
        val username: String,
        val uid: Int?,
        val totalDays: Int?,
        val monthDays: Int?,
        val reward: String?,
        val lastSignText: String?,
        val avatarUrl: String?,
    )

    data class RankPage(val entries: List<RankEntry>, val hasMore: Boolean)

    data class State(
        val todayRank: String? = null,
        val continuous: String? = null,
        val total: String? = null,
        val level: String? = null,
        val alreadySigned: Boolean = false,
        val formhash: String? = null,
        val ranks: List<RankEntry> = emptyList(),
        val hasMoreRanks: Boolean = false,
    )

    private fun opOf(rankType: String): String = when (rankType) {
        "month" -> "month"
        "total" -> "zong"
        else -> "today"
    }

    suspend fun load(rankType: String = "today"): State =
        withContext(Dispatchers.IO) { loadInternal(rankType, withRanks = true) }

    /**
     * 只取签到状态（页头统计 + 今天是否已签 + formhash），**不拉排行榜**。
     * 启动签到提醒这类场景只需要"今天签没签到"，旧实现顺带抓了 200 条排行榜，
     * 等于启动时多一个整页请求 + 一次大列表解析。
     */
    suspend fun loadStateOnly(): State =
        withContext(Dispatchers.IO) { loadInternal("today", withRanks = false) }

    private suspend fun loadInternal(rankType: String, withRanks: Boolean): State {
        val html = GenericPageClient.get(SIGN_PAGE)
        val doc = Jsoup.parse(html, SIGN_PAGE)

        // stats: parse numbers next to labels from whole page text (most robust)
        val pageText = doc.text()
        fun pick(label: String): String? {
            val patterns = labelPatterns(label)
            patterns.first().find(pageText)?.let { return it.groupValues[1] }
            return patterns.last().find(pageText)?.groupValues?.get(1)
        }
        val stats = mapOf(
            "\u4eca\u65e5\u6392\u540d" to pick("\u4eca\u65e5\u6392\u540d"),
            "\u8fde\u7eed\u7b7e\u5230" to pick("\u8fde\u7eed\u7b7e\u5230"),
            "\u7d2f\u8ba1\u7b7e\u5230" to pick("\u7d2f\u8ba1\u7b7e\u5230"),
        )

        fun stat(vararg keys: String): String? =
            stats.entries.firstOrNull { entry -> keys.any { entry.key.contains(it) } }?.value

        val alreadySigned = doc.selectFirst("a.btn_visited") != null ||
            doc.select("a, span, button").any { it.text().trim() == "\u5df2\u7b7e\u5230" }

        val formhash = formhashFromQiandaoRegex.find(html)?.groupValues?.get(1)
            ?: Net.formhashOf(html)

        val level = levelRegex.find(pageText)?.groupValues?.get(1)

        val firstPage = if (withRanks) {
            runCatching { loadRankPage(rankType, 1) }.getOrDefault(RankPage(emptyList(), false))
        } else {
            RankPage(emptyList(), false)
        }

        return State(
            todayRank = stat("\u4eca\u65e5\u6392\u540d"),
            continuous = stat("\u8fde\u7eed\u7b7e\u5230"),
            total = stat("\u7d2f\u8ba1\u7b7e\u5230"),
            level = level,
            alreadySigned = alreadySigned,
            formhash = formhash,
            ranks = firstPage.entries,
            hasMoreRanks = firstPage.hasMore,
        )
    }

    /** 抓取指定页排行榜（page 从 1 开始）；format=ajax 返回纯列表片段，省流量 */
    suspend fun loadRankPage(rankType: String, page: Int): RankPage = withContext(Dispatchers.IO) {
        val url = "$BASE/plugin.php?id=k_misign:sign&operation=list" +
            "&op=${opOf(rankType)}&page=$page&format=ajax"
        val html = GenericPageClient.get(url)
        if (GenericPageClient.isLoginPage(html)) return@withContext RankPage(emptyList(), false)
        val entries = parseRankEntries(html)
        RankPage(entries, hasMore = entries.size >= PAGE_SIZE && page < MAX_PAGES)
    }

    /** 解析排行榜 HTML（整页或 ajax 片段皆可）——直接暴露给单元测试 */
    fun parseRankEntries(html: String): List<RankEntry> {
        // format=ajax 只返回一堆 <tbody>，没有外层 <table>。
        // Jsoup 用 HTML 规则解析时会把游离的 tbody/tr 丢掉，所以这里补一个 table 外壳。
        val wrapped = if (html.contains("<table", ignoreCase = true)) {
            html
        } else {
            "<table id=\"misign_list\">$html</table>"
        }
        val doc = Jsoup.parse(wrapped, BASE)
        // 每个用户一个 tbody#autolist_<uid>；没有时退回整张表
        val rows = doc.select("tbody[id^=autolist_] tr").ifEmpty { doc.select("#misign_list tr") }

        return rows.mapNotNull { row ->
            val cell = row.selectFirst("td.k_misign_lc") ?: row
            val nameLink = cell.selectFirst("h4 a")
                ?: row.selectFirst("a[href*=uid=]")
                ?: return@mapNotNull null
            val name = nameLink.text().trim()
            if (name.isBlank()) return@mapNotNull null

            val uid = uidRegex.find(nameLink.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull()

            val h4 = cell.selectFirst("h4") ?: row.selectFirst("h4")
            // 总天数：<span class="y">总天数 183天</span>
            val totalText = cell.selectFirst("span.y")?.text() ?: h4?.selectFirst("span.y")?.text()
            val totalDays = firstInt(totalText)

            // <p>月天数 19 天 ， 上次奖励 2 社区值</p>
            val pText = cell.selectFirst("p")?.text().orEmpty()
            val monthDays = monthDaysRegex.find(pText)
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: firstDayRegex.find(pText)?.groupValues?.get(1)?.toIntOrNull()
            val reward = rewardRegex.find(pText)?.groupValues?.get(1)

            // 签到时间：h4 里除 .y 之外的 span（如「8 分钟前」）
            val lastSignText = h4?.select("span")?.firstOrNull { !it.hasClass("y") }
                ?.text()?.trim()?.replace("\u00a0", " ")?.takeIf { it.isNotBlank() }

            val avatarUrl = row.selectFirst("td.k_misign_lu img")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let { src ->
                    when {
                        src.startsWith("http") -> src
                        src.startsWith("//") -> "https:$src"
                        src.startsWith("/") -> "$BASE$src"
                        else -> "$BASE/$src"
                    }
                }

            RankEntry(
                username = name,
                uid = uid,
                totalDays = totalDays,
                monthDays = monthDays,
                reward = reward,
                lastSignText = lastSignText,
                avatarUrl = avatarUrl,
            )
        }.distinctBy { it.uid ?: it.username }
    }

    private fun firstInt(text: String?): Int? =
        firstIntRegex.find(text.orEmpty())?.groupValues?.get(1)?.toIntOrNull()

    /** Execute sign-in; response is CDATA text like "already signed today" */
    suspend fun signIn(state: State): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val hash = state.formhash ?: return@withContext (false to "\u7f3a\u5c11 formhash")
        runCatching {
            val raw = GenericPageClient.get(
                "$BASE/plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=$hash",
            )
            val text = raw.substringAfter("<![CDATA[").substringBefore("]]>").ifBlank { raw }.trim()
            when {
                text.contains("\u5df2\u7b7e") -> true to "\u4eca\u5929\u5df2\u7b7e\u5230"
                text.contains("\u5931\u8d25") || text.contains("\u9519\u8bef") ->
                    false to text.take(30)
                else -> true to text.take(30).ifBlank { "\u7b7e\u5230\u6210\u529f" }
            }
        }.getOrDefault(false to "\u7f51\u7edc\u9519\u8bef")
    }
}
