package cn.mce.starlight.data

/**
 * 站点/网络公共常量与小工具（v0.3.3 批 6 收敛）。
 *
 * 之前 BASE 硬编码 14 份、MOBILE_UA 4 份、`absolutize()` 10 份、formhash 提取 4 套、
 * 拦截页/登录页判定 2 份——改一处要改十几处，还出现过漏改导致行为不一致。
 */
object Net {
    const val BASE = "https://mce.msi.cn"

    const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 " +
            "Mobile/15E148 Safari/604.1"

    /** 桌面版 UA：个别页面手机模板缺失（如用户搜索页），需要强制电脑版模板才渲染 */
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    private val formhashInputRegex = Regex("""name="formhash"\s+value="([a-z0-9]+)"""")
    private val formhashQueryRegex = Regex("""formhash=([a-z0-9]+)""")

    /** 从任意 Discuz 页面提取 formhash（隐藏域优先，其次链接参数） */
    fun formhashOf(html: String): String? =
        formhashInputRegex.find(html)?.groupValues?.get(1)
            ?: formhashQueryRegex.find(html)?.groupValues?.get(1)

    /** 相对地址补全为绝对地址 */
    fun absolutize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http") -> src
        src.startsWith("/") -> "$BASE$src"
        else -> "$BASE/$src"
    }

    /**
     * 是否被论坛频率/安全策略拦截。
     * 要求"确实是 Discuz 提示/错误页"才判定，正常页面里出现"请稍后"/"seccodeverify" 不误判。
     */
    fun isBlockedPage(html: String): Boolean {
        val looksLikeNotice = html.contains("messagetext") ||
            html.contains("alert_error") ||
            html.contains("alert_info") ||
            html.contains("showError(")
        if (!looksLikeNotice) return false
        return html.contains("\u9891\u7e41") ||            // 频繁
            html.contains("\u8bf7\u7a0d\u540e") ||          // 请稍后
            html.contains("\u8bbf\u95ee\u88ab\u62d2\u7edd") || // 访问被拒绝
            html.contains("seccodeverify")
    }

    /** 是否被重定向到登录页（会话失效） */
    fun isLoginPage(html: String): Boolean =
        html.contains("id=\"loginform\"") || html.contains("name=\"loginsubmit\"") ||
            (html.contains("name=\"password\"") && html.contains("member.php?mod=logging"))
}
