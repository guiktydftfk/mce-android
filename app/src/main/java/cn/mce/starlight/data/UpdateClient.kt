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
    private val MANIFEST_URLS = listOf(
        "https://gh-proxy.com/https://raw.githubusercontent.com/guiktydftfk/mce-android/main/version.json",
        "https://fastly.jsdelivr.net/gh/guiktydftfk/mce-android@main/version.json",
        "https://gcore.jsdelivr.net/gh/guiktydftfk/mce-android@main/version.json",
        "https://raw.githubusercontent.com/guiktydftfk/mce-android/main/version.json",
    )
    private const val API_LATEST =
        "https://api.github.com/repos/guiktydftfk/mce-android/releases/latest"
    private const val UA = "MCE-Android-Updater"
    private val versionNumberRegex = Regex("""(\d+)""")

    data class Release(
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val apkUrl: String?,
        /**
         * 紧急推送（v0.3.8）：version.json 里 `"emergency": true` 时，
         * 客户端不弹「是否更新」确认框，直接后台下载；下载完仍会拉起系统安装器（需用户点一次安装）。
         */
        val emergency: Boolean = false,
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

    sealed interface CheckResult {
        data class Available(val release: Release) : CheckResult
        data object Latest : CheckResult
        data class Failed(val reason: String) : CheckResult
    }

    /** 检查更新并能区分「已是最新 / 有新版 / 检查失败」 */
    suspend fun checkDetailed(context: Context): CheckResult = withContext(Dispatchers.IO) {
        // v0.3.3（批 1）：旧实现先对 4 个镜像逐个 httpGet 做"可达性预探测"，再调 check()
        // 把同样的 URL 再抓一遍 → 启动最坏 8 次串行请求。现在每个镜像只抓一次，
        // 并且某个镜像的清单不新时继续试下一个（避免 gh-proxy 缓存旧文件导致"永远已是最新"）。
        val current = currentVersionCode(context)
        var anyReachable = false

        for (manifestUrl in MANIFEST_URLS) {
            val body = httpGet(withTimestamp(manifestUrl)) ?: continue
            anyReachable = true
            parseManifest(body, current)?.let { return@withContext CheckResult.Available(it) }
        }

        // 回退 GitHub Releases
        httpGet(API_LATEST)?.let { body ->
            anyReachable = true
            parseGithubRelease(body, current)?.let { return@withContext CheckResult.Available(it) }
            return@withContext CheckResult.Latest
        }

        if (anyReachable) {
            CheckResult.Latest
        } else {
            CheckResult.Failed("\u65e0\u6cd5\u8fde\u63a5\u66f4\u65b0\u670d\u52a1\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u540e\u91cd\u8bd5")
        }
    }

    /** 兼容旧调用：只关心有没有新版（失败/最新都返回 null） */
    suspend fun check(context: Context): Release? =
        when (val r = checkDetailed(context)) {
            is CheckResult.Available -> r.release
            else -> null
        }

    /** 清单 URL 加时间戳绕 CDN 缓存（version.json 很小，开销可忽略；不加会误报"已是最新"） */
    private fun withTimestamp(url: String): String =
        url + (if (url.contains("?")) "&" else "?") + "t=" + System.currentTimeMillis()

    private fun parseManifest(body: String, currentCode: Int): Release? = runCatching {
        val json = JSONObject(body)
        val code = json.optInt("versionCode", 0)
        if (code <= currentCode) return@runCatching null
        Release(
            versionName = json.optString("versionName", "?"),
            versionCode = code,
            notes = json.optString("notes", ""),
            apkUrl = json.optString("apk").takeIf { it.isNotBlank() },
            emergency = json.optBoolean("emergency", false) || json.optBoolean("urgent", false),
        )
    }.getOrNull()

    private fun parseGithubRelease(body: String, currentCode: Int): Release? = runCatching {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trimStart('v', 'V')
        val notes = json.optString("body", "")
        val assetUrl = json.optJSONArray("assets")?.let { arr ->
            (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
        }
        val code = versionNumberRegex.find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (code > currentCode) Release(tag.ifBlank { "\u65b0\u7248\u672c" }, code, notes, assetUrl) else null
    }.getOrNull()

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
