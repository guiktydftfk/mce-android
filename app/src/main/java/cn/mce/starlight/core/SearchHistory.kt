package cn.mce.starlight.core

import android.content.Context

/** v0.4.4：搜索历史（最近 10 条，本地保存） */
object SearchHistory {
    private const val PREFS = "mce_search_history"
    private const val KEY = "list"
    private const val MAX = 10
    private var ctx: Context? = null

    fun init(context: Context) { ctx = context.applicationContext }

    fun items(): List<String> {
        val raw = ctx?.let { Prefs.of(it, PREFS) }?.getString(KEY, null) ?: return emptyList()
        return CryptoBox.decrypt(raw)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun add(keyword: String) {
        val k = keyword.trim()
        if (k.isBlank()) return
        val list = (listOf(k) + items().filterNot { it == k }).take(MAX)
        val prefs = ctx?.let { Prefs.of(it, PREFS) } ?: return
        prefs.edit().putString(KEY, CryptoBox.encrypt(list.joinToString("\n"))).apply()
    }

    /** v0.4.7：删除单条历史 */
    fun remove(keyword: String) {
        val list = items().filterNot { it == keyword }
        val prefs = ctx?.let { Prefs.of(it, PREFS) } ?: return
        if (list.isEmpty()) {
            prefs.edit().remove(KEY).apply()
        } else {
            prefs.edit().putString(KEY, CryptoBox.encrypt(list.joinToString("\n"))).apply()
        }
    }

    fun clear() {
        ctx?.let { Prefs.of(it, PREFS) }?.edit()?.remove(KEY)?.apply()
    }
}