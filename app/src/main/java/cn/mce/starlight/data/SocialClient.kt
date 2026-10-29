package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 我的勋章 / 我的好友：专用解析，避免通用列表把导航等无关条目也抓进来。
 */
object SocialClient {
    private const val BASE = Net.BASE

    private val uidRegex = Regex("""uid=(\d+)""")

    /** v0.4.1：勋章墙 —— 补全未解锁勋章 + 进度文案 */
    data class Medal(
        val name: String,
        val desc: String?,
        val image: String?,
        val unlocked: Boolean = true,
        val progress: String? = null,
    )

    /** v0.4.1：好友增加在线状态（true=在线 / false=离线 / null=未知） */
    data class Friend(
        val uid: Int,
        val name: String,
        val avatar: String?,
        val extra: String?,
        val online: Boolean? = null,
    )

    /** 勋章墙：只保留带勋章图标的条目 */
    suspend fun loadMedals(): List<Medal> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/home.php?mod=space&do=medal"
            val html = GenericPageClient.get(url)
            val doc = Jsoup.parse(html, url)

            // v0.3.3 批 3：旧实现 `doc.select("li, div")` 遍历整页并集（嵌套盒重复命中、近似 O(节点×深度)），
            // 现在用 :has() 一次筛出"含勋章图标的容器"，再按名称去重。
            val boxes = doc.select(
                "li:has(img[src*=medal]), div:has(img[src*=medal]), " +
                    "li:has(img[src*=static/image/common/]), " +
                    "div:has(img[src*=static/image/common/])",
            )
            boxes.mapNotNull { box ->
                val img = box.selectFirst("img[src]") ?: return@mapNotNull null
                val src = img.attr("src")
                if (!src.contains("medal") && !src.contains("static/image/common/")) return@mapNotNull null
                val name = img.attr("alt").trim().ifBlank {
                    box.selectFirst("h3, h4, .xs2, dt")?.text()?.trim().orEmpty()
                }
                if (name.isBlank() || name.length > 20) return@mapNotNull null
                val text = box.text()
                val locked = text.contains("未获得") || text.contains("未解锁") ||
                    text.contains("去申请") || box.classNames().any { it.contains("lock", true) }
                val desc = box.selectFirst("p, dd, .xg1")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() && it != name && it.length <= 60 }
                val progress = Regex("""(\d+\s*/\s*\d+|\d+\s*%)""").find(text)?.value?.trim()
                Medal(name, desc, absolutize(src), unlocked = !locked, progress = progress)
            }.distinctBy { it.name }
        }.getOrDefault(emptyList())
    }

    /** 好友列表：多入口兜底（不同模板路径不同） */
    suspend fun loadFriends(): List<Friend> = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "home.php?mod=space&do=friend&view=me",
            "home.php?mod=space&do=friend",
            "home.php?mod=space&do=friend&from=space",
            "home.php?mod=spacecp&ac=friend",
        )
        for (path in candidates) {
            val url = "$BASE/$path"
            val pageHtml = runCatching { GenericPageClient.get(url) }.getOrNull() ?: continue
            val list = runCatching { parseFriendsHtml(pageHtml, url) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) return@withContext list
        }
        emptyList()
    }

    /**
     * 实测结构（touch 模板）：
     *   <ul id="alist" class="pagelist friend">
     *     <li><div class="locker" id="locker_2499">
     *       <a class="friendavatar"><img class="user_avatar" src="avatar.php?uid=2499"></a>
     *       <h1><a href="...uid=2499&do=profile">Brian</a></h1>
     *       <p>最近主题/签名</p>
     *       <div class="friend_mg">…消息/招呼/备注/分组/删除…</div>
     */
    private fun parseFriendsHtml(html: String, url: String): List<Friend> {
        val doc = Jsoup.parse(html, url)

        val items = doc.select("ul#alist > li")
            .ifEmpty { doc.select("li:has(a.friendavatar), li:has(img.user_avatar)") }

        return items.mapNotNull { li ->
            val avatarImg = li.selectFirst("img.user_avatar, a.friendavatar img")
                ?: return@mapNotNull null
            val link = li.selectFirst("h1 a") ?: li.selectFirst("a.friendavatar")
                ?: return@mapNotNull null
            val uid = uidRegex.find(link.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: uidRegex.find(avatarImg.attr("src"))
                    ?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = link.text().trim()
                .ifBlank { avatarImg.attr("alt").trim() }
                .ifBlank { li.selectFirst("h1, h2")?.text()?.trim().orEmpty() }
            if (name.isBlank() || name.length > 24) return@mapNotNull null
            val extra = li.selectFirst("p")?.text()?.trim()?.takeIf { it.isNotBlank() }
            // v0.4.1：在线状态（em.online / .online / 文本「在线」「离线」）
            val textAll = li.text()
            val online = when {
                li.selectFirst(".online, em.online, .user_online, i[class*=online]") != null -> true
                textAll.contains("在线") -> true
                textAll.contains("离线") -> false
                else -> null
            }
            Friend(uid, name, absolutize(avatarImg.attr("src")), extra, online)
        }.distinctBy { it.uid }
    }
    private fun absolutize(src: String): String = Net.absolutize(src)
}
