package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Session-level formhash cache: saves one full page fetch per post/reply/search.
 *
 * v0.3.3 批 4：
 *  - 登录 / 退出时必须 [invalidate]（formhash 跟会话绑定；旧实现从不失效，
 *    游客期缓存的 hash 在登录后继续用 → 服务端"请求来路不正确"）
 *  - [get] 加 Mutex 单飞，避免并发时各抓一次 forum.php
 */
object FormhashStore {
    private const val TTL_MS = 10 * 60 * 1000L
    private const val BASE = Net.BASE
    private val formhashRegex = Regex("""formhash=([a-z0-9]+)""")

    private val mutex = Mutex()
    private var cached: String? = null
    private var cachedAt = 0L

    suspend fun get(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            cached?.let { if (now - cachedAt < TTL_MS) return@withLock it }
            val hash = runCatching {
                val html = GenericPageClient.get("$BASE/forum.php")
                Net.formhashOf(html).orEmpty()
            }.getOrDefault("")
            if (hash.isNotBlank()) {
                cached = hash
                cachedAt = now
            }
            hash
        }
    }

    /** 登录 / 退出 / 会话变更后调用 */
    fun invalidate() {
        cached = null
        cachedAt = 0L
    }

    /** 用页面里已有的 formhash 预热 */
    fun seed(hash: String?) {
        if (!hash.isNullOrBlank()) {
            cached = hash
            cachedAt = System.currentTimeMillis()
        }
    }
}
