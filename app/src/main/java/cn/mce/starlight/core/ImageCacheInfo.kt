package cn.mce.starlight.core

import android.content.Context
import java.io.File

/**
 * v0.4.8：图片缓存状态探测（只读，不做任何网络操作）。
 *
 * 用于判断是否刚安装 / 刚清过图片缓存：这种情况首屏图片要重新下载，
 * 启动页可以多停留一会儿（2 秒），避免进首页后立刻看到一排灰占位。
 * 注意：这里只做一个目录大小统计，不发起任何请求，避免影响图片加载。
 */
object ImageCacheInfo {

    private const val EMPTY_THRESHOLD_BYTES = 64L * 1024

    fun isEmpty(context: Context): Boolean {
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
}
