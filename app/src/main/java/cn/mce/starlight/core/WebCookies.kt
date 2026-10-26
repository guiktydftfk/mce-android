package cn.mce.starlight.core

/**
 * Cookie 访问入口：只以自建 [CookieJar] 为准。
 *
 * v0.3.3 批 2（按用户决策「兼容层删除」）：
 *  - 删掉 WebView `CookieManager` 镜像：应用早已移除 WebView，但每条响应仍在
 *    `CookieManager.getInstance() + setCookie + flush()`（进程内 WebView provider 初始化 + 每次同步落盘），
 *    这是纯开销，而且 `legacyCookie()` 回退会把旧游客 Cookie 拼进来（历史上"登录了却是游客"的根因之一）。
 *  - 极老版本升级用户可能需要重新登录一次（CookieJar 自 0.0.14 起就是唯一来源）。
 */
object WebCookies {
    fun cookieHeader(url: String = "https://mce.msi.cn/"): String? = CookieJar.header(url)

    /** 把响应 Set-Cookie 写入 CookieJar（登录/会话的关键） */
    fun saveFromHeaders(headers: List<String>) {
        CookieJar.saveFromHeaders(headers)
    }

    fun clear() {
        CookieJar.clear()
    }
}
