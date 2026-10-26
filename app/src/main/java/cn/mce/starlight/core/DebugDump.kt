package cn.mce.starlight.core

import android.content.Context
import java.io.File

/**
 * 调试用：把抓取到的原始 HTML 落盘到应用私有目录，
 * 便于用 `adb shell run-as cn.mce.starlight cat files/debug_xxx.html` 直接查看真实结构。
 *
 * v0.3.3 批 2：
 *  - 生产路径默认**不落盘**（[enabled] 默认 false；本项目一直打 debug 包分发，
 *    用 `BuildConfig.DEBUG` 判断会一直在生产环境落盘，所以改用显式开关）
 *  - 启动时清理历史遗留的 `debug_*.html`（老版本每次进勋章/好友/他人主页都会写一份，且永不清理）
 */
object DebugDump {
    /** 需要抓页面排查时临时改 true 再本地构建 */
    var enabled: Boolean = false

    /** 保留多少天内的调试文件（启动清理用） */
    private const val KEEP_DAYS = 7L

    private var dir: File? = null

    fun init(context: Context) {
        val filesDir = context.applicationContext.filesDir
        dir = filesDir
        // 清理放后台线程：不在首帧前的主线程做目录遍历/删除
        runCatching {
            Thread {
                runCatching { cleanupStale(filesDir, KEEP_DAYS) }
            }.apply { isDaemon = true }.start()
        }
    }

    fun dump(name: String, content: String) {
        if (!enabled) return
        runCatching {
            File(dir ?: return, "debug_$name.html").writeText(content)
        }
    }

    /** 删除超过 [keepDays] 天的 debug_*.html（keepDays <= 0 表示全删） */
    fun cleanupStale(filesDir: File, keepDays: Long) {
        val files = filesDir.listFiles() ?: return
        val now = System.currentTimeMillis()
        val cutoff = if (keepDays <= 0) Long.MAX_VALUE else now - keepDays * 24L * 60 * 60 * 1000
        files.forEach { file ->
            if (file.isFile && file.name.startsWith("debug_") && file.name.endsWith(".html")) {
                if (keepDays <= 0 || file.lastModified() < cutoff) file.delete()
            }
        }
    }
}
