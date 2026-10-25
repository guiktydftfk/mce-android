package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 个人中心列表（我的主题 / 我的回复 / 我的收藏 / 消息提醒）。
 * 选择器参考鸿蒙版 DiscuzParser 的实测结构。
 */
object PersonalClient {
    private const val BASE = "https://mce.msi.cn"

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
            val href = link.attr("href")
            val tid = Regex("""(?:ptid|tid)=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val content = li.selectFirst("p.my_reply, p")?.text()?.trim().orEmpty()
            val time = Regex("""\d{4}-\d{1,2}-\d{1,2}[^\s<]{0,10}""").find(li.text())?.value
            ReplyItem(tid, link.text().trim(), content, time)
        }
    }

    /** 我的收藏：li with favid= */
    suspend fun loadFavorites(page: Int): List<FavoriteItem> = withContext(Dispatchers.IO) {
        val url = "$BASE/home.php?mod=space&do=favorite&page=$page"
        val doc = Jsoup.parse(GenericPageClient.get(url), url)
        doc.select("li").mapNotNull { li ->
            val html = li.html()
            if (!html.contains("favid=")) return@mapNotNull null
            val href = li.selectFirst("a[href*=tid]")?.attr("href").orEmpty()
            val tid = Regex("""tid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
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
                ?.let { Regex("""[^<]{4,30}""").find(it)?.value?.trim() }
            val noteBlock = li.selectFirst("div.notice_note")
            val note = noteBlock?.text()?.trim()
            val liHtml = li.html()
            val href = noteBlock?.selectFirst("a[href]")?.attr("href").orEmpty()
            val tid = Regex("""(?:ptid|tid)=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(?:ptid|tid)=(\d+)""").find(liHtml)?.groupValues?.get(1)?.toIntOrNull()
            val author = noteBlock?.selectFirst("a[href*=space]")?.text()?.trim()
            NoticeItem(avatar, author, time, note, tid)
        }
    }

    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }
}
