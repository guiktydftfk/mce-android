package cn.mce.starlight.data

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
 * 现在：并发限制(2) + 失败负缓存(10 分钟) + 成功正缓存 + 同 tid 去重。
 */
object ThreadImageCache {
    private val positive = mutableMapOf<Int, String>()
    private val negative = mutableMapOf<Int, Long>()

    private const val NEGATIVE_TTL_MS = 10 * 60 * 1000L
    private val semaphore = Semaphore(2)
    private val inflight = mutableSetOf<Int>()

    suspend fun firstImage(tid: Int): String? = withContext(Dispatchers.IO) {
        synchronized(positive) { positive[tid] }?.let { return@withContext it }

        val failedAt = synchronized(negative) { negative[tid] }
        if (failedAt != null && System.currentTimeMillis() - failedAt < NEGATIVE_TTL_MS) {
            return@withContext null
        }

        // 同一 tid 只发一次请求
        val started = synchronized(inflight) {
            if (inflight.contains(tid)) false else { inflight.add(tid); true }
        }
        if (!started) return@withContext synchronized(positive) { positive[tid] }

        try {
            semaphore.withPermit {
                val url = runCatching {
                    val html = GenericPageClient.get("https://mce.msi.cn/forum.php?mod=viewthread&tid=$tid")
                    if (looksBlocked(html)) return@runCatching null
                    val detail = ThreadDetailParser.parse(
                        html,
                        "https://mce.msi.cn/forum.php?mod=viewthread&tid=$tid",
                        tid,
                        1,
                    )
                    detail.posts.asSequence()
                        .flatMap { it.images.asSequence() }
                        .firstOrNull()
                }.getOrNull()

                if (url.isNullOrBlank()) {
                    synchronized(negative) { negative[tid] = System.currentTimeMillis() }
                    null
                } else {
                    synchronized(positive) { positive[tid] = url }
                    url
                }
            }
        } finally {
            synchronized(inflight) { inflight.remove(tid) }
        }
    }

    /** 论坛频率限制/拦截页识别 */
    fun looksBlocked(html: String): Boolean =
        html.contains("\u9891\u7e41") ||
            html.contains("\u8bf7\u7a0d\u540e") ||
            html.contains("seccodeverify") ||
            html.contains("\u8bbf\u95ee\u88ab\u62d2\u7edd")

    fun clear() {
        positive.clear()
        negative.clear()
    }
}
