package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Sign-in (k_misign plugin) - implemented against real endpoints:
 *   state:  GET plugin.php?id=k_misign:sign
 *   sign:   GET plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=xxx
 *   ranks:  GET plugin.php?id=k_misign:sign&operation=list (today) / &op=month / &op=zong
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
    )

    suspend fun load(rankType: String = "today"): State = withContext(Dispatchers.IO) {
        val html = GenericPageClient.get(SIGN_PAGE)
        val doc = Jsoup.parse(html, SIGN_PAGE)

        // stats blocks: <div class="item"><div>label</div><div>value</div></div>
        val stats = doc.select("div.info div.item").mapNotNull { item ->
            val parts = item.select("div")
            if (parts.size >= 2) parts[0].text().trim() to parts[1].text().trim() else null
        }.toMap()

        fun stat(vararg keys: String): String? =
            stats.entries.firstOrNull { entry -> keys.any { entry.key.contains(it) } }?.value

        val alreadySigned = doc.selectFirst("a.btn_visited") != null ||
            doc.select("a, span, button").any { it.text().trim() == "\u5df2\u7b7e\u5230" }

        val formhash = Regex("""operation=qiandao[^"']*?formhash=([a-z0-9]+)""").find(html)
            ?.groupValues?.get(1)
            ?: Regex("""name="formhash"\s+value="([a-z0-9]+)"""").find(html)?.groupValues?.get(1)

        val level = Regex("""Lv\s*(\d+)""").find(doc.text())?.groupValues?.get(1)

        val ranks = runCatching { loadRanks(rankType) }.getOrDefault(emptyList())

        State(
            todayRank = stat("\u4eca\u65e5\u6392\u540d"),
            continuous = stat("\u8fde\u7eed\u7b7e\u5230"),
            total = stat("\u7d2f\u8ba1\u7b7e\u5230"),
            level = level,
            alreadySigned = alreadySigned,
            formhash = formhash,
            ranks = ranks,
        )
    }

    /** Rankings: rows of table#misign_list */
    private fun loadRanks(rankType: String): List<RankEntry> {
        val extra = when (rankType) {
            "month" -> "&op=month"
            "total" -> "&op=zong"
            else -> ""
        }
        val html = GenericPageClient.get("$BASE/plugin.php?id=k_misign:sign&operation=list$extra")
        val doc = Jsoup.parse(html, BASE)

        return doc.select("table#misign_list tr").mapNotNull { row ->
            val nameLink = row.selectFirst("td.k_misign_lc h4 a")
                ?: row.selectFirst("a[href*=space]")
                ?: return@mapNotNull null
            val name = nameLink.text().trim()
            if (name.isBlank()) return@mapNotNull null

            val uid = Regex("""uid=(\d+)""").find(nameLink.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull()

            val totalDays = row.selectFirst("span.y")?.text()?.trim()
            val monthDays = row.selectFirst("td.k_misign_lc p")?.text()
                ?.let { Regex("""(\d+)\s*\u5929""").find(it)?.groupValues?.get(1) }

            val value = listOfNotNull(totalDays, monthDays?.let { "M$it" })
                .joinToString(" / ").ifBlank { "-" }

            RankEntry(name, value, uid)
        }.distinctBy { it.username }.take(30)
    }

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
