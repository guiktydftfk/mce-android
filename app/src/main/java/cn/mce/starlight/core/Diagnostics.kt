package cn.mce.starlight.core

import android.content.Context
import android.os.Build
import cn.mce.starlight.data.Boards
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 自检报告：把运行环境 / 登录态 / 设置 / 最近错误汇总成可粘贴的文本 */
object Diagnostics {

    fun build(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("===== MCE 星光班 自检报告 =====")
        sb.appendLine("时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()))

        // 版本
        val pkg = context.packageName
        val pi = runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
        }.getOrNull()
        sb.appendLine("应用包名：$pkg")
        sb.appendLine("版本：${pi?.versionName ?: "?"}（code ${pi?.versionCode ?: "?"}）")

        // 设备
        sb.appendLine("设备品牌：${Build.BRAND}")
        sb.appendLine("设备厂商：${Build.MANUFACTURER}")
        sb.appendLine("设备型号：${Build.MODEL}")
        sb.appendLine("设备代号：${Build.DEVICE}（产品 ${Build.PRODUCT}）")
        sb.appendLine("硬件：${Build.HARDWARE} / 主板 ${Build.BOARD}")
        sb.appendLine("系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        sb.appendLine("系统版本号：${Build.DISPLAY}")
        if (Build.VERSION.SDK_INT >= 23) sb.appendLine("安全补丁：${Build.VERSION.SECURITY_PATCH}")
        sb.appendLine("内核：${System.getProperty("os.version") ?: "?"}")
        sb.appendLine("ABI：${Build.SUPPORTED_ABIS.joinToString(",")}")
        sb.appendLine("语言/地区：${java.util.Locale.getDefault()}")
        val dm = context.resources.displayMetrics
        val density = context.resources.displayMetrics.density
        sb.appendLine("屏幕：${dm.widthPixels}x${dm.heightPixels} @ ${"%.2f".format(density)}x（${dm.densityDpi}dpi）")

        // 登录态（不输出 Cookie 值）
        sb.appendLine("登录状态：" + if (SessionStore.isLoggedIn) "已登录（${SessionStore.username ?: "?"}）" else "未登录")
        val cookieNames = CookieJar.all().keys.sorted()
        sb.appendLine("Cookie 项：${cookieNames.size} 个" + if (cookieNames.isEmpty()) "" else "（${cookieNames.joinToString(",")}）")

        // 设置
        sb.appendLine("深色模式：" + if (SettingsStore.darkMode) "开" else "关")
        sb.appendLine("自定义背景：" + if (SettingsStore.backgroundPath != null) "有" else "无")
        sb.appendLine("板块数量：${Boards.all.size}")

        // 最近落盘的调试页面
        val files = runCatching {
            context.filesDir.listFiles { f -> f.name.startsWith("debug_") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(10)
                ?.map { "${it.name}(${it.length()}B)" }
                .orEmpty()
        }.getOrDefault(emptyList())
        sb.appendLine("最近抓取页面：${if (files.isEmpty()) "无" else files.joinToString("，")}")

        sb.appendLine("===== 报告结束 =====")
        return sb.toString()
    }
}
