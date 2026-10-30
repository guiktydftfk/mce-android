package cn.mce.starlight.core

import android.content.Context
import android.os.Build
import cn.mce.starlight.data.Boards
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自检报告：把运行环境 / 身份 / 运行时状态 / 网络与解析统计 / 最近错误 / 设置
 * 汇总成可粘贴的文本（v0.4.3 起大幅扩充，用于定位问题）。
 *
 * 隐私：不输出 Cookie 值；不采集 IMEI/MAC/序列号；错误信息自动脱敏（[DiagLog.redact]）。
 */
object Diagnostics {

    /**
     * 生成自检报告。
     * @param tid 可选：填了帖子 ID 就顺带做一次图片自检
     */
    suspend fun build(context: Context, tid: Int? = null): String {
        val sb = StringBuilder()
        sb.appendLine("===== MCE 星光班 自检报告 =====")
        sb.appendLine("时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()))

        section(sb, "设备标识")
        sb.appendLine("设备标识：" + runCatching { DeviceIdentity.current() }.getOrDefault("M-?-?"))
        sb.appendLine("（只包含论坛 UID 与本地哈希短码；不采集 IMEI/MAC/序列号，也不上传）")

        section(sb, "基本信息")
        val pkg = context.packageName
        val pi = runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
        }.getOrNull()
        sb.appendLine("应用包名：$pkg")
        sb.appendLine("版本：${pi?.versionName ?: "?"}（code ${pi?.versionCode ?: "?"}）")
        sb.appendLine("设备品牌：${Build.BRAND} / 厂商 ${Build.MANUFACTURER} / 型号 ${Build.MODEL}")
        sb.appendLine("设备代号：${Build.DEVICE}（产品 ${Build.PRODUCT}）")
        sb.appendLine("硬件：${Build.HARDWARE} / 主板 ${Build.BOARD}")
        sb.appendLine("系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        sb.appendLine("系统版本号：${Build.DISPLAY}")
        if (Build.VERSION.SDK_INT >= 23) sb.appendLine("安全补丁：${Build.VERSION.SECURITY_PATCH}")
        sb.appendLine("内核：${System.getProperty("os.version") ?: "?"}")
        sb.appendLine("ABI：${Build.SUPPORTED_ABIS.joinToString(",")}")
        sb.appendLine("语言/地区：${java.util.Locale.getDefault()}")
        sb.appendLine("时区：${java.util.TimeZone.getDefault().id}")
        val dm = context.resources.displayMetrics
        sb.appendLine("屏幕：${dm.widthPixels}x${dm.heightPixels} @ ${"%.2f".format(dm.density)}x（${dm.densityDpi}dpi）")

        section(sb, "账号与身份")
        sb.appendLine("登录状态：" + if (SessionStore.isLoggedIn) "已登录" else "未登录")
        sb.appendLine("论坛 UID：${SessionStore.uid ?: "-"}")
        sb.appendLine("用户名：${SessionStore.username ?: "-"}")
        sb.appendLine("管理组标记：${SessionStore.staff ?: "普通用户"}")
        val cookieNames = CookieJar.all().keys.sorted()
        sb.appendLine("Cookie 项：${cookieNames.size} 个" + if (cookieNames.isEmpty()) "" else "（${cookieNames.joinToString(",")}）")

        section(sb, "运行时状态")
        sb.appendLine("当前页面：${DiagStats.currentPage}")
        sb.appendLine("首页缓存：${DiagStats.feedCacheInfo}")
        sb.appendLine("最近刷新：${DiagStats.lastRefreshInfo}")
        DiagStats.memoryReport(context).forEach { sb.appendLine(it) }

        section(sb, "启动与性能")
        val startup = DiagStats.startupReport()
        if (startup.isEmpty()) sb.appendLine("（本次启动无记录）") else startup.forEach { sb.appendLine(it) }

        section(sb, "网络（最近 10 条）")
        val net = DiagStats.networkReport()
        if (net.isEmpty()) sb.appendLine("（暂无记录）") else net.forEach { sb.appendLine(it) }

        section(sb, "解析")
        DiagStats.parseReport().forEach { sb.appendLine(it) }

        section(sb, "设置快照")
        sb.appendLine("明暗模式：" + SettingsStore.darkModeSetting.label)
        sb.appendLine("主题色：" + SettingsStore.accentTheme)
        sb.appendLine("字体缩放：${SettingsStore.fontScale}")
        sb.appendLine("自定义背景：" + if (SettingsStore.backgroundPath != null) "有" else "无")
        sb.appendLine("低数据流量：${SettingsStore.lowDataMode}")
        sb.appendLine("首页过滤字数：${SettingsStore.feedMinChars}")
        sb.appendLine("签到提醒：" + if (SettingsStore.signinReminderEnabled) "开（${SettingsStore.signinReminderHour} 点后）" else "关")
        sb.appendLine("更新暂停：${SettingsStore.snoozeRemainingDays()} 天")
        sb.appendLine("板块数量：${Boards.all.size}")

        section(sb, "最近错误（后 10 条）")
        val logs = DiagLog.dump()
        if (logs.isEmpty()) sb.appendLine("（暂无）") else logs.takeLast(10).forEach { sb.appendLine(it) }

        section(sb, "最近一次崩溃")
        val crash = DiagLog.lastCrash()
        if (crash.isEmpty()) sb.appendLine("（无记录）") else crash.forEach { sb.appendLine(it) }

        section(sb, "最近抓取页面")
        val files = runCatching {
            context.filesDir.listFiles { f -> f.name.startsWith("debug_") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(10)
                ?.map { "${it.name}(${it.length()}B)" }
                .orEmpty()
        }.getOrDefault(emptyList())
        sb.appendLine(if (files.isEmpty()) "无" else files.joinToString("，"))

        if (tid != null && tid > 0) {
            section(sb, "图片自检（tid=$tid）")
            val report = runCatching { cn.mce.starlight.data.ImageDiagClient.run(tid) }.getOrNull()
            if (report == null) {
                sb.appendLine("图片自检失败（网络异常或帖子不可见）")
            } else {
                sb.appendLine("页面：${report.threadUrl}")
                sb.appendLine("HTML ${report.htmlLength}B，img 标签 ${report.imgTags} 个，正文解析出 ${report.parsedCount} 张图")
                if (report.probes.isEmpty()) sb.appendLine("没有可探测的图片")
                report.probes.forEachIndexed { index, p ->
                    sb.appendLine(
                        "[$index] HTTP ${p.code} type=${p.contentType ?: "?"} len=${p.length} looksHtml=${p.looksHtml}",
                    )
                    sb.appendLine("    url=${p.url}")
                    if (p.looksHtml || p.code !in 200..299) sb.appendLine("    head=${p.preview}")
                }
                report.notes.take(12).forEach { sb.appendLine("note: $it") }
            }
        }

        sb.appendLine("===== 报告结束 =====")
        return DiagLog.redact(sb.toString())
    }

    private fun section(sb: StringBuilder, title: String) {
        sb.appendLine()
        sb.appendLine("[${title}]")
    }
}
