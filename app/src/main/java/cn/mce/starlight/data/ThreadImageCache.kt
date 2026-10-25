package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 帖子首图缓存：列表页 HTML 里没有缩略图，
 * 所以按需抓取帖子页，取正文第一张图作为列表配图，并缓存。
 */
object ThreadImageCache {
    private val cache = mutableMapOf<Int, String?>()
    private const val BASE = "https://mce.msi.cn"

    suspend fun firstImage(tid: Int): String? = withContext(Dispatchers.IO) {
        cache[tid]?.let { return@withContext it }
        val url = runCatching {
            val html = GenericPageClient.get("$BASE/forum.php?mod=viewthread&tid=$tid")
            val doc = Jsoup.parse(html, BASE)
            val img = doc.select("div.message img[src], .message img[src]")
                .mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
                .map { absolutize(it) }
                .firstOrNull { it.contains("/data/attachment/") && !it.contains("smiley") }
            img
        }.getOrNull()
        cache[tid] = url
        url
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }
}
