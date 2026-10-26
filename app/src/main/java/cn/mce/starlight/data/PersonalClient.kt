package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 个人中心列表（我的主题 / 我的回复 / 我的收藏 / 消息提醒）。
 * 选择器参考鸿蒙版 DiscuzParser 的实测结构。
 *
 * v0.3.3 批 3：循环内的 `Regex(...)` 全部提升为常量；
 * 收藏/提醒不再对每个 `li` 做 `li.html()` 全量序列化，改用 `:has()` 选择器一次筛出目标条目。
 */
object PersonalClient {
    private const val BASE = Net.BASE

    private val tidRegex = Regex("""(?:ptid|tid)=(\d+)""")
    private val viewTidRegex = Regex("""tid=(\d+)""")
    private val dateRegex = Regex("""\d{4}-\d{1,2}-\d{1,2}[^\s<]{0,10}""")
    private val shortTextRegex = Regex("""[^<]{4,30}""")

    data class ReplyItem(val tid: Int, val title: String, val content: String, val time: String?)
    data class FavoriteItem(val tid: Int, val title: String, val forumName: String?)
    data class NoticeItem(
        val avatar: String?,
        val author: String?,
        val time: String?,
        val note: String?,
        val tid: Int?,
    )

    /** 我的主题（沿用帖子列表模板） */
    suspend fun loadMyThreads(page: Int): ForumPage = withContext(Dispatchers.IO) {
        val url = "$BASE/home.php?mod=space&do=thread&view=me&page=$page"
        MobileForumParser.parse(GenericPageClient.get(url), url, page)
    }

    /** 我的回复：li.home_thread_reply */
    suspend fun loadMyReplies(page: Int): List<ReplyItem> = withContext(Dispatchers.IO) {
        val url = "$BASE/home.php?mod=space&do=thread&view=me&from=space&type=reply&page=$page"
        val doc = Jsoup.parse(GenericPageClient.get(url), url)
        doc.select("li.home_thread_reply, li[class*=home_thread_reply]").mapNotNull { li ->
            val link = li.selectFirst("div.mbm a, a[href*=ptid], a[href*=tid]") ?: return@mapNotNull null
            val tid = tidRegex.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val content = li.selectFirst("p.my_reply, p")?.text()?.trim().orEmpty()
            val time = dateRegex.find(li.text())?.value
            ReplyItem(tid, link.text().trim(), content, time)
        }
    }

    /** 我的收藏：带 favid= 的条目 */
    suspend fun loadFavorites(page: Int): List<FavoriteItem> = withContext(Dispatchers.IO) {
        val url = "$BASE/home.php?mod=space&do=favorite&page=$page"
        val doc = Jsoup.parse(GenericPageClient.get(url), url)
        // 先用选择器筛出收藏条目（旧实现遍历所有 li 并逐个 li.html() 只为一次 contains）
        doc.select("li:has(a[href*=favid=])").mapNotNull { li ->
            val href = li.selectFirst("a[href*=tid]")?.attr("href").orEmpty()
            val tid = viewTidRegex.find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val title = li.selectFirst("p a, a[href*=viewthread]")?.text()?.trim()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val forum = li.select("a").map { it.text().trim() }
                .firstOrNull { it.length in 2..12 && it != title }
            FavoriteItem(tid, title, forum)
        }.distinctBy { it.tid }
    }

    /** 消息提醒：li.notice_item */
    suspend fun loadNotices(page: Int): List<NoticeItem> = withContext(Dispatchers.IO) {
        val url = "$BASE/home.php?mod=space&do=notice&page=$page"
        val doc = Jsoup.parse(GenericPageClient.get(url), url)
        doc.select("li.notice_item, li[class*=notice_item]").map { li ->
            val avatar = li.selectFirst("img[src*=avatar.php]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)
            val time = li.selectFirst("p.notice_time")?.text()
                ?.let { shortTextRegex.find(it)?.value?.trim() }
            val noteBlock = li.selectFirst("div.notice_note")
            val note = noteBlock?.text()?.trim()
            // 旧实现为了拿 fallback 链接会做一次整块 li.html()（每条的 DOM 序列化）
            val linkEl = noteBlock?.selectFirst("a[href]") ?: li.selectFirst("a[href*=ptid], a[href*=tid]")
            val tid = linkEl?.attr("href")?.let { href ->
                tidRegex.find(href)?.groupValues?.get(1)?.toIntOrNull()
            }
            val author = noteBlock?.selectFirst("a[href*=space]")?.text()?.trim()
            NoticeItem(avatar, author, time, note, tid)
        }
    }

    private fun absolutize(src: String): String = Net.absolutize(src)
}
