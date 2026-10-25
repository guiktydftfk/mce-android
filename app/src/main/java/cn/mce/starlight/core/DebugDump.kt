package cn.mce.starlight.core

import android.content.Context
import java.io.File

/**
 * 调试用：把抓取到的原始 HTML 落盘到应用私有目录，
 * 便于用 `adb shell run-as cn.mce.starlight cat files/debug_xxx.html` 直接查看真实结构。
 */
object DebugDump {
    private var dir: File? = null

    fun init(context: Context) {
        dir = context.applicationContext.filesDir
    }

    fun dump(name: String, content: String) {
        runCatching {
            File(dir ?: return, "debug_$name.html").writeText(content)
        }
    }
}
