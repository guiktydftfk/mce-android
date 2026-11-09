package cn.mce.starlight.core

import android.content.Context
import java.io.File

/**
 * v0.4.5：帖子 HTML 磁盘缓存 —— 弱网/断网时用上次成功抓取的页面兜底（离线可读）。
 * 只缓存帖子页面本身，最多 40 个文件，登录态变化不影响（页面内容由服务端渲染）。
 */
object ThreadHtmlCache {
    private var ctx: Context? = null
    private const val DIR = "thread_cache"
    private const val MAX_FILES = 40

    fun init(context: Context) { ctx = context.applicationContext }

    private fun dir(): File? = ctx?.let { File(it.filesDir, DIR) }

    fun put(tid: Int, page: Int, html: String) {
        val d = dir() ?: return
        runCatching {
            d.mkdirs()
            File(d, "${tid}_$page.html").writeText(html)
            val files = d.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
            files.drop(MAX_FILES).forEach { it.delete() }
        }
    }

    fun get(tid: Int, page: Int): String? {
        val d = dir() ?: return null
        val f = File(d, "${tid}_$page.html")
        return runCatching { if (f.isFile && f.length() > 0) f.readText() else null }.getOrNull()
    }

    fun clear() {
        dir()?.listFiles()?.forEach { it.delete() }
    }
}
