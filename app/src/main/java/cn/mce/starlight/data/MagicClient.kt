package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * v0.4.1：道具中心（home.php?mod=magic）。
 *
 * 手机模板结构不稳定，这里用「含图标 + 价格文本」的启发式解析：
 *  - 商店：`home.php?mod=magic`（默认页）
 *  - 我的道具：`home.php?mod=magic&action=my`
 *  - 道具记录：`home.php?mod=magic&action=log`
 * 解析不到价格时仍列出条目（点击走网页/原生页）。
 */
object MagicClient {
    private const val BASE = Net.BASE

    private val priceRegex = Regex("""(\d+)\s*(?:社区值|赞赏值|星光值|金钱|贡献|积分)""")
    private val tidRegex = Regex("""tid=(\d+)""")

    data class Item(
        val name: String,
        val image: String?,
        val price: String?,
        val desc: String?,
        val url: String?,
    )

    data class Section(val title: String?, val items: List<Item>)

    suspend fun load(path: String): List<Section> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (path.startsWith("http")) path else "$BASE/$path"
            val html = GenericPageClient.get(url)
            val doc = Jsoup.parse(html, url)

            val sections = mutableListOf<Section>()
            // 1) 优先按“含图标的卡片”分组
            val containers = doc.select("li:has(img), div.magic_card, li.magic_item, div.magic_item")
            var currentTitle: String? = null
            val buffer = mutableListOf<Item>()
            var lastItem: Item? = null

            containers.forEach { box ->
                val img = box.selectFirst("img[src]")
                val text = box.text().trim()
                if (text.length < 2 || text.length > 120) return@forEach
                val name = (img?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                    ?: box.selectFirst("h3, h4, dt, .name, .title")?.text()?.trim()
                    ?: text.lineSequence().firstOrNull()?.trim().orEmpty())
                if (name.isBlank() || name.length > 24) return@forEach
                val price = priceRegex.find(text)?.value?.trim()
                val link = box.selectFirst("a[href]")?.attr("href")
                val item = Item(
                    name = name,
                    image = img?.attr("src")?.takeIf { it.isNotBlank() }?.let { Net.absolutize(it) },
                    price = price,
                    desc = text.takeIf { it != name && it.length <= 60 && price == null },
                    url = link?.let { Net.absolutize(it) },
                )
                // 条目里出现 "我的道具/商店/记录" 之类的分区标题时切换 section
                val heading = box.selectFirst("h1, h2, h3")?.text()?.trim()
                if (!heading.isNullOrBlank() && heading != name && heading.length <= 8) {
                    if (buffer.isNotEmpty()) {
                        sections += Section(currentTitle, buffer.toList())
                        buffer.clear()
                    }
                    currentTitle = heading
                }
                if (lastItem == null || lastItem?.name != item.name) buffer += item
                lastItem = item
            }
            if (buffer.isNotEmpty()) sections += Section(currentTitle, buffer.toList())

            // 2) 兜底：按链接抓带 magic 的条目
            if (sections.isEmpty()) {
                val fallback = doc.select("a[href*=magic]").mapNotNull { a ->
                    val name = a.text().trim()
                    if (name.length < 2 || name.length > 20) return@mapNotNull null
                    if (name.contains("道具") && name.length <= 4) return@mapNotNull null
                    Item(name, null, null, null, Net.absolutize(a.attr("href")))
                }.distinctBy { it.name }
                if (fallback.isNotEmpty()) sections += Section(null, fallback)
            }
            sections
        }.getOrDefault(emptyList())
    }
}
