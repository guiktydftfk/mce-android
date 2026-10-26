package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Session-level formhash cache: saves one full page fetch per post/reply/search. */
object FormhashStore {
    private const val TTL_MS = 10 * 60 * 1000L
    private var cached: String? = null
    private var cachedAt = 0L

    suspend fun get(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < TTL_MS) return@withContext it }
        val hash = runCatching {
            val html = GenericPageClient.get("https://mce.msi.cn/forum.php")
            Regex("""formhash=([a-z0-9]+)""").find(html)?.groupValues?.get(1).orEmpty()
        }.getOrDefault("")
        if (hash.isNotBlank()) { cached = hash; cachedAt = now }
        hash
    }

    fun invalidate() { cached = null; cachedAt = 0L }

    /** 用页面里已有的 formhash 预热 */
    fun seed(hash: String?) {
        if (!hash.isNullOrBlank()) { cached = hash; cachedAt = System.currentTimeMillis() }
    }
}