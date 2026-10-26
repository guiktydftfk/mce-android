package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 发帖表情：扫描论坛页面里出现的 smiley 资源地址（含 JS 表情数据），
 * 覆盖 reddragon（小红龙）等表情包。
 */
object SmileyClient {
    private const val BASE = "https://mce.msi.cn"
    data class Smiley(val url: String)
    private var cache: List<Smiley> = emptyList()

    private val REGEX = Regex("""(?:static/image/smiley/[^"'\s<>\\]+|forum\.php\?mod=image[^"'\s<>\\]+)""")

    suspend fun load(fid: Int): List<Smiley> = withContext(Dispatchers.IO) {
        if (cache.isNotEmpty()) return@withContext cache
        val urls = LinkedHashSet<String>()
        listOf(
            "$BASE/forum.php?mod=post&action=newthread&fid=$fid",
            "$BASE/forum.php?mod=post&action=reply&fid=$fid&tid=38023",
            "$BASE/forum.php?mod=viewthread&tid=38023",
        ).forEach { page ->
            runCatching {
                val html = GenericPageClient.get(page)
                if (GenericPageClient.isLoginPage(html)) return@runCatching
                REGEX.findAll(html).forEach { m ->
                    val path = m.value
                    if (path.contains("smiley")) urls.add(absolutize(path))
                }
            }
        }
        val list = urls.map { Smiley(it.replace(" ", "%20")) }
        if (list.isNotEmpty()) cache = list
        list
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("http") -> src
        src.startsWith("//") -> "https:$src"
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }
}