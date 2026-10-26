package cn.mce.starlight.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 帖子缩略图 URL 的磁盘缓存（容量 200）。
 *
 * v0.3.3 批 1：真正的访问序 LRU + get/put 同步（原来注释写 LRU 实为插入序）。
 * v0.3.3 批 2：[init] 只存 SharedPreferences 引用（很便宜），200 条记录的解析
 * 推迟到首次 [get]/[put] 才做，避免冷启动首帧前在主线程解析整份 XML 字符串。
 */
object ThumbCache {
    private const val PREFS = "mce_thumbs"
    private const val KEY = "map"
    private const val CAP = 200

    private var prefs: SharedPreferences? = null
    private var map: LinkedHashMap<Int, String>? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun get(tid: Int): String? = ensureLoaded()?.get(tid)

    @Synchronized
    fun put(tid: Int, url: String) {
        val m = ensureLoaded() ?: return
        m[tid] = url
        persist(m)
    }

    @Synchronized
    private fun ensureLoaded(): LinkedHashMap<Int, String>? {
        map?.let { return it }
        val p = prefs ?: return null
        val lru = object : LinkedHashMap<Int, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean =
                size > CAP
        }
        p.getString(KEY, "").orEmpty().split('\n').forEach { line ->
            val i = line.indexOf('|')
            if (i > 0) {
                line.substring(0, i).toIntOrNull()?.let { tid -> lru[tid] = line.substring(i + 1) }
            }
        }
        map = lru
        return lru
    }

    private fun persist(m: LinkedHashMap<Int, String>) {
        val sb = StringBuilder()
        m.entries.forEach { sb.append(it.key).append('|').append(it.value).append('\n') }
        prefs?.edit()?.putString(KEY, sb.toString())?.apply()
    }
}
