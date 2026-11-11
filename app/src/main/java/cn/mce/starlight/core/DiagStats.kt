package cn.mce.starlight.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.4.3：自检报告用的轻量统计（全部只在内存，重启清零）。
 *
 * - 启动阶段耗时：App 创建 / 首帧 / 登录探测 / 消息刷新 / 首页首屏；
 * - 网络：最近 10 条请求（方法、路径、状态码、耗时、是否失败）；
 * - 解析：各类页面最近一次解析出的条目数 / 失败次数；
 * - 运行时：当前页面、最近一次刷新信息。
 */
object DiagStats {

    // ---------- 启动耗时 ----------
    private val startup = LinkedHashMap<String, Long>()
    private var processStartAt = System.currentTimeMillis()

    fun markProcessStart() {
        processStartAt = System.currentTimeMillis()
        startup.clear()
    }

    private var launchStartedAt = 0L
    private var firstFrameRecorded = false

    /** v0.4.7：每次 Activity 启动时调用；首帧耗时按本次启动计时，避免进程复用导致的 96s/204s 假数据 */
    fun markLaunchStart() {
        launchStartedAt = System.currentTimeMillis()
        firstFrameRecorded = false
    }

    fun recordFirstFrameIfNeeded() {
        if (firstFrameRecorded || launchStartedAt <= 0L) return
        firstFrameRecorded = true
        val ms = (System.currentTimeMillis() - launchStartedAt).coerceIn(0L, 60000L)
        recordStartup("首帧", ms)
    }
    /** 距进程启动的毫秒数（首帧/启动阶段计时用） */
    fun processElapsedMs(): Long = System.currentTimeMillis() - processStartAt

    fun recordStartup(stage: String, ms: Long) {
        startup[stage] = ms
    }

    fun startupReport(): List<String> = startup.map { (k, v) -> "$k=${v}ms" }

    // ---------- 网络 ----------
    data class NetRecord(
        val method: String,
        val path: String,
        val code: Int,
        val ms: Long,
        val error: String?,
    )

    private const val NET_MAX = 10
    private val netBuffer = ArrayDeque<NetRecord>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    @Synchronized
    fun recordNetwork(method: String, url: String, code: Int, ms: Long, error: String? = null) {
        val path = url
            .substringAfter("://", url)
            .substringAfter("/", "")
            .substringBefore("?")
            .ifBlank { url.take(80) }
        netBuffer.addLast(NetRecord(method, "/" + path, code, ms, error?.take(80)))
        while (netBuffer.size > NET_MAX) netBuffer.removeFirst()
    }

    @Synchronized
    fun networkReport(): List<String> = netBuffer.map { r ->
        val status = if (r.code > 0) "HTTP ${r.code}" else "ERR"
        val err = r.error?.let { " err=$it" }.orEmpty()
        "${r.method} ${r.path} $status ${r.ms}ms$err"
    }

    // ---------- 解析 ----------
    private val parseCounts = LinkedHashMap<String, String>()
    private var parseFailures = 0

    @Synchronized
    fun recordParse(type: String, detail: String) {
        parseCounts[type] = detail
    }

    @Synchronized
    fun recordParseFailure(type: String, reason: String) {
        parseFailures++
        DiagLog.record("parse:$type", reason)
    }

    @Synchronized
    fun parseReport(): List<String> =
        parseCounts.map { (k, v) -> "$k=$v" } + listOf("解析失败次数=$parseFailures")

    // ---------- 运行时 ----------
    var currentPage: String = "?"
        private set
    var lastRefreshInfo: String = "-"
        private set
    var feedCacheInfo: String = "-"
        private set

    fun setCurrentPage(page: String) {
        currentPage = page
    }

    fun setLastRefresh(info: String) {
        lastRefreshInfo = info
    }

    fun setFeedCacheInfo(info: String) {
        feedCacheInfo = info
    }

    // ---------- 内存/缓存 ----------
    fun memoryReport(context: Context): List<String> {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
        val maxMb = rt.maxMemory() / 1024 / 1024
        val cacheMb = CacheManager.sizeBytes(context) / 1024.0 / 1024.0
        return listOf(
            "Java 堆=${usedMb}MB / ${maxMb}MB",
            "缓存占用=%.1f MB".format(cacheMb),
        )
    }

    fun cleanTime(): String = timeFormat.format(Date())
}
