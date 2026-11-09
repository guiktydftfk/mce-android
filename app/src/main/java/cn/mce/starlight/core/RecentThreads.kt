package cn.mce.starlight.core

import android.content.Context

/** v0.4.4：最近浏览的帖子（最近 30 条，本地保存） */
object RecentThreads {
    data class Item(val tid: Int, val title: String, val at: Long)

    private const val PREFS = "mce_recent_threads"
    private const val KEY = "list"
    private const val MAX = 30
    private var ctx: Context? = null

    fun init(context: Context) { ctx = context.applicationContext }

    fun items(): List<Item> {
        val raw = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null) ?: return emptyList()
        return (CryptoBox.decrypt(raw) ?: return emptyList()).lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|')
                val tid = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val at = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                Item(tid, parts.getOrNull(1).orEmpty(), at)
            }.toList()
    }

    fun add(tid: Int, title: String) {
        if (tid <= 0 || title.isBlank()) return
        val list = (listOf(Item(tid, title, System.currentTimeMillis())) + items().filterNot { it.tid == tid })
            .take(MAX)
        val text = list.joinToString("\n") { "${it.tid}|${it.title.replace("|", "｜")}|${it.at}" }
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY, CryptoBox.encrypt(text))?.apply()
    }

    /** v0.4.5：删除单条浏览记录 */
    fun remove(tid: Int) {
        val list = items().filterNot { it.tid == tid }
        val text = list.joinToString("\n") { "${it.tid}|${it.title.replace("|", "｜")}|${it.at}" }
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY, CryptoBox.encrypt(text))?.apply()
    }

    fun clear() {
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(KEY)?.apply()
    }
}