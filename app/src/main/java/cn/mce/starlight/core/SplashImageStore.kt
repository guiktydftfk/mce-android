package cn.mce.starlight.core

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * v0.4.7：自定义启动页首图。
 * 图片保存在应用私有目录 files/splash/splash.jpg；启动时全屏展示约 900ms 后自动淡出。
 */
object SplashImageStore {
    /** 本次进程是否已经展示过启动图（避免旋转/切页重复出现） */
    var consumed: Boolean = false

    private fun dir(ctx: Context): File = File(ctx.filesDir, "splash").apply { mkdirs() }

    fun current(ctx: Context): File? {
        val f = File(dir(ctx), "splash.jpg")
        return f.takeIf { it.exists() && it.length() > 0 }
    }

    /** 从相册选择后保存（返回是否成功） */
    fun save(ctx: Context, uri: Uri): Boolean = runCatching {
        val target = File(dir(ctx), "splash.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        } != null
    }.getOrDefault(false)

    fun clear(ctx: Context): Boolean = runCatching {
        File(dir(ctx), "splash.jpg").delete()
    }.getOrDefault(false)
}
