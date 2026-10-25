package cn.mce.starlight.core

import android.net.Uri

/**
 * 站内域判定 —— 与 iOS 版 SiteScope 逻辑一致。
 * 纯函数，便于单测（后续加 Robolectric/JVM 测试）。
 */
object SiteScope {
    private val hostSuffixWhitelist = setOf("mce.msi.cn")

    fun isInternal(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return hostSuffixWhitelist.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    fun isForumScriptPath(uri: Uri): Boolean {
        if (!isInternal(uri)) return false
        val last = uri.lastPathSegment?.lowercase() ?: return false
        return last in setOf(
            "forum.php", "portal.php", "home.php", "misc.php",
            "plugin.php", "member.php", "search.php",
        )
    }
}
