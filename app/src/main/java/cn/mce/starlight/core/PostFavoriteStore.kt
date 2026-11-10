package cn.mce.starlight.core

import android.content.Context

/**
 * v0.4.6：收藏分组/备注（本地保存，服务端收藏保持不变，这里只做分组与备注管理）。
 * 存储格式：tid|title|group|note|at（每行一条，加密落盘）。
 */
object PostFavoriteStore {
    data class Item(val tid: Int, val title: String, val group: String, val note: String, val at: Long)

    private const val PREFS = "mce_post_fav"
    private const val KEY = "list"
    private var ctx: Context? = null

    fun init(context: Context) { ctx = context.applicationContext }

    fun items(): List<Item> {
        val raw = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null) ?: return emptyList()
        return decode(CryptoBox.decrypt(raw) ?: return emptyList())
    }

    /** 添加/更新（同 tid 覆盖分组与备注） */
    fun put(tid: Int, title: String, group: String, note: String) {
        if (tid <= 0) return
        val list = (listOf(Item(tid, title, group.ifBlank { "默认分组" }, note, System.currentTimeMillis())) +
            items().filterNot { it.tid == tid })
            .take(200)
        save(list)
    }

    fun remove(tid: Int) { save(items().filterNot { it.tid == tid }) }

    private fun save(list: List<Item>) {
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY, CryptoBox.encrypt(encode(list)))?.apply()
    }

    /** 纯函数：编码（供单测） */
    fun encode(list: List<Item>): String = list.joinToString("\n") { it ->
        listOf(it.tid, it.title, it.group, it.note, it.at)
            .joinToString("|") { v -> v.toString().replace("|", "｜").replace("\n", " ") }
    }

    /** 纯函数：解码（供单测） */
    fun decode(text: String): List<Item> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|')
        if (p.size < 5) return@mapNotNull null
        val tid = p[0].toIntOrNull() ?: return@mapNotNull null
        val at = p[4].toLongOrNull() ?: 0L
        Item(tid, p[1], p[2].ifBlank { "默认分组" }, p[3], at)
    }.toList()
}
