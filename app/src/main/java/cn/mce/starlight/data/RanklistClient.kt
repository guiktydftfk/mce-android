package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 排行榜（misc.php?mod=ranklist）。
 *
 * 实测结构：
 *   li.menu_slide a            顶部标签（帖子/版块/用户）
 *   div.rank_box               每个标签对应的榜单
 *     ul.rank_thread > li > a  条目，i.rank_hot 名次，span.threadhot[title] 热度
 */
object RanklistClient {
    private const val BASE = "https://mce.msi.cn"

    data class RankItem(
        val title: String,
        val tid: Int?,
        val rank: String?,
        val hot: String?,
    )

    data class Tab(val name: String, val items: List<RankItem>)

    suspend fun load(): List<Tab> = withContext(Dispatchers.IO) {
        runCatching {
            val html = GenericPageClient.get("$BASE/misc.php?mod=ranklist")
            val doc = Jsoup.parse(html, "$BASE/misc.php?mod=ranklist")

            val tabNames = doc.select("li.menu_slide a").map { it.text().trim() }
                .filter { it.isNotBlank() }

            val boxes = doc.select("div.rank_box")
            boxes.mapIndexedNotNull { index, box ->
                val items = box.select("ul.rank_thread > li > a, ul.rank_thread li a").mapNotNull { a ->
                    val href = a.attr("href")
                    val tid = Regex("""tid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                    val rank = a.selectFirst("i")?.text()?.trim()
                    val hot = a.selectFirst("span.threadhot")?.attr("title")?.trim()
                    val hotText = a.selectFirst("span.threadhot")?.text()?.trim().orEmpty()
                    val rankText = a.selectFirst("i")?.text()?.trim().orEmpty()
                    val title = a.text().trim()
                        .removePrefix(hotText)
                        .removePrefix(rankText)
                        .trim()
                    if (title.isBlank()) null else RankItem(title, tid, rank, hot)
                }.distinctBy { it.title }.take(30)

                if (items.isEmpty()) null
                else Tab(tabNames.getOrNull(index) ?: "榜单${index + 1}", items)
            }
        }.getOrDefault(emptyList())
    }
}
