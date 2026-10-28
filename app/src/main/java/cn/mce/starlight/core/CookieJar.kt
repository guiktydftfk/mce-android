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
        // v0.4.0：登录 Cookie 属于个人信息，落盘前用 Keystore AES-GCM 加密；
        // 旧版明文数据在后台线程静默迁移，不阻塞首帧。
        Thread { runCatching { migrateToEncrypted() } }.apply { isDaemon = true }.start()
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

    /**
     * v0.4.1：启动预热 —— 后台线程解密一次全部 Cookie 并填 header 缓存，
     * 避免首次网络请求（图片/列表）时才在调用线程做 Keystore 解密。
     */
    fun warmUp() {
        runCatching { header() }
    }
    fun all(): Map<String, String> {
        val p = store() ?: return emptyMap()
        return p.all.entries
            .filter { it.value is String }
            // v0.4.0：存储值是密文，读取时解密；旧明文由 CryptoBox 原样返回
            .mapNotNull { entry -> CryptoBox.decrypt(entry.value as String)?.let { entry.key to it } }
            .toMap()
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
                if (value.isEmpty() || value == "deleted") editor.remove(name) else editor.putString(name, CryptoBox.encrypt(value))
            }
        }
        editor.apply()
        invalidateHeaderCache()
    }

    @Synchronized
    fun save(name: String, value: String) {
        store()?.edit()?.putString(name, CryptoBox.encrypt(value))?.apply()
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

    /** 把历史遗留的明文 Cookie 迁移成密文（后台执行，失败不影响使用） */
    private fun migrateToEncrypted() {
        val p = store() ?: return
        val plain = p.all.filterValues { it is String && !CryptoBox.isEncrypted(it as String) }
        if (plain.isEmpty()) return
        val editor = p.edit()
        plain.forEach { (key, value) -> editor.putString(key, CryptoBox.encrypt(value as String)) }
        editor.apply()
        invalidateHeaderCache()
    }

    private fun invalidateHeaderCache() {
        cachedHeader = null
        cachedAt = 0L
    }
}
