package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 帖子配图缓存：列表页 HTML 没有缩略图，
 * 按需抓帖子页，取正文第一张图（含附件链接），并缓存。
 */
object ThreadImageCache {
    private val cache = mutableMapOf<Int, String?>()
    private const val BASE = "https://mce.msi.cn"

    suspend fun firstImage(tid: Int): String? = withContext(Dispatchers.IO) {
        if (cache.containsKey(tid)) return@withContext cache[tid]
        val url = runCatching {
            val html = GenericPageClient.get("$BASE/forum.php?mod=viewthread&tid=$tid")
            val doc = Jsoup.parse(html, BASE)
            val root = doc.selectFirst("div.plc div.message")
                ?: doc.selectFirst("div.message")
                ?: return@runCatching null
            ThreadDetailParser.collectImages(root).firstOrNull()
        }.getOrNull()
        cache[tid] = url
        url
    }

    fun clear() = cache.clear()
}
