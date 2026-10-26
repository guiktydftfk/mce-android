package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 发帖表情：**只使用论坛网页端的表情集**（含小红龙 reddragon 包）。
 *
 * 不用自造 emoji 的原因：不同 ROM 字体缺字形会出现方框 □，且与网页版不一致。
 * 优先抓发帖页表情面板；抓不到时从公开可见的帖子页采集（帖子页含 smilieid，游客可访问）。
 */
object SmileyClient {
    private const val BASE = "https://mce.msi.cn"

    data class Smiley(val url: String)

    private var cache: List<Smiley> = emptyList()

    private val SAMPLE_TID = 38023

    suspend fun load(fid: Int): List<Smiley> = withContext(Dispatchers.IO) {
        if (cache.isNotEmpty()) return@withContext cache

        val fromCompose = runCatching { harvest("$BASE/forum.php?mod=post&action=newthread&fid=$fid") }
            .getOrDefault(emptyList())
        val fromThread = if (fromCompose.isEmpty()) {
            runCatching { harvest("$BASE/forum.php?mod=viewthread&tid=$SAMPLE_TID") }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val list = (fromCompose + fromThread).distinctBy { it.url }
        if (list.isNotEmpty()) cache = list
        list
    }

    private fun harvest(url: String): List<Smiley> {
        val html = GenericPageClient.get(url)
        if (GenericPageClient.isLoginPage(html)) return emptyList()
        val doc = Jsoup.parse(html, BASE)
        return doc.select("img[smilieid], img[src*=smiley]").mapNotNull { img ->
            val src = img.attr("src")
            if (src.isBlank() || !src.contains("smiley")) return@mapNotNull null
            Smiley(absolutize(src))
        }.distinctBy { it.url }
    }

    private fun absolutize(src: String): String {
        val full = when {
            src.startsWith("//") -> "https:$src"
            src.startsWith("http") -> src
            src.startsWith("/") -> "$BASE$src"
            else -> "$BASE/$src"
        }
        return full.replace(" ", "%20")
    }
}
