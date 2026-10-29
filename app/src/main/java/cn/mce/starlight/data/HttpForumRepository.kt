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
            if (detail.modActions.isEmpty() && cn.mce.starlight.core.SessionStore.staff != null) {
                runCatching {
                    val desktopUrl = "$baseHost/forum.php?mod=viewthread&tid=$tid&mobile=no"
                    val desktopHtml = GenericPageClient.get(
                        desktopUrl,
                        userAgent = Net.DESKTOP_UA,
                        isolateCookies = true,
                        retryOnFailure = false,
                    )
                    ThreadDetailParser.parseModActionsFromHtml(desktopHtml, desktopUrl)
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { detail.copy(modActions = it) } ?: detail
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
    suspend fun loadLatestAllBoards(): List<ThreadSummary> = withContext(Dispatchers.IO) {
        val cached = latestAllCache
        if (cached != null && System.currentTimeMillis() - cached.first < 300_000L) {
            return@withContext cached.second
        }
        val fids = Boards.all.map { it.fid }.filter { it > 0 }.distinct()
        val semaphore = Semaphore(6)
        val all = coroutineScope {
            fids.map { fid ->
                async {
                    semaphore.withPermit {
                        runCatching { loadThreads(fid, 1).threads }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
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
