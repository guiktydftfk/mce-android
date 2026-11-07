package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * v0.4.3：TA 的主题（电脑版空间主题页）。
 *
 * URL: home.php?mod=space&uid=X&do=thread&view=me&from=space&page=N&mobile=no
 *
 * 实测结构（Discuz X3.5 电脑版空间）：
 *   <div class="tl"><table>
 *     <tr class="th">…表头…</tr>
 *     <tr>
 *       <td class="icn">…</td>
 *       <th><a href="forum.php?mod=viewthread&tid=37657">牛师傅上门装机记</a></th>
 *       <td><a href="forum.php?mod=forumdisplay&fid=2" class="xg1">综合交流</a></td>
 *       <td class="num"><a class="xi2">4</a><em>33</em></td>
 *       <td class="by"><cite><a>肉丸鸭鸭</a></cite><em><a>2026-10-19 20:41</a></em></td>
 *     </tr>
 *   </table></div>
 *
 * 只解析这张表，天然排除页面里「常用表单」等导航链接（之前误收录的原因）。
 */
object UserThreadsClient {
    private const val BASE = Net.BASE
    private val tidRegex = Regex("""tid=(\d+)""")

    data class Item(
        val tid: Int,
        val title: String,
        val board: String? = null,
        val lastPoster: String? = null,
        val time: String? = null,
        val replies: Int? = null,
        val views: Int? = null,
    )

    suspend fun load(uid: Int, page: Int = 1): List<Item> = withContext(Dispatchers.IO) {
        if (uid <= 0) return@withContext emptyList()
        val url = "$BASE/home.php?mod=space&uid=$uid&do=thread&view=me&from=space&page=$page&mobile=no"
        val html = runCatching {
            GenericPageClient.get(url, userAgent = Net.DESKTOP_UA, isolateCookies = true)
        }.getOrNull() ?: return@withContext emptyList()
        parse(html)

    }

    internal fun parse(html: String): List<Item> {
        val doc = Jsoup.parse(html, BASE)
        return doc.select("div.tl table tr").mapNotNull { tr ->
            if (tr.hasClass("th")) return@mapNotNull null
            val titleEl = tr.selectFirst("th a[href*=viewthread]") ?: return@mapNotNull null
            val tid = tidRegex.find(titleEl.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val title = titleEl.text().trim()
            if (title.length < 2) return@mapNotNull null

            val board = tr.selectFirst("td a[href*=forumdisplay]")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
            val replies = tr.selectFirst("td.num a")?.text()?.trim()?.toIntOrNull()
            val views = tr.selectFirst("td.num em")?.text()?.trim()?.toIntOrNull()
            val lastPoster = tr.selectFirst("td.by cite a")?.text()?.trim()?.takeIf { it.isNotBlank() }
            val time = tr.selectFirst("td.by em a")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: tr.selectFirst("td.by em")?.text()?.trim()

            Item(tid, title, board, lastPoster, time, replies, views)
        }.distinctBy { it.tid }
    }
}
