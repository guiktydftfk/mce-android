package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 帖子首图（列表卡片缩略图）。
 *
 * 注意两个历史坑（都与「解析时机/解析范围」有关）：
 *  1. 只扫 div.message → 相册图/附件图在 .attach_list 里，永远取不到；
 *  2. 失败结果也缓存 → 一次网络抖动后该帖永久没有缩略图。
 * 现在：直接用完整解析器（含附件容器），且只缓存成功结果。
 */
object ThreadImageCache {
    private val cache = mutableMapOf<Int, String>()

    suspend fun firstImage(tid: Int): String? = withContext(Dispatchers.IO) {
        cache[tid]?.let { return@withContext it }

        val url = runCatching {
            val html = GenericPageClient.get("https://mce.msi.cn/forum.php?mod=viewthread&tid=$tid")
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

        // 只有成功才缓存，避免失败结果长期污染
        if (!url.isNullOrBlank()) cache[tid] = url
        url
    }

    fun clear() = cache.clear()
}
