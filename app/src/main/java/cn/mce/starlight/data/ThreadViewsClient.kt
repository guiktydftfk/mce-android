package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * v0.4.6：电脑版版块列表里的「查看数」抓取（后台补抓，不阻塞首屏）。
 * 电脑版行结构：tbody[id^=normalthread_] → a[href*=viewthread]（tid）+ td.num em（查看数）
 */
object ThreadViewsClient {
    private val tidRegex = Regex("""tid=(\d+)""")

    suspend fun load(fid: Int, page: Int = 1): Map<Int, Int> = withContext(Dispatchers.IO) {
        if (fid <= 0) return@withContext emptyMap()
        runCatching {
            val url = "${Net.BASE}/forum.php?mod=forumdisplay&fid=$fid&page=$page&mobile=no"
            val html = GenericPageClient.get(url, userAgent = Net.DESKTOP_UA, isolateCookies = true)
            cn.mce.starlight.core.DebugDump.dump("views_desktop_fid${fid}_p$page", html)
            val doc = Jsoup.parse(html, url)
            doc.select("tbody[id^=normalthread_], tbody[id^=stickthread_]").mapNotNull { tr ->
                val href = tr.selectFirst("a[href*=viewthread]")?.attr("href") ?: return@mapNotNull null
                val tid = tidRegex.find(href)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                val views = tr.selectFirst("td.num em")?.text()?.filter { it.isDigit() }?.toIntOrNull()
                    ?: return@mapNotNull null
                tid to views
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}
