package cn.mce.starlight.core

import android.content.Context
import android.content.ComponentCallbacks2
import cn.mce.starlight.data.ThreadImageCache
import java.io.File

/**
 * v0.4.1：本地缓存占用统计与清理（设置页「存储」用）。
 *
 * 占用口径：应用 cacheDir（Coil 图片磁盘缓存 / WebView 缓存等）+ 首页列表缓存文件。
 * 清理不会动登录态、草稿、设置，只清可再生成的数据。
 */
object CacheManager {

    /** 当前缓存占用（字节） */
    fun sizeBytes(context: Context): Long {
        var total = 0L
        total += dirSize(context.cacheDir)
        total += dirSize(File(context.filesDir, "image_cache"))
        runCatching {
            context.filesDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("feed_cache")) total += f.length()
            }
        }
        return total
    }

    /** 清理缓存，返回清理前占用（字节） */
    fun clear(context: Context): Long {
        val before = sizeBytes(context)
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            loader.diskCache?.clear()
            loader.memoryCache?.trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        }
        runCatching { FeedCache.clear() }
        runCatching { ThreadImageCache.trimMemory() }
        runCatching { File(context.cacheDir, "image_cache").deleteRecursively() }
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching {
            context.filesDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("feed_cache")) f.delete()
            }
        }
        return before
    }

    fun format(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }
}
