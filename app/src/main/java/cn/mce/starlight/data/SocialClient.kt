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
        /** v0.4.1：条件已满足、可点「领取」（勋章中心里显示「领取」链接） */
        val claimable: Boolean = false,
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
            // 已获得（我的勋章页）
            val earned = loadEarnedMedals()
            // 全量（勋章中心，含未获得 / 可领取）
            val center = loadMedalCenter()
            val merged = LinkedHashMap<String, Medal>()
            earned.forEach { merged[it.name] = it }
            val ownedNames = earned.mapTo(HashSet()) { it.name }
            center.forEach { c ->
                val e = merged[c.name]
                merged[c.name] = if (e != null) {
                    // 已拥有 → 一律点亮（即便勋章中心还挂着“领取”按钮）
                    e.copy(unlocked = true, claimable = false, progress = null, desc = e.desc ?: c.desc)
                } else {
                    c.copy(unlocked = c.unlocked || c.name in ownedNames, claimable = c.claimable && c.name !in ownedNames)
                }
            }
            merged.values.toList()
        }.getOrDefault(emptyList())
    }

    /**
     * 我拥有的勋章 —— 取自「个人资料页」的荣誉勋章区块：
     *   <li><span>荣誉勋章</span><a class="medal_list"><img alt="星宝勋章"><img alt="暑期训练营"></a></li>
     * （手机模板的 home.php?mod=space&do=medal 实测会回落到资料页，所以直接解析资料页最稳）
     */
    private fun loadEarnedMedals(): List<Medal> {
        val urls = listOf(
            "$BASE/home.php?mod=space&do=profile",
            "$BASE/home.php?mod=space&do=medal",
        )
        for (u in urls) {
            val html = runCatching { GenericPageClient.get(u) }.getOrNull() ?: continue
            val doc = Jsoup.parse(html, u)
            val list = doc.select("a.medal_list img[src], img[src*=勋章]")
                .mapNotNull { img ->
                    val name = img.attr("alt").trim()
                    if (name.isBlank() || name.length > 20) return@mapNotNull null
                    Medal(name, null, absolutize(img.attr("src")), unlocked = true)
                }.distinctBy { it.name }
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    /**
     * 勋章中心（桌面版 home.php?mod=medal&mobile=no）：
     *   <li><div id="medal_4" class="mg_img"><img alt="超级版主"></div>
     *       <p class="xw1">超级版主</p><p> 已领取|领取|&nbsp; </p></li>
     * 空 <p> = 未获得；有「领取」链接 = 条件已满足待领取；「已领取」= 已点亮。
     */
    private fun loadMedalCenter(): List<Medal> {
        val url = "$BASE/home.php?mod=medal&mobile=no"
        val html = runCatching {
            GenericPageClient.get(url, userAgent = Net.DESKTOP_UA, isolateCookies = true)
        }.getOrNull() ?: return emptyList()
        val doc = Jsoup.parse(html, url)
        return doc.select("li:has(div.mg_img), li:has(div[id^=medal_])").mapNotNull { li ->
            val img = li.selectFirst("div.mg_img img, img[src*=medal]") ?: return@mapNotNull null
            val name = li.selectFirst("p.xw1")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: img.attr("alt").trim()
            if (name.isBlank() || name.length > 20) return@mapNotNull null
            val statusText = li.select("p").map { it.text().trim() }.lastOrNull { it.length <= 8 && it != name }.orEmpty()
            val unlocked = statusText.contains("已领取") || statusText.contains("已获得")
            val claimable = !unlocked && li.selectFirst("a[href*=action=confirm]") != null
            val kind = li.selectFirst("div.tip p.mtn")?.text()?.trim()
            val desc = li.selectFirst("div.tip p:not(.mtn)")?.text()?.trim()
                ?.takeIf { it.isNotBlank() && it != name && it.length <= 60 }
            val progress = when {
                unlocked -> null
                claimable -> "可领取"
                kind != null -> kind
                else -> null
            }
            Medal(
                name = name,
                desc = desc,
                image = absolutize(img.attr("src")),
                unlocked = unlocked,
                progress = progress,
                claimable = claimable,
            )
        }.distinctBy { it.name }
    }

    /** v0.4.1：桌面版勋章中心（含未获得的全部勋章） */
    private fun loadMedalsDesktop(): List<Medal> {
        val urls = listOf(
            "$BASE/home.php?mod=space&do=medal&mobile=no",
            "$BASE/home.php?mod=medal&mobile=no",
        )
        for ((idx, u) in urls.withIndex()) {
            val html = runCatching {
                GenericPageClient.get(u, userAgent = Net.DESKTOP_UA, isolateCookies = true)
            }.getOrNull() ?: continue
            cn.mce.starlight.core.DebugDump.dump("medals_desktop_$idx", html)
            val doc = Jsoup.parse(html, u)
            val boxes = doc.select("li:has(img), div:has(img)")
            val list = boxes.mapNotNull { box ->
                val img = box.selectFirst("img[src]") ?: return@mapNotNull null
                val src = img.attr("src")
                if (!src.contains("medal") && !src.contains("static/image/common/")) return@mapNotNull null
                val name = img.attr("alt").trim().ifBlank {
                    box.selectFirst("h3, h4, dt, .xs2, strong")?.text()?.trim().orEmpty()
                }
                if (name.isBlank() || name.length > 20) return@mapNotNull null
                val text = box.text()
                val locked = text.contains("未获得") || text.contains("未领取") || text.contains("未点亮") ||
                    text.contains("去申请") || text.contains("已过期")
                val progress = Regex("""(\d+\s*/\s*\d+|\d+\s*%)""").find(text)?.value?.trim()
                val desc = box.selectFirst("p, dd, .xg1")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() && it != name && it.length <= 60 }
                Medal(name, desc, Net.absolutize(src), unlocked = !locked, progress = progress)
            }.distinctBy { it.name }
            if (list.isNotEmpty()) return list
        }
        return emptyList()
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
            cn.mce.starlight.core.DebugDump.dump("friends_mobile", pageHtml)
            val list = runCatching { parseFriendsHtml(pageHtml, url) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                // v0.4.1：好友列表不带在线状态 —— 用「在线好友」页判断（不在其中 = 离线）
                val onlineUids = runCatching { loadOnlineFriendUids() }.getOrDefault(emptySet())
                return@withContext list.map { f -> f.copy(online = f.uid in onlineUids) }
            }
        }
        emptyList()
    }

    /**
     * v0.4.1：在线好友 = 单独页面（好友列表本身不显示在线状态）。
     *   home.php?mod=space&do=friend&view=online&type=friend
     * 解析该页出现的 uid 集合；总好友列表里不在集合中的即视为离线。
     */
    private fun loadOnlineFriendUids(): Set<Int> {
        val urls = listOf(
            "$BASE/home.php?mod=space&do=friend&view=online&type=friend",
            "$BASE/home.php?mod=space&do=friend&view=online&type=friend&mobile=no",
        )
        for (u in urls) {
            val html = runCatching {
                GenericPageClient.get(u, userAgent = if (u.contains("mobile=no")) Net.DESKTOP_UA else null, isolateCookies = true)
            }.getOrNull() ?: continue
            val doc = Jsoup.parse(html, u)
            val uids = doc.select("a[href*=uid=]").mapNotNull { a ->
                uidRegex.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            }.toSet()
            if (uids.isNotEmpty()) return uids
        }
        return emptySet()
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
