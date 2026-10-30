package cn.mce.starlight.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.4.3：诊断日志（自检报告用）。
 *
 * - 内存环形缓冲：最近 50 条警告/错误，带时间与标签；
 * - 自动脱敏：Cookie / token / formhash / 密码 / 手机号等替换为 ***；
 * - 未捕获异常：落盘 filesDir/last_crash.txt，下次自检报告带出来（只保留最近 1 次）。
 *
 * 说明：日志只在本地，不上传；报告由用户主动复制。
 */
object DiagLog {
    private const val MAX = 50
    private const val CRASH_FILE = "last_crash.txt"

    private val buffer = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 记录一条（自动脱敏，过长截断） */
    @Synchronized
    fun record(tag: String, message: String?) {
        if (message.isNullOrBlank()) return
        val line = "${timeFormat.format(Date())} [$tag] ${redact(message).take(300)}"
        buffer.addLast(line)
        while (buffer.size > MAX) buffer.removeFirst()
    }

    @Synchronized
    fun record(tag: String, error: Throwable) {
        record(tag, error.javaClass.simpleName + ": " + (error.message ?: ""))
    }

    @Synchronized
    fun dump(): List<String> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()

    /** 保存崩溃堆栈（只留最近一次） */
    fun saveCrash(error: Throwable) {
        val ctx = appContext ?: return
        runCatching {
            val text = buildString {
                appendLine("time=" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()))
                appendLine("device=" + DeviceIdentity.current())
                appendLine(error.stackTraceToString().take(8000))
            }
            File(ctx.filesDir, CRASH_FILE).writeText(CryptoBox.encrypt(text))
        }
    }

    /** 读取最近一次崩溃（脱敏后逐行返回；无则空） */
    fun lastCrash(): List<String> {
        val ctx = appContext ?: return emptyList()
        val raw = runCatching { File(ctx.filesDir, CRASH_FILE).takeIf { it.isFile }?.readText() }.getOrNull()
            ?: return emptyList()
        val text = (CryptoBox.decrypt(raw) ?: raw).replace("\r\n", "\n")
        return text.lines().filter { it.isNotBlank() }.take(24).map { redact(it).take(300) }
    }

    fun clearCrash() {
        val ctx = appContext ?: return
        runCatching { File(ctx.filesDir, CRASH_FILE).delete() }
    }

    /**
     * 脱敏：Cookie/Authorization/token/formhash/password/手机号/邮箱等。
     * 纯函数，便于单测。
     */
    fun redact(input: String): String {
        var text = input
        // key=value / key: value 形式
        text = Regex("""(?i)(cookie|authorization|token|access_token|formhash|password|passwd|pwd|auth|salt)(\s*[=:]\s*)\S+""")
            .replace(text) { m -> m.groupValues[1] + m.groupValues[2] + "***" }
        // 长 hex / base64 串（Cookie 值常见）
        text = Regex("""\b[A-Fa-f0-9]{24,}\b""").replace(text, "***")
        // 手机号
        text = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""").replace(text, "***")
        // 邮箱
        text = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""").replace(text, "***@***")
        return text
    }
}
