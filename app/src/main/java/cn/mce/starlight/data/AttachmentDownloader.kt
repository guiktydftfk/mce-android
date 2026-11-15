package cn.mce.starlight.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import cn.mce.starlight.core.WebCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * v0.4.8：非图片附件下载（zip / pdf / apk / doc 等）。
 *
 * 论坛附件需要登录 Cookie + UA 才能下载；下载到应用私有目录后用 FileProvider
 * 交给系统应用打开（不申请存储权限，也不落公共存储）。
 */
object AttachmentDownloader {

    private val illegalChars = Regex("""[\\/:*?"<>|]""")

    /** 下载附件，返回本地文件；失败抛 IOException（调用方转成友好提示） */
    suspend fun download(ctx: Context, url: String, name: String): File = withContext(Dispatchers.IO) {
        val safeName = name.replace(illegalChars, "_").trim().take(80).ifBlank { "attachment" }
        val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
        // 同名文件覆盖前先删掉，避免上次失败留下半截文件
        val file = File(dir, safeName)
        if (file.exists()) file.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = Net.CONNECT_TIMEOUT_MS
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", Net.MOBILE_UA)
            WebCookies.cookieHeader(url)?.let { setRequestProperty("Cookie", it) }
        }
        try {
            val code = conn.responseCode
            val input = if (code in 200..299) conn.inputStream else conn.errorStream
            if (input == null) throw IOException("附件下载失败（HTTP $code）")
            input.use { ins -> file.outputStream().use { outs -> ins.copyTo(outs) } }
        } finally {
            conn.disconnect()
        }
        if (!file.exists() || file.length() <= 0L) throw IOException("附件下载失败，请稍后重试")
        file
    }

    /** 用系统应用打开已下载附件 */
    fun open(ctx: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, "打开附件"))
    }
}
