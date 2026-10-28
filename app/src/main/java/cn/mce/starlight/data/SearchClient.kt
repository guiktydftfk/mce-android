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
    private const val BASE = Net.BASE

    private val tidRegex = Regex("""tid=(\d+)""")
    private val uidRegex = Regex("""uid=(\d+)""")

    data class Hit(val tid: Int, val title: String)
    data class UserHit(val uid: Int, val name: String, val avatarUrl: String?)
    data class Advanced(
        val keyword: String = "",
        val author: String = "",
        val fid: Int? = null,
        val days: Int? = null,
        val orderBy: String = "lastpost",
    )

    private suspend fun formhash(): String = FormhashStore.get()
    /**
     * v0.4.1：支持电脑版模板回退（mobile=no + 桌面 UA）。
     * 手机模板的帖子搜索偶尔只回「标题命中」，电脑版搜索能覆盖到正文。
     */
    private fun post(
        fields: List<Pair<String, String>>,
        mobile: Boolean = true,
    ): String {
        val body = fields.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }
        val url = if (mobile) {
            "$BASE/search.php?searchsubmit=yes"
        } else {
            "$BASE/search.php?searchsubmit=yes&mobile=no"
        }
        return GenericPageClient.httpPost(
            url,
            body,
            null,
            if (mobile) null else GenericPageClient.DESKTOP_UA,
        )
    }

    /**
     * v0.4.1：帖子关键词搜索 —— 关键词同时匹配**标题与正文**（论坛全文搜索）。
     * 手机模板若只回标题命中（结果为空/不全），回退电脑版模板再搜一次。
     */
    suspend fun searchThreads(query: String): List<Hit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val fields = listOf(
            "formhash" to formhash(),
            "mod" to "forum",
            "srchtxt" to query,
            "searchsubmit" to "yes",
        )
        val mobileHits = runCatching { parseThreads(post(fields, mobile = true)) }.getOrDefault(emptyList())
        if (mobileHits.isNotEmpty()) return@withContext mobileHits
        runCatching { parseThreads(post(fields, mobile = false)) }.getOrDefault(emptyList())
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
        var lastError: Throwable? = null

        // 1) 手机模板（正常路径）
        val mobileHits = runCatching {
            parseUsers(
                post(
                    listOf(
                        "formhash" to formhash(),
                        "mod" to "user",
                        "srchtxt" to query,
                        "searchsubmit" to "yes",
                    ),
                ),
            )
        }.onFailure { lastError = it }.getOrDefault(emptyList())
        if (mobileHits.isNotEmpty()) return@withContext mobileHits

        // 2) 回退：强制电脑版模板（`mobile=no`）。
        // 论坛手机模板缺失 mytpl/menubottom.htm，用户搜索页会 503；电脑版模板正常，
        // 所以这里换 UA + mobile=no 再取一次，解析 ul.buddy 用户列表。
        val desktop = runCatching { searchUsersDesktop(query) }.onFailure { lastError = it }.getOrNull()
        if (desktop != null) return@withContext desktop

        throw (lastError ?: java.io.IOException("\u7528\u6237\u641c\u7d22\u5931\u8d25"))
    }

    /** 电脑版模板的用户搜索：GET home.php?mod=spacecp&ac=search&username=X&searchsubmit=yes&mobile=no */
    private fun searchUsersDesktop(query: String): List<UserHit> {
        val url = "$BASE/home.php?mod=spacecp&ac=search&searchsubmit=yes&mobile=no&username=" +
            URLEncoder.encode(query, "UTF-8")
        // 关键：isolateCookies=true —— 服务端会把 mobile=no 写成 Cookie，
        // 一旦落盘（如 l2ND_2132_mobile=no），之后所有页面都会被当成电脑版模板，
        // 移动版选择器全部解析为空（实测踩过）。这里用完即弃。
        val html = GenericPageClient.get(
            url,
            userAgent = GenericPageClient.DESKTOP_UA,
            isolateCookies = true,
        )
        return parseDesktopUsers(html)
    }

    /** 解析电脑版"查找好友"结果页（真实抓包结构）：ul.buddy > li > .avt img + h4 a + p.maxh */
    fun parseDesktopUsers(html: String): List<UserHit> {
        val doc = Jsoup.parse(html, BASE)
        val boxes = doc.select("ul.buddy > li")
            .ifEmpty { doc.select("li:has(img[src*=avatar.php]):has(a[href*=home.php?mod=space&uid=])") }
        return boxes.mapNotNull { li ->
            val link = li.selectFirst("h4 a")
                ?: li.selectFirst("a[href*=home.php?mod=space&uid=]")
                ?: return@mapNotNull null
            val uid = uidRegex.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = link.text().trim()
            if (name.isBlank() || name.length > 24) return@mapNotNull null
            val avatar = li.selectFirst("img[src*=avatar.php]")?.attr("src")?.let(::absolutize)
            UserHit(uid, name, avatar)
        }.distinctBy { it.uid }.take(100)
    }

    private fun parseThreads(html: String): List<Hit> {
        // v0.3.3 批 3：只解析一次 DOM（旧实现先 Jsoup.parse，又调 MobileForumParser.parse 内部再 parse 一遍）
        val doc = Jsoup.parse(html, BASE)
        val fromList = MobileForumParser.parse(doc, 1).threads.map { Hit(it.id, it.title) }
        if (fromList.isNotEmpty()) return fromList

        return doc.select("a[href*=viewthread]").mapNotNull { a ->
            val tid = tidRegex.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val title = a.text().trim()
            if (title.length < 4) null else Hit(tid, title)
        }.distinctBy { it.tid }.take(50)
    }

    private fun parseUsers(html: String): List<UserHit> {
        val doc = Jsoup.parse(html, BASE)
        // v0.3.3 批 3：先用容器选择器一次筛出"含头像的条目"，
        // 不再对每个锚点 `parents()` 回溯全部祖先（50 个链接 × 深 10 层子树选择）
        val boxes = doc.select(
            "li:has(img[src*=avatar.php]), div.u:has(img[src*=avatar.php]), " +
                "div.locker:has(img[src*=avatar.php])",
        )
        if (boxes.isNotEmpty()) {
            return boxes.mapNotNull { box ->
                val a = box.selectFirst("a[href*=home.php?mod=space]") ?: return@mapNotNull null
                val uid = uidRegex.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@mapNotNull null
                val name = a.text().trim()
                if (name.isBlank() || name.length > 24) return@mapNotNull null
                val avatar = box.selectFirst("img[src*=avatar.php]")?.attr("src")?.let(::absolutize)
                UserHit(uid, name, avatar)
            }.distinctBy { it.uid }.take(50)
        }

        // 兜底：模板不同时退回锚点扫描（祖先只回溯最近 4 层）
        return doc.select("a[href*=home.php?mod=space]").mapNotNull { a ->
            val href = a.attr("href")
            val uid = uidRegex.find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = a.text().trim()
            if (name.isBlank() || name.length > 24) return@mapNotNull null
            val container = a.parents().take(4).firstOrNull { it.selectFirst("img[src*=avatar.php]") != null }
            val avatar = container?.selectFirst("img[src*=avatar.php]")?.attr("src")?.let(::absolutize)
            UserHit(uid, name, avatar)
        }.distinctBy { it.uid }.take(50)
    }

    private fun absolutize(src: String): String = Net.absolutize(src)
}
