package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** 发帖页表情：从网页端发帖页抓取表情图，失败则用 emoji 兜底 */
object SmileyClient {
    private const val BASE = "https://mce.msi.cn"

    data class Smiley(val url: String?, val emoji: String?)

    private val EMOJIS = listOf(
        "\uD83D\uDE00","\uD83D\uDE03","\uD83D\uDE04","\uD83D\uDE01","\uD83D\uDE06","\uD83D\uDE05","\uD83D\uDE02",
        "\uD83E\uDD23","\uD83D\uDE0A","\uD83D\uDE07","\uD83D\uDE42","\uD83D\uDE09","\uD83D\uDE0C","\uD83D\uDE0D",
        "\uD83E\uDD70","\uD83D\uDE18","\uD83D\uDE17","\uD83D\uDE19","\uD83D\uDE1A","\uD83D\uDE0B","\uD83D\uDE1B",
        "\uD83D\uDE1D","\uD83D\uDE1C","\uD83E\uDD2A","\uD83E\uDD28","\uD83E\uDD14","\uD83E\uDD13","\uD83D\uDE0E",
        "\uD83E\uDD73","\uD83D\uDE12","\uD83D\uDE44","\uD83D\uDE2C","\uD83D\uDE2D","\uD83D\uDE30","\uD83D\uDE25",
        "\uD83E\uDD7A","\uD83D\uDE22","\uD83D\uDE2B","\uD83D\uDE20","\uD83D\uDE21","\uD83E\uDD2C","\uD83E\uDD2F",
        "\uD83D\uDE33","\uD83E\uDD75","\uD83E\uDD76","\uD83D\uDE31","\uD83D\uDE28","\uD83D\uDE30","\uD83D\uDE25",
        "\uD83D\uDE13","\uD83E\uDD17","\uD83E\uDD14","\uD83E\uDD2D","\uD83E\uDD2B","\uD83D\uDE36","\uD83D\uDE10",
        "\uD83D\uDE11","\uD83D\uDE44","\uD83D\uDE2F","\uD83D\uDE26","\uD83D\uDE27","\uD83D\uDE2E","\uD83D\uDE32",
    )

    suspend fun load(fid: Int): List<Smiley> = withContext(Dispatchers.IO) {
        val fromWeb = runCatching {
            val html = GenericPageClient.get("$BASE/forum.php?mod=post&action=newthread&fid=$fid")
            if (GenericPageClient.isLoginPage(html)) return@runCatching emptyList()
            val doc = Jsoup.parse(html, BASE)
            doc.select("img[smilieid], #smilies img, div.smilies img").mapNotNull { img ->
                val src = img.attr("src")
                if (src.isBlank() || !src.contains("smiley")) null
                else Smiley(absolutize(src).replace(" ", "%20"), null)
            }.distinctBy { it.url }
        }.getOrDefault(emptyList())

        if (fromWeb.isNotEmpty()) fromWeb
        else EMOJIS.map { Smiley(null, it) }
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }
}