package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** v0.4.4：收藏板块（长按侧栏板块可收藏/取消，收藏的板块在侧栏置顶并显示 ★） */
object BoardFavorites {
    private const val PREFS = "mce_board_fav"
    private const val KEY = "fids"
    private var ctx: Context? = null

    var favorites by mutableStateOf<Set<Int>>(emptySet())
        private set

    fun init(context: Context) {
        ctx = context.applicationContext
        favorites = read()
    }

    fun isFavorite(fid: Int): Boolean = fid in favorites

    fun toggle(fid: Int) {
        val next = if (fid in favorites) favorites - fid else favorites + fid
        favorites = next
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY, CryptoBox.encrypt(next.joinToString(",")))?.apply()
    }

    private fun read(): Set<Int> {
        val raw = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null) ?: return emptySet()
        val text = CryptoBox.decrypt(raw) ?: return emptySet()
        return text.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }
}