package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 搜索：帖子 / 用户 / 高级条件。
 *
 * 网页版表单：POST search.php?searchsubmit=yes
 *   mod=forum           帖子搜索
 *   mod=user            用户搜索
 *   srchtxt             关键词
 *   searchuser          作者
 *   srchfid             版块
 *   srchfrom            时间（最近 N 天）
 *   orderby / ascdesc   排序
 */
object SearchClient {
    private const val BASE = "https://mce.msi.cn"

    data class Hit(val tid: Int, val title: String)
    data class UserHit(val uid: Int, val name: String, val avatarUrl: String?)
    data class Advanced(
        val keyword: String = "",
        val author: String = "",
        val fid: Int? = null,
        val days: Int? = null,
        val orderBy: String = "lastpost",
    )

    private fun formhash(): String {
        val html = runCatching { GenericPageClient.get("$BASE/forum.php") }.getOrDefault("")
        return Regex("""formhash=([a-z0-9]+)""").find(html)?.groupValues?.get(1).orEmpty()
    }

    private fun post(fields: List<Pair<String, String>>): String {
        val body = fields.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }
        return GenericPageClient.httpPost("$BASE/search.php?searchsubmit=yes", body, null)
    }

    suspend fun searchThreads(query: String): List<Hit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val html = post(
            listOf(
                "formhash" to formhash(),
                "mod" to "forum",
                "srchtxt" to query,
                "searchsubmit" to "yes",
            ),
        )
        parseThreads(html)
    }

    suspend fun advancedSearch(advanced: Advanced): List<Hit> = withContext(Dispatchers.IO) {
        val fields = mutableListOf(
            "formhash" to formhash(),
            "mod" to "forum",
            "searchsubmit" to "yes",
            "srchtxt" to advanced.keyword,
            "orderby" to advanced.orderBy,
            "ascdesc" to "desc",
        )
        if (advanced.author.isNotBlank()) fields += "searchuser" to advanced.author
        advanced.fid?.let { fields += "srchfid" to it.toString() }
        advanced.days?.let { fields += "srchfrom" to it.toString() }
        parseThreads(post(fields))
    }

    suspend fun searchUsers(query: String): List<UserHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val html = post(
            listOf(
                "formhash" to formhash(),
                "mod" to "user",
                "srchtxt" to query,
                "searchsubmit" to "yes",
            ),
        )
        parseUsers(html)
    }

    private fun parseThreads(html: String): List<Hit> {
        val doc = Jsoup.parse(html, BASE)
        val fromList = MobileForumParser.parse(html, BASE, 1).threads.map { Hit(it.id, it.title) }
        if (fromList.isNotEmpty()) return fromList

        return doc.select("a[href*=viewthread]").mapNotNull { a ->
            val tid = Regex("""tid=(\d+)""").find(a.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val title = a.text().trim()
            if (title.length < 4) null else Hit(tid, title)
        }.distinctBy { it.tid }.take(50)
    }

    private fun parseUsers(html: String): List<UserHit> {
        val doc = Jsoup.parse(html, BASE)
        return doc.select("a[href*=home.php?mod=space]").mapNotNull { a ->
            val href = a.attr("href")
            val uid = Regex("""uid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = a.text().trim()
            if (name.isBlank() || name.length > 24) return@mapNotNull null
            val container = a.parents().firstOrNull { it.selectFirst("img[src*=avatar.php]") != null }
            val avatar = container?.selectFirst("img[src*=avatar.php]")?.attr("src")
                ?.let { absolutize(it) }
            UserHit(uid, name, avatar)
        }.distinctBy { it.uid }.take(50)
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }
}
