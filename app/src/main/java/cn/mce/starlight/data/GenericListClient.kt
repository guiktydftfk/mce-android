package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 通用个人页列表（勋章 / 好友 / 道具 等）。
 * 不依赖具体模板：抓取页面里「链接 + 文本 + 可选图片」构成的条目。
 */
object GenericListClient {
    private const val BASE = "https://mce.msi.cn"

    data class Item(
        val title: String,
        val subtitle: String?,
        val image: String?,
        val url: String?,
        val tid: Int?,
        val uid: Int?,
    )

    suspend fun load(path: String): List<Item> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (path.startsWith("http")) path else "$BASE/$path"
            val html = GenericPageClient.get(url)
            val doc = Jsoup.parse(html, url)

            // 优先解析 li 条目，其次 img 卡片（勋章常是图片墙）
            val items = mutableListOf<Item>()

            doc.select("li").forEach { li ->
                val a = li.selectFirst("a[href]") ?: return@forEach
                val text = li.text().trim()
                if (text.length !in 2..200) return@forEach
                val href = a.attr("href")
                items += buildItem(li, a, text, href)
            }

            if (items.isEmpty()) {
                doc.select("img[src*=medal]").forEach { img ->
                    val alt = img.attr("alt").trim()
                    items += Item(
                        title = alt.ifBlank { "勋章" },
                        subtitle = img.attr("title").trim().ifBlank { null },
                        image = absolutize(img.attr("src")),
                        url = null,
                        tid = null,
                        uid = null,
                    )
                }
            }

            items.map { it }
                .filter { it.title.isNotBlank() }
                .distinctBy { it.title to it.url }
                .take(80)
        }.getOrDefault(emptyList())
    }

    private fun buildItem(li: Element, a: Element, text: String, href: String): Item {
        val tid = Regex("""tid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
        val uid = Regex("""uid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
        val image = li.selectFirst("img[src]")?.attr("src")
            ?.takeIf { it.isNotBlank() && !it.contains("filetype") }
            ?.let(::absolutize)
        val title = a.text().trim().ifBlank { text }
        val subtitle = li.selectFirst("p, span, time")?.text()?.trim()
            ?.takeIf { it.isNotBlank() && it != title }
        return Item(
            title = title,
            subtitle = subtitle,
            image = image,
            url = absolutize(href),
            tid = tid,
            uid = uid,
        )
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }
}
