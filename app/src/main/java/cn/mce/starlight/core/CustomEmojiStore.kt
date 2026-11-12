package cn.mce.starlight.core

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * v0.4.7：本地自定义表情包（从相册/文件选择图片，保存到应用私有目录）。
 *
 * 数据位置：`files/custom_emoji/e_<时间戳>.png`，不依赖网络、不上传服务器。
 * 使用时由发帖/回帖页把图片作为附件上传（复用现有 uploadImage 流程）后插入正文。
 */
object CustomEmojiStore {
    private var appCtx: Context? = null

    fun init(context: Context) {
        appCtx = context.applicationContext
        runCatching { dir().mkdirs() }
    }

    private fun dir(): File = File(requireNotNull(appCtx).filesDir, "custom_emoji")

    /** 本地表情图片（按加入时间排序） */
    fun list(): List<File> {
        val ctx = appCtx ?: return emptyList()
        return runCatching {
            File(ctx.filesDir, "custom_emoji").listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                ?.sortedBy { it.name }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** 从 Uri 导入一张图片（复制到私有目录），返回保存后的文件 */
    fun add(uri: Uri): File? = runCatching {
        val ctx = appCtx ?: return null
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return null
        val d = File(ctx.filesDir, "custom_emoji").apply { mkdirs() }
        val f = File(d, "e_${System.currentTimeMillis()}.png")
        f.writeBytes(bytes)
        f
    }.getOrNull()

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)
}
