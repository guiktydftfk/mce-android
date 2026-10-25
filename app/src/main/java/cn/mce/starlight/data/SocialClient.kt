package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 我的勋章 / 我的好友：专用解析，避免通用列表把导航等无关条目也抓进来。
 */
object SocialClient {
    private const val BASE = "https://mce.msi.cn"

    data class Medal(val name: String, val desc: String?, val image: String?)
    data class Friend(val uid: Int, val name: String, val avatar: String?, val extra: String?)

    /** 勋章墙：只保留带勋章图标的条目 */
    suspend fun loadMedals(): List<Medal> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/home.php?mod=space&do=medal"
            val html = GenericPageClient.get(url)
            cn.mce.starlight.core.DebugDump.dump("medals", html)
            val doc = Jsoup.parse(html, url)

            val medals = mutableListOf<Medal>()
            doc.select("li, div").forEach { box ->
                val img = box.selectFirst("img[src]") ?: return@forEach
                val src = img.attr("src")
                if (!src.contains("medal") && !src.contains("static/image/common/")) return@forEach
                val name = img.attr("alt").trim().ifBlank {
                    box.selectFirst("h3, h4, .xs2, dt")?.text()?.trim().orEmpty()
                }
                if (name.isBlank() || name.length > 20) return@forEach
                val desc = box.selectFirst("p, dd, .xg1")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() && it != name && it.length <= 60 }
                medals += Medal(name, desc, absolutize(src))
            }
            medals.distinctBy { it.name }
        }.getOrDefault(emptyList())
    }

    /** 好友列表：多入口兜底（不同模板路径不同），并把每个响应落盘便于排查 */
    suspend fun loadFriends(): List<Friend> = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "home.php?mod=space&do=friend&view=me",
            "home.php?mod=space&do=friend",
            "home.php?mod=space&do=friend&from=space",
            "home.php?mod=spacecp&ac=friend",
        )
        for (path in candidates) {
            val url = "$BASE/$path"
            var html: String? = null
            runCatching { GenericPageClient.get(url) }
                .onSuccess { html = it }
                .onFailure {
                    cn.mce.starlight.core.DebugDump.dump("friends_err_${path.hashCode()}", it.toString())
                }
            val pageHtml = html ?: continue
            html
            cn.mce.starlight.core.DebugDump.dump("friends_${path.hashCode()}", pageHtml)
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
            val uid = Regex("""uid=(\d+)""").find(link.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""uid=(\d+)""").find(avatarImg.attr("src"))
                    ?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = link.text().trim()
                .ifBlank { avatarImg.attr("alt").trim() }
                .ifBlank { li.selectFirst("h1, h2")?.text()?.trim().orEmpty() }
            if (name.isBlank() || name.length > 24) return@mapNotNull null
            val extra = li.selectFirst("p")?.text()?.trim()?.takeIf { it.isNotBlank() }
            Friend(uid, name, absolutize(avatarImg.attr("src")), extra)
        }.distinctBy { it.uid }
    }
    private fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }
}
