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
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun store(): SharedPreferences? = prefs

    /** 返回 Cookie 请求头 */
    fun header(url: String = "https://mce.msi.cn/"): String? {
        val map = all()
        if (map.isEmpty()) return null
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
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
    }

    fun save(name: String, value: String) {
        store()?.edit()?.putString(name, value)?.apply()
    }

    fun clear() {
        store()?.edit()?.clear()?.apply()
    }
}
