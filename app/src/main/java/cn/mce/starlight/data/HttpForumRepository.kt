package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Forum repository: all requests go through GenericPageClient
 * (single network stack, cookies saved on every redirect hop).
 */
class HttpForumRepository(
    private val baseHost: String = Net.BASE,
) : ForumRepository {

    companion object {
        const val MOBILE_UA = Net.MOBILE_UA

        /** v0.4.1：全板块最新主题缓存（companion 共享，启动预热线程与首页实例共用） */
        @Volatile
        private var latestAllCache: Pair<Long, List<ThreadSummary>>? = null

        /** 启动预热：后台拉一次全板块最新页填缓存，首页刷新直接命中 */
        suspend fun prewarmLatestAllBoards() {
            runCatching { HttpForumRepository().loadLatestAllBoards() }
        }
    }

    override suspend fun loadBoards(): List<ForumBoard> = Boards.all

    override suspend fun loadThreads(fid: Int, page: Int, filter: String?): ForumPage =
        withContext(Dispatchers.IO) {
            val extra = when (filter) {
                "lastpost" -> "&filter=lastpost&orderby=lastpost"
                "heat" -> "&filter=heat&orderby=heats"
                "digest" -> "&filter=digest&digest=1"
                else -> ""
            }
            val url = "$baseHost/forum.php?mod=forumdisplay&fid=$fid&page=$page$extra&t=${System.currentTimeMillis()}"
            MobileForumParser.parse(fetch(url), url, page)
        }

    override suspend fun loadGuide(view: String, page: Int): ForumPage =
        withContext(Dispatchers.IO) {
            val url = "$baseHost/forum.php?mod=guide&view=$view&page=$page&t=${System.currentTimeMillis()}"
            MobileForumParser.parse(fetch(url), url, page)
        }

    override suspend fun loadThreadDetail(tid: Int, page: Int, noCache: Boolean): ThreadDetail =
        withContext(Dispatchers.IO) {
            val cacheBust = if (noCache) "&t=${System.currentTimeMillis()}&mobile=2" else ""
            val url = "$baseHost/forum.php?mod=viewthread&tid=$tid&page=$page$cacheBust"
            val detail = ThreadDetailParser.parse(fetch(url), url, tid, page)
            // v0.4.1：版主管理条只在网页端「电脑版」完整渲染（手机模板经常没有）；
            // 已知当前账号是管理组时补抓一次电脑版页面解析管理操作，普通用户不会多发请求。
            // v0.4.2：评分/打赏与版主管理条都只在「电脑版」页面渲染（手机模板没有），
            // 登录后在第 1 页补抓一次电脑版页面，一次请求同时解析两者。
            val needDesktop = page == 1 && cn.mce.starlight.core.SessionStore.isLoggedIn
            if (needDesktop) {
                runCatching {
                    val desktopUrl = "$baseHost/forum.php?mod=viewthread&tid=$tid&mobile=no"
                    val desktopHtml = GenericPageClient.get(
                        desktopUrl,
                        userAgent = Net.DESKTOP_UA,
                        isolateCookies = true,
                        retryOnFailure = false,
                    )
                    // v0.4.2：不再依赖「先打开过我的页拿到 staff 标记」——
                    // 管理组页面本来就有 topicadmin 链接、普通用户页面没有，
                    // 直接解析即可：有链接 → 显示「管理」，没有 → 隐藏。
                    // （之前用 SessionStore.staff 当开关，冷启动直接进帖子的管理组会看不到管理按钮）
                    val mods = ThreadDetailParser.parseModActionsFromHtml(desktopHtml, desktopUrl)
                    detail.copy(
                        modActions = mods.ifEmpty { detail.modActions },
                        ratings = ThreadDetailParser.parseRatingsFromHtml(desktopHtml, desktopUrl),
                    )
                }.getOrNull() ?: detail
            } else {
                detail
            }
        }

    /**
     * v0.4.1：并行拉取**所有板块**的最新一页主题，合并去重后按发布时间倒序。
     * 首页「最新」用它兜底导读延迟收录（用户要求覆盖全部板块）。
     * - 并发 6，避免一次打满论坛导致限流；
     * - 结果带 120 秒内存缓存：首次刷新并行拉全板块，后续刷新直接命中缓存（明显更快）。
     */
    /**
     * v0.4.3：只取「已就绪的全板块缓存」，**绝不等待网络**。
     * 首页首屏用它：缓存没热就跳过全板块合并（返回 null），让首屏只等导读；
     * 同时调用方可以后台触发 [prewarmLatestAllBoards] 补缓存，下次刷新再合并。
     *
     * 背景：0.4.2 把全板块改成「按分组串行」后，冷缓存时首页第一页要等 34 个板块
     * 分 4 组串行拉完（弱网 5s+），用户感知就是「打开应用一直在加载」。
     */
    fun latestAllBoardsIfCached(): List<ThreadSummary>? {
        val cached = latestAllCache
        return if (cached != null && System.currentTimeMillis() - cached.first < 300_000L) cached.second else null
    }

    suspend fun loadLatestAllBoards(): List<ThreadSummary> = withContext(Dispatchers.IO) {
        val cached = latestAllCache
        if (cached != null && System.currentTimeMillis() - cached.first < 300_000L) {
            return@withContext cached.second
        }
        // v0.4.1：按分组优先级顺序拉取：全国交流 → 项目和活动 → 城市板块 → 星光大厅（最后）
        // 组内仍并发（并发 6），组与组之间串行，保证优先级高的板块先返回。
        val all = coroutineScope {
            val collected = mutableListOf<ThreadSummary>()
            val semaphore = Semaphore(6)
            Boards.groups.forEach { (_, boards) ->
                val fids = boards.map { it.fid }.filter { it > 0 }.distinct()
                if (fids.isEmpty()) return@forEach
                val partial = fids.map { fid ->
                    async {
                        semaphore.withPermit {
                            runCatching { loadThreads(fid, 1).threads }.getOrDefault(emptyList())
                        }
                    }
                }.awaitAll().flatten()
                collected += partial
            }
            collected
        }
        val merged = all.distinctBy { it.id }
            .sortedWith(
                compareByDescending { cn.mce.starlight.core.FeedTime.parseApproxMs(it.metaText) ?: 0L },
            )
        latestAllCache = System.currentTimeMillis() to merged
        merged
    }

    private fun fetch(url: String): String {
        val html = GenericPageClient.get(url)
        if (GenericPageClient.isBlockedPage(html)) {
            throw IOException("\u8bbf\u95ee\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5")
        }
        if (GenericPageClient.isLoginPage(html)) {
            throw IOException("\u9700\u8981\u767b\u5f55\uff0c\u8bf7\u5148\u767b\u5f55\u540e\u518d\u8bd5")
        }
        return html
    }
}
