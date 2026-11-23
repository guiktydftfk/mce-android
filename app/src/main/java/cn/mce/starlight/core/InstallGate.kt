package cn.mce.starlight.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * v0.4.8：安装权限 / vivo 拦截引导。
 *
 * vivo / iQOO 的「Super Care（应用注册验证）」会在安装 APK 时弹窗：
 *  - "App registration verification failed" → 可点「authorize its installation」授权安装
 *  - 未在 vivo 注册的第三方应用每次安装都可能被拦
 *
 * App 无法绕过系统校验（也不应该），能做的是：
 *  1. 安装前检查「安装未知应用」权限，未授权就引导到系统设置；
 *  2. 给 vivo/iQOO 用户写清具体点哪里（避免点到 Cancel）；
 *  3. 安装器拉不起来时回退到应用详情页。
 */
object InstallGate {

    /** vivo / iQOO 判定（可单测的纯函数） */
    fun isVivoLike(manufacturer: String?, brand: String?): Boolean {
        val text = ((manufacturer ?: "") + " " + (brand ?: "")).lowercase()
        return text.contains("vivo") || text.contains("iqoo")
    }

    val isVivoDevice: Boolean
        get() = isVivoLike(Build.MANUFACTURER, Build.BRAND)

    /** Android 8.0+ 需要用户授予「安装未知应用」权限 */
    fun canRequestInstalls(context: Context): Boolean = runCatching {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(true)

    /** 打开「安装未知应用」授权页；失败则退到应用详情页 */
    fun openUnknownSourcesSettings(context: Context): Boolean {
        val pkgUri = Uri.parse("package:${context.packageName}")
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return true
        }
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    /** vivo 专属提示文案（未注册应用被 Super Care 拦截时的指引） */
    val vivoInstallHint: String
        get() = "vivo / iQOO 提示：安装时若出现「Super Care 应用注册验证」，请点弹窗里的" +
            "「authorize its installation（授权安装）」，不要点 Cancel；" +
            "若仍被拦截，请到 i管家 → 安全防护 关闭「安装安全检测」后再试。"
}
