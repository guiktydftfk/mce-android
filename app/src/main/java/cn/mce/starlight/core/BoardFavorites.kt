package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * v0.4.5：收藏板块（侧栏长按板块收藏，用于「收藏板块管理页」）。
 * 存储格式 `fid:名称,fid:名称`（兼容旧版纯 fid 列表）。
 */
object BoardFavorites {
    private const val PREFS = "mce_board_fav"
    private const val KEY = "fids"
    private var ctx: Context? = null

    /** fid -> 板块名 */
    var favorites by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    fun init(context: Context) {
        ctx = context.applicationContext
        favorites = read()
    }

    fun isFavorite(fid: Int): Boolean = fid in favorites

    fun toggle(fid: Int, name: String? = null) {
        val next = if (fid in favorites) {
            favorites - fid
        } else {
            favorites + (fid to (name ?: favorites[fid] ?: "板块 $fid"))
        }
        favorites = next
        save(next)
    }

    fun remove(fid: Int) {
        favorites = favorites - fid
        save(favorites)
    }

    private fun save(map: Map<Int, String>) {
        val text = map.entries.joinToString(",") { "${it.key}:${it.value.replace(",", " ").replace(":", " ")}" }
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY, CryptoBox.encrypt(text))?.apply()
    }

    private fun read(): Map<Int, String> {
        val raw = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null) ?: return emptyMap()
        val text = CryptoBox.decrypt(raw) ?: return emptyMap()
        val map = LinkedHashMap<Int, String>()
        text.split(',').forEach { part ->
            val fid = part.substringBefore(':').trim().toIntOrNull() ?: return@forEach
            val name = part.substringAfter(':', "").trim().ifBlank { "板块 $fid" }
            map[fid] = name
        }
        return map
    }
}
