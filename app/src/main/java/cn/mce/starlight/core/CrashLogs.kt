package cn.mce.starlight.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.4.8：崩溃日志（本地归档，最多 10 份）。
 *
 * 每次未捕获崩溃由 [DiagLog.saveCrash] 写入 filesDir/crashes/ 下，
 * 这里是读取/清空入口；用户在设置 → 诊断 → 崩溃日志 里可查看并复制。
 */
object CrashLogs {

    data class Entry(
        val timeText: String,
        val summary: String,
        val lines: List<String>,
    )

    fun entries(context: Context): List<Entry> = runCatching {
        val dir = File(context.filesDir, "crashes")
        if (!dir.isDirectory) return emptyList()
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".txt") }
            .sortedByDescending { it.name }
            .mapNotNull { f ->
                val raw = runCatching { f.readText() }.getOrNull() ?: return@mapNotNull null
                val text = (CryptoBox.decrypt(raw) ?: raw).replace("\r\n", "\n")
                val lines = text.lines().filter { it.isNotBlank() }
                val time = lines.firstOrNull { it.startsWith("time=") }?.removePrefix("time=")?.trim()
                    ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(f.lastModified()))
                val summary = lines.firstOrNull { it.contains("Exception") || it.contains("Error") }
                    ?: lines.getOrNull(2).orEmpty()
                Entry(
                    timeText = time,
                    summary = DiagLog.redact(summary).take(160),
                    lines = lines.take(80).map { DiagLog.redact(it).take(300) },
                )
            }
    }.getOrDefault(emptyList())

    fun clearAll(context: Context) {
        runCatching {
            File(context.filesDir, "crashes").listFiles()?.forEach { it.delete() }
            DiagLog.clearCrash()
        }
    }
}