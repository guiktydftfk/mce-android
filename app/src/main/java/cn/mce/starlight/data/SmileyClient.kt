package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 发帖表情面板。
 *
 * v0.3.2 修复「表情缺小红龙（reddragon）」：
 *  旧实现用正则 `[^"'\s<>\\]+` 扫页面里的 static/image/smiley/... 地址，遇到
 *  `red dragon (12).jpg` 这类**文件名带空格**的表情会在空格处被截断
 *  （得到 `static/image/smiley/reddragon/red` → 404），于是小红龙全套都加载不出来。
 *
 *  现在直接读论坛的表情定义文件 `data/cache/common_smilies_var.js`
 *  （smilies_type / smilies_array），拿到「小红龙 / 小红龙2 / 经典 / 小表情」全套，
 *  文件名保留空格并在拼 URL 时统一 %20 编码；插入正文用论坛原生表情代码 `{:5_176:}`。
 */
object SmileyClient {
    private const val BASE = Net.BASE
    private const val SMILEY_VAR = "$BASE/data/cache/common_smilies_var.js"

    data class Smiley(
        /** 表情代码，如 `{:5_176:}`，插入正文由 Discuz 渲染成图片 */
        val code: String,
        /** 面板里预览用的图片地址（空格已 %20 编码） */
        val url: String,
    )

    data class SmileySet(val id: String, val name: String, val dir: String, val smileys: List<Smiley>)

    private var cache: List<SmileySet> = emptyList()

    suspend fun load(fid: Int): List<SmileySet> = withContext(Dispatchers.IO) {
        if (cache.isNotEmpty()) return@withContext cache

        val fromVar = runCatching { parseSets(GenericPageClient.get(SMILEY_VAR)) }.getOrDefault(emptyList())
        val sets = if (fromVar.isNotEmpty()) fromVar else runCatching { scanPages(fid) }.getOrDefault(emptyList())
        if (sets.isNotEmpty()) cache = sets
        sets
    }

    // ---------- 解析 common_smilies_var.js ----------

    private val typeRegex =
        Regex("""smilies_type\['_(\d+)'\]\s*=\s*\['([^']*)'\s*,\s*'([^']*)'\]""")

    private val arrayRegex =
        Regex("""smilies_array\[(\d+)\]\[1\]\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL)

    /** 单个表情项：['176', '{:5_176:}','red dragon (1).jpg','40','40','50'] */
    private val itemRegex =
        Regex("""\[\s*'(\d+)'\s*,\s*'((?:\\.|[^'])*)'\s*,\s*'([^']+)'""")

    /** 直接暴露给单元测试 */
    fun parseSets(js: String): List<SmileySet> {
        val types = LinkedHashMap<String, Pair<String, String>>() // id -> (name, dir)
        typeRegex.findAll(js).forEach { m ->
            types[m.groupValues[1]] = m.groupValues[2] to m.groupValues[3]
        }

        val arrays = arrayRegex.findAll(js).associate { m ->
            m.groupValues[1] to m.groupValues[2]
        }

        // 按类型定义顺序输出（小红龙在前）
        return types.mapNotNull { (id, meta) ->
            val body = arrays[id] ?: return@mapNotNull null
            val (name, dir) = meta
            val smileys = itemRegex.findAll(body).mapNotNull { item ->
                val code = item.groupValues[2].replace("\\'", "'").trim()
                val file = item.groupValues[3].trim()
                if (file.isBlank()) return@mapNotNull null
                Smiley(code = code, url = smileyUrl(dir, file))
            }.toList()
            if (smileys.isEmpty()) null else SmileySet(id = id, name = name, dir = dir, smileys = smileys)
        }
    }

    private fun smileyUrl(dir: String, file: String): String =
        "$BASE/static/image/smiley/$dir/${file.replace(" ", "%20")}"

    // ---------- 兼容兜底：抓页面里的表情引用（允许文件名带空格） ----------

    private val pageRegex =
        Regex("""static/image/smiley/[^"'<>\r\n]+?\.(?:gif|jpe?g|png)""", RegexOption.IGNORE_CASE)

    private fun scanPages(fid: Int): List<SmileySet> {
        val urls = LinkedHashSet<String>()
        listOf(
            "$BASE/forum.php?mod=post&action=newthread&fid=$fid",
            "$BASE/forum.php?mod=post&action=reply&fid=$fid&tid=38023",
            "$BASE/forum.php?mod=viewthread&tid=38023",
        ).forEach { page ->
            runCatching {
                val html = GenericPageClient.get(page)
                if (GenericPageClient.isLoginPage(html)) return@runCatching
                pageRegex.findAll(html).forEach { m -> urls.add(m.value.trim()) }
            }
        }
        if (urls.isEmpty()) return emptyList()
        val smileys = urls.map { raw ->
            Smiley(code = "[img]${absolutize(raw).replace(" ", "%20")}[/img]", url = absolutize(raw).replace(" ", "%20"))
        }
        return listOf(SmileySet(id = "scan", name = "表情", dir = "", smileys = smileys))
    }

    private fun absolutize(src: String): String = Net.absolutize(src)
}
