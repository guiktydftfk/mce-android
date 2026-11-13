package cn.mce.starlight.core

import android.content.Context
import cn.mce.starlight.data.SmileyClient

/**
 * v0.4.7：最近使用的表情（微信/QQ 式表情面板的「最近」分类）。
 * 只记录表情代码与预览地址，本地保存，最多 24 个。
 */
object RecentSmileyStore {
    private const val PREFS = "mce_recent_smiley"
    private const val KEY = "list"
    private const val MAX = 24
    private var ctx: Context? = null

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    fun recent(): List<SmileyClient.Smiley> {
        val raw = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null)
            ?: return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                SmileyClient.Smiley(parts[0], parts[1])
            } else null
        }
    }

    fun record(smiley: SmileyClient.Smiley) {
        val existing = recent().filterNot { it.code == smiley.code }
        val list = (listOf(smiley) + existing).take(MAX)
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY, list.joinToString("\n") { "${it.code}|${it.url}" })
            ?.apply()
    }

    fun clear() {
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(KEY)?.apply()
    }
}
