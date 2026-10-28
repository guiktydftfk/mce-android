package cn.mce.starlight.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 帖子首图（列表卡片缩略图）。
 *
 * 经验教训（都踩过）：
 *  1. 只扫 div.message → 相册/附件图在 .attach_list 里，取不到；
 *  2. 失败结果永久缓存 → 一次抖动后该帖永远没缩略图；
 *  3. 失败不缓存 + 每张卡片整页拉取 → **请求风暴**，触发论坛频率限制，
 *     帖子页返回拦截页，表现为"所有帖子都解析不到楼层"。
 *
 * v0.3.3（批 1）：
 *  - 同一 tid 单飞：并发调用者用 [CompletableDeferred] 等同一个结果（旧实现直接返回 null → 丢图且不重试）
 *  - 负缓存写时清理过期项并限容；正缓存改带容量上限的 LRU（访问序）
 *  - 首图用 [ThreadDetailParser.firstImageUrl] 轻量提取，不再跑整页楼层解析
 *  - 频率限制判定复用 [GenericPageClient.isBlockedPage]（去掉重复实现）
 *  - 登录/退出时 [clear]（避免"登录了仍 10 分钟无图"）
 */
object ThreadImageCache {
    private const val NEGATIVE_TTL_MS = 10 * 60 * 1000L
    private const val POSITIVE_CAP = 512
    private const val NEGATIVE_CAP = 256
    private const val CONCURRENCY = 2

    /** 访问序 LRU，避免无界增长 */
    private val positive = object : LinkedHashMap<Int, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean =
            size > POSITIVE_CAP
    }
    private val negative = mutableMapOf<Int, Long>()
    private val semaphore = Semaphore(CONCURRENCY)
    private val inflight = mutableMapOf<Int, CompletableDeferred<String?>>()

    /** v0.4.0：内存紧张时清空缩略图正/负缓存（磁盘 ThumbCache 保留） */
    fun trimMemory() {
        synchronized(positive) { positive.clear() }
        synchronized(negative) { negative.clear() }
    }

    suspend fun firstImage(tid: Int): String? = withContext(Dispatchers.IO) {
        synchronized(positive) { positive[tid] }?.let { return@withContext it }

        ThumbCache.get(tid)?.let { url ->
            synchronized(positive) { positive[tid] = url }
            return@withContext url
        }

        val negativeAt = synchronized(negative) { negative[tid] }
        if (negativeAt != null) {
            if (System.currentTimeMillis() - negativeAt < NEGATIVE_TTL_MS) return@withContext null
            synchronized(negative) { negative.remove(tid) }
        }

        // 同一 tid 只发一次请求：后来者等待同一个结果（而不是立刻拿到 null）
        val (deferred, isOwner) = synchronized(inflight) {
            val existing = inflight[tid]
            if (existing != null) {
                existing to false
            } else {
                CompletableDeferred<String?>().also { inflight[tid] = it } to true
            }
        }
        if (!isOwner) return@withContext deferred.await()

        try {
            val url = semaphore.withPermit { fetch(tid) }
            if (!url.isNullOrBlank()) {
                synchronized(positive) { positive[tid] = url }
                ThumbCache.put(tid, url)
                deferred.complete(url)
                url
            } else {
                synchronized(negative) {
                    negative[tid] = System.currentTimeMillis()
                    if (negative.size > NEGATIVE_CAP) {
                        val now = System.currentTimeMillis()
                        negative.entries.removeAll { now - it.value >= NEGATIVE_TTL_MS }
                    }
                }
                deferred.complete(null)
                null
            }
        } catch (t: Throwable) {
            // 异常也要唤醒等待者，否则并发调用者会永久挂起
            deferred.complete(null)
            throw t
        } finally {
            synchronized(inflight) { inflight.remove(tid) }
        }
    }

    private fun fetch(tid: Int): String? = runCatching {
        val pageUrl = "https://mce.msi.cn/forum.php?mod=viewthread&tid=$tid"
        val html = GenericPageClient.get(pageUrl)
        if (GenericPageClient.isBlockedPage(html)) return@runCatching null
        ThreadDetailParser.firstImageUrl(html, pageUrl)
    }.getOrNull()

    /** 登录 / 退出 / 手动刷新时清空（正缓存与负缓存一起清） */
    fun clear() {
        synchronized(positive) { positive.clear() }
        synchronized(negative) { negative.clear() }
    }
}
