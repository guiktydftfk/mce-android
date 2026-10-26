package cn.mce.starlight.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 自建 Cookie 存储。
 *
 * 之前依赖 WebView 的 CookieManager，但应用已移除 WebView，
 * 没有 WebView 实例时 CookieManager 不能可靠地保存/读取 Cookie，
 * 导致"登录了却像没登录"。
 *
 * 这里用 SharedPreferences 保存 name=value，并在每个请求里带上。
 */
object CookieJar {
    private const val PREFS = "mce_cookies"
    private const val BASE = "https://mce.msi.cn/"
    private var prefs: SharedPreferences? = null

    /** Cookie 请求头缓存：每次网络请求（含每张图片）都去 getAll()+filter+associate 太浪费 */
    private var cachedHeader: String? = null
    private var cachedAt = 0L
    private const val HEADER_TTL_MS = 5 * 60 * 1000L

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun store(): SharedPreferences? = prefs

    /** 返回 Cookie 请求头（带内存缓存，save/clear 时失效） */
    @Synchronized
    fun header(url: String = BASE): String? {
        cachedHeader?.let {
            if (System.currentTimeMillis() - cachedAt < HEADER_TTL_MS) return it
        }
        val map = all()
        if (map.isEmpty()) {
            cachedHeader = null
            return null
        }
        val header = map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        cachedHeader = header
        cachedAt = System.currentTimeMillis()
        return header
    }

    fun all(): Map<String, String> {
        val p = store() ?: return emptyMap()
        return p.all.entries
            .filter { it.value is String }
            .associate { it.key to (it.value as String) }
    }

    /** 是否已登录（Discuz 下发的 auth 类 Cookie） */
    fun hasAuth(): Boolean {
        val keys = all().keys
        return keys.any { it.contains("auth", ignoreCase = true) || it.contains("saltkey", ignoreCase = true) }
    }

    /** 解析并保存响应中的 Set-Cookie */
    @Synchronized
    fun saveFromHeaders(setCookieHeaders: List<String>) {
        val p = store() ?: return
        val editor = p.edit()
        setCookieHeaders.forEach { raw ->
            val first = raw.substringBefore(';')
            val name = first.substringBefore('=', "").trim()
            val value = first.substringAfter('=', "").trim()
            if (name.isNotEmpty()) {
                if (value.isEmpty() || value == "deleted") editor.remove(name) else editor.putString(name, value)
            }
        }
        editor.apply()
        invalidateHeaderCache()
    }

    @Synchronized
    fun save(name: String, value: String) {
        store()?.edit()?.putString(name, value)?.apply()
        invalidateHeaderCache()
    }

    @Synchronized
    fun clear() {
        store()?.edit()?.clear()?.apply()
        invalidateHeaderCache()
    }

    /**
     * 移除所有 `*_mobile` Cookie。
     *
     * 背景：用户搜索回退到电脑版模板时请求带 `mobile=no`，服务端会把这个选择**写成 Cookie**
     * （如 `l2ND_2132_mobile=no`），之后所有请求都会被当成电脑版 → 移动版选择器全部解析为空。
     * 所以：① 电脑版回退请求用"隔离 Cookie"模式（不落盘）；② 启动时兜底清掉这个残留。
     */
    @Synchronized
    fun removeMobileCookies() {
        val p = store() ?: return
        val keys = p.all.keys.filter { it.endsWith("_mobile", ignoreCase = true) }
        if (keys.isEmpty()) return
        val editor = p.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
        invalidateHeaderCache()
    }

    private fun invalidateHeaderCache() {
        cachedHeader = null
        cachedAt = 0L
    }
}
