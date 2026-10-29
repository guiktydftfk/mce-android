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
            val mode = when {
                path.contains("action=log") -> "log"
                path.contains("action=my") -> "my"
                else -> "shop"
            }

            // 页面里混有顶部快捷菜单（发新帖/抢沙发/去签到…），必须按“道具特征”过滤
            val banned = listOf(
                "发新帖", "抢沙发", "来打Call", "去签到", "查积分", "签到", "积分",
                "排行榜", "搜索", "消息", "提醒", "退出", "首页", "版块", "发现", "我的",
                "道具商店", "我的道具", "道具记录", "道具中心",
            )
            val qtyRegex = Regex("""(数量|剩余|库存|有效期)\s*[:：]?\s*([^\s|]{1,16})""")
            val timeRegex = Regex("""\d{4}-\d{1,2}-\d{1,2}|\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2}|昨天|前天|小时前|分钟前""")

            val boxes = doc.select("li, div.magic_card, div.magic_item, div.card")
            val items = mutableListOf<Item>()
            boxes.forEach { box ->
                val img = box.selectFirst("img[src]")
                val text = box.text().trim()
                if (text.length < 2 || text.length > 160) return@forEach
                val name = (img?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                    ?: box.selectFirst("h3, h4, dt, .name, .title, strong")?.text()?.trim()
                    ?: box.selectFirst("a[href]")?.text()?.trim().orEmpty())
                if (name.isBlank() || name.length > 24) return@forEach
                if (banned.any { name == it || name.contains(it) }) return@forEach
                val price = priceRegex.find(text)?.value?.trim()
                val qty = qtyRegex.find(text)?.let { it.value.trim() }
                val time = timeRegex.find(text)?.value
                val keep = when (mode) {
                    "shop" -> price != null
                    "my" -> qty != null || text.contains("我的")
                    else -> time != null
                }
                if (!keep) return@forEach
                val link = box.selectFirst("a[href]")?.attr("href")
                val item = Item(
                    name = name,
                    image = img?.attr("src")?.takeIf { it.isNotBlank() }?.let { Net.absolutize(it) },
                    price = when (mode) {
                        "shop" -> price
                        "my" -> qty ?: price
                        else -> time
                    },
                    desc = text.takeIf { it != name && it.length <= 80 && it != price },
                    url = link?.let { Net.absolutize(it) },
                )
                if (items.none { it.name == item.name }) items += item
            }
            if (items.isEmpty()) emptyList() else listOf(Section(null, items))
        }.getOrDefault(emptyList())
    }
}
