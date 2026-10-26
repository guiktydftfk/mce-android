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
    /** 私信会话条目（v0.3.4） */
    data class PmItem(
        val touid: Int,
        val avatar: String?,
        /** "我对 X 说" / "X 对我说" */
        val title: String,
        val time: String?,
        val preview: String?,
        /** 未读数（span.num；0 表示已读） */
        val unread: Int,
        val url: String,
    )

    data class NoticeItem(
        val avatar: String?,
        val author: String?,
        val time: String?,
        val note: String?,
        val tid: Int?,
        val uid: Int? = null,
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

    /**
     * 私信列表（v0.3.4）：`ul#alist.pmbox li > a.pmtxt`
     * 结构：头像 + span.num（未读）+ div.pm_authi（span.name / span.time）+ div.pmmes（正文摘要）
     */
    suspend fun loadPm(page: Int): List<PmItem> = withContext(Dispatchers.IO) {
        val url = "$BASE/home.php?mod=space&do=pm&page=$page"
        val doc = Jsoup.parse(GenericPageClient.get(url), url)
        val items = doc.select("ul#alist li > a[href*=do=pm], ul.pmbox li a[href*=subop=view]").mapNotNull { a ->
            val href = a.attr("href").replace("&amp;", "&")
            val touid = Regex("""touid=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val avatar = a.selectFirst("img[src*=avatar.php]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)
            val name = a.selectFirst("div.pm_authi span.name")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val time = a.selectFirst("div.pm_authi span.time")?.text()?.trim()
            val preview = a.selectFirst("div.pmmes")?.text()?.trim()
            val unread = a.selectFirst("span.num")?.text()?.trim()?.toIntOrNull() ?: 0
            PmItem(
                touid = touid,
                avatar = avatar,
                title = name,
                time = time,
                preview = preview,
                unread = unread,
                url = Net.absolutize(href),
            )
        }
        // 同一个好友可能有多条往来消息：按 touid 合并（保留最新一条，未读数取该会话最大值），
        // 否则"我对他说"和"他对我说"会被 distinctBy 丢掉未读那条 → 新私信弹窗不触发。
        val merged = items.groupBy { it.touid }.values.map { group ->
            val first = group.first()
            first.copy(unread = group.maxOf { it.unread })
        }
        // v0.3.7：论坛手机模板在「APP 里读完私信」后仍会返回 span.num 未读（网页端同样如此，属论坛侧问题），
        // 这里叠加客户端本地已读记录：签名未变的会话未读归零，一旦来新消息签名变化会重新显示未读。
        cn.mce.starlight.core.PmReadStore.overlay(merged)
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
            val linkEl = noteBlock?.selectFirst("a[href*=ptid], a[href*=pid=]") ?: noteBlock?.selectFirst("a[href*=findpost]") ?: li.selectFirst("a[href*=ptid], a[href*=tid]")
            val tid = linkEl?.attr("href")?.let { href ->
                tidRegex.find(href)?.groupValues?.get(1)?.toIntOrNull()
            }
            val author = noteBlock?.selectFirst("a[href*=space]")?.text()?.trim()
            NoticeItem(avatar, author, time, note, tid)
        }
    }

    /** 私信聊天条目：incoming=true 表示对方发来 */
    data class PmMessage(
        val incoming: Boolean,
        val avatar: String?,
        val time: String?,
        val text: String,
    )

    /** 私信会话页（home.php?mod=space&do=pm&subop=view&touid=X） */
    data class PmChat(
        val touid: Int,
        val messages: List<PmMessage>,
        /** 发送表单 action（含 pmid/formhash 等参数） */
        val action: String,
        val formhash: String,
    )

    suspend fun loadPmChat(touid: Int, name: String? = null): PmChat = withContext(Dispatchers.IO) {
        val url = "$BASE/home.php?mod=space&do=pm&subop=view&touid=$touid"
        val doc = Jsoup.parse(GenericPageClient.get(url), url)
        val messages = doc.select("div.friend_msg, div.self_msg").mapNotNull { el ->
            val incoming = el.hasClass("friend_msg")
            val text = el.selectFirst("div.dialog_white, div.dialog_green")?.text()?.trim()
                ?: return@mapNotNull null
            val avatar = el.selectFirst("img[src*=avatar.php]")?.attr("src")
                ?.takeIf { it.isNotBlank() }?.let(::absolutize)
            val time = el.selectFirst("div.date")?.text()?.trim()
            PmMessage(incoming, avatar, time, text)
        }
        val form = doc.selectFirst("form#pmform")
        PmChat(
            touid = touid,
            messages = messages,
            action = form?.attr("action")?.replace("&amp;", "&")
                ?: "home.php?mod=spacecp&ac=pm&op=send&touid=$touid&pmsubmit=yes&mobile=2",
            formhash = form?.selectFirst("input[name=formhash]")?.attr("value")
                ?: Net.formhashOf(doc.outerHtml()).orEmpty(),
        )
    }

    private fun absolutize(src: String): String = Net.absolutize(src)
}
