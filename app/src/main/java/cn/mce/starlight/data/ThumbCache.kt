package cn.mce.starlight.data

import android.content.Context
import android.content.SharedPreferences

/** Tiny disk-backed LRU for thread thumbnails (cap 200 entries). */
object ThumbCache {
    private const val PREFS = "mce_thumbs"
    private const val KEY = "map"
    private const val CAP = 200
    private var prefs: SharedPreferences? = null
    private var map: LinkedHashMap<Int, String>? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs?.getString(KEY, "").orEmpty()
        val loaded = LinkedHashMap<Int, String>()
        raw.split('\n').forEach { line ->
            val i = line.indexOf('|')
            if (i > 0) {
                line.substring(0, i).toIntOrNull()?.let { tid ->
                    loaded[tid] = line.substring(i + 1)
                }
            }
        }
        // accessOrder = true for LRU
        map = object : LinkedHashMap<Int, String>(loaded) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
                return size > CAP
            }
        }
    }

    fun get(tid: Int): String? = map?.get(tid)?.also { map?.get(tid)?.let { _ -> map?.get(tid) } }

    fun put(tid: Int, url: String) {
        val m = map ?: return
        m[tid] = url
        m[tid] // touch
        persist(m)
    }

    private fun persist(m: LinkedHashMap<Int, String>) {
        val sb = StringBuilder()
        m.entries.toList().takeLast(CAP).forEach { sb.append(it.key).append('|').append(it.value).append('\n') }
        prefs?.edit()?.putString(KEY, sb.toString())?.apply()
    }
}