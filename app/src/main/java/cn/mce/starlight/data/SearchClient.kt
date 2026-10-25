package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/** 原生搜索：POST search.php，解析结果列表 */
object SearchClient {
    private const val BASE = "https://mce.msi.cn"

    data class Hit(val tid: Int, val title: String, val snippet: String?)

    suspend fun search(keyword: String): List<Hit> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()

        val forumHtml = GenericPageClient.get("$BASE/forum.php")
        val formhash = Regex("""formhash=([a-z0-9]+)""").find(forumHtml)?.groupValues?.get(1).orEmpty()

        val body = listOf(
            "formhash" to formhash,
            "mod" to "forum",
            "srchtxt" to keyword,
            "searchsubmit" to "yes",
        ).joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }

        val html = GenericPageClient.httpPost("$BASE/search.php?searchsubmit=yes", body, null)
        val doc = Jsoup.parse(html, BASE)

        val hits = doc.select("a[href*=viewthread]")
            .mapNotNull { a ->
                val href = a.attr("href")
                val tid = Regex("""tid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@mapNotNull null
                val title = a.text().trim()
                if (title.length < 4) return@mapNotNull null
                Hit(tid, title, null)
            }
            .distinctBy { it.tid }

        hits.take(50)
    }
}
