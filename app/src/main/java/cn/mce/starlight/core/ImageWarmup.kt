package cn.mce.starlight.core

import android.content.Context
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.ThreadImageCache
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v0.4.8：启动页图片预热。
 *
 * 清缓存后首页缩略图很慢的原因：列表只给帖子 id，缩略图需要先逐个请求
 * `viewthread` 整页解析出首图 URL（见 [ThreadImageCache]），再下载图片。
 * 首次安装 / 清缓存后这些全部要重新做一遍，首屏图片就会长时间转圈。
 *
 * 这里在「图片缓存为空」时，利用启动页停留的 2 秒先并行预热：
 *  1. 触发 Coil ImageLoader 初始化（OkHttp 连接池 / 磁盘缓存）
 *  2. 取首页前 8 条帖子，预解析首图并预下载（与列表共用 ThreadImageCache 的 inflight 去重）
 * 这样启动页结束时，首屏图片通常已命中内存/磁盘缓存。
 */
object ImageWarmup {

    /** 图片缓存目录小于该值视为「空缓存」（刚安装 / 刚清过缓存） */
    private const val EMPTY_THRESHOLD_BYTES = 64L * 1024

    /** 预热帖子数：与首页首屏可见数大致匹配，避免过多请求抢带宽 */
    private const val PREFETCH_THREADS = 8

    fun isImageCacheEmpty(context: Context): Boolean {
        val dir = File(context.cacheDir, "image_cache")
        if (!dir.exists()) return true
        var size = 0L
        runCatching {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                    if (size > EMPTY_THRESHOLD_BYTES) return false
                }
            }
        }
        return size <= EMPTY_THRESHOLD_BYTES
    }

    /** 预热首页缩略图；失败静默（启动阶段绝不阻塞主流程） */
    suspend fun prefetchHome(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            val threads = FeedCache.get("guide_newthread")?.threads
                ?: runCatching {
                    HttpForumRepository().loadGuide("newthread", 1).threads
                }.getOrNull().orEmpty()
            if (threads.isEmpty()) return@runCatching

            // ThreadImageCache 内部限流 2 并发 + inflight 去重，这里可以放心并发发起
            val target = threads.take(PREFETCH_THREADS)
            val urls = coroutineScope {
                target.map { thread ->
                    async { runCatching { ThreadImageCache.firstImage(thread.id) }.getOrNull() }
                }.awaitAll()
            }.filterNotNull().distinct()

            urls.forEach { url ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(720)
                        .memoryCacheKey(url)
                        .build(),
                )
            }
            DiagLog.record("image-warmup", "threads=${target.size} images=${urls.size}")
        }
    }
}
