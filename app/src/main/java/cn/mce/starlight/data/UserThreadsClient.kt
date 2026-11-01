package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * v0.4.3：TA 的全部主题。
 *
 * 手机模板的「用户主题列表」路径不可用（返回提示信息页），改抓**电脑版**空间主题页：
 *   home.php?mod=space&uid=X&do=thread&view=me&from=space&mobile=no
 * 解析页内 viewthread 链接（标题 + tid），支持分页（?page=N）。
 */
object UserThreadsClient {
    private const val BASE = Net.BASE
    private val tidRegex = Regex("""tid=(\d+)""")

    data class Item(val tid: Int, val title: String)

    suspend fun load(uid: Int, page: Int = 1): List<Item> = withContext(Dispatchers.IO) {
        if (uid <= 0) return@withContext emptyList()
        val url = "$BASE/home.php?mod=space&uid=$uid&do=thread&view=me&from=space&page=$page&mobile=no"
        val html = runCatching {
            GenericPageClient.get(url, userAgent = Net.DESKTOP_UA, isolateCookies = true)
        }.getOrNull() ?: return@withContext emptyList()
        val doc = Jsoup.parse(html, url)
        doc.select("a[href*=viewthread]").mapNotNull { a ->
            val tid = tidRegex.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val title = a.text().trim()
            // 过滤「回复/查看」等导航链接与过短文本
            if (title.length < 4 || title.length > 80) return@mapNotNull null
            if (title == "回复" || title == "查看") return@mapNotNull null
            Item(tid, title)
        }.distinctBy { it.tid }
    }
}