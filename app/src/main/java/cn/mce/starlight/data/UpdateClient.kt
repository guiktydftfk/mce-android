package cn.mce.starlight.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import cn.mce.starlight.core.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 自动更新：
 *  1. 读取仓库里的 version.json（失败则退 GitHub Releases API）
 *  2. 点“更新”后后台静默下载 APK 到 cacheDir/updates
 *  3. 下载完成后拉起系统安装器（普通应用无法完全静默安装，需用户点一次“安装”）
 *  4. 安装后下次启动即为新版
 */
object UpdateClient {
    private const val RAW_MANIFEST =
        "https://raw.githubusercontent.com/guiktydftfk/mce-android/main/version.json"
    private const val API_LATEST =
        "https://api.github.com/repos/guiktydftfk/mce-android/releases/latest"
    private const val UA = "MCE-Android-Updater"

    data class Release(
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val apkUrl: String?,
    )

    private fun httpGet(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        val text = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
        else conn.errorStream?.bufferedReader()?.use { it.readText() }
        conn.disconnect()
        if (code in 200..299) text else null
    }.getOrNull()

    /** 当前安装版本 */
    fun currentVersionCode(context: Context): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).let { info ->
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(0)

    fun currentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    /** 检查更新；返回 null 表示已是最新或检查失败 */
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        // 1) 优先 version.json
        httpGet(RAW_MANIFEST)?.let { body ->
            runCatching {
                val json = JSONObject(body)
                val code = json.optInt("versionCode", 0)
                if (code <= currentVersionCode(context)) return@withContext null
                Release(
                    versionName = json.optString("versionName", "?"),
                    versionCode = code,
                    notes = json.optString("notes", ""),
                    apkUrl = json.optString("apk").takeIf { it.isNotBlank() },
                )
            }.onSuccess { if (it?.versionCode ?: 0 > 0) return@withContext it }
        }

        // 2) 回退 GitHub Releases
        httpGet(API_LATEST)?.let { body ->
            runCatching {
                val json = JSONObject(body)
                val tag = json.optString("tag_name").trimStart('v', 'V')
                val notes = json.optString("body", "")
                val assetUrl = json.optJSONArray("assets")?.let { arr ->
                    (0 until arr.length())
                        .map { arr.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk") }
                        ?.optString("browser_download_url")
                }
                val code = Regex("""(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (code > currentVersionCode(context)) {
                    Release(tag.ifBlank { "新版本" }, code, notes, assetUrl)
                } else null
            }.getOrNull()
        }
    }

    /** 静默下载 APK，返回本地文件 */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "MCE-update.apk")
            if (target.exists()) target.delete()

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
            }
            if (conn.responseCode !in 200..299) return@runCatching null

            val total = conn.contentLengthLong
            var read = 0L
            var lastPercent = -1
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val percent = (read * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            conn.disconnect()

            if (target.length() > 0) {
                SettingsStore.updateDownloadedApk(target.absolutePath)
                target
            } else null
        }.getOrNull()
    }

    /** 拉起系统安装器（普通应用需用户点“安装”） */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /** 已下载但未安装的包 */
    fun pendingApk(context: Context): File? = SettingsStore.downloadedApkPath
        ?.let { File(it) }
        ?.takeIf { it.exists() && it.length() > 0 }
}
