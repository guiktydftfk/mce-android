package cn.mce.starlight.core

import cn.mce.starlight.data.ThreadDetail

/**
 * v0.4.4：帖子阅读进度缓存 —— 退出帖子再进来恢复上次的楼层与滚动位置。
 * 内存缓存，最多 5 帖、15 分钟有效。
 */
object ThreadProgressCache {
    data class Entry(val detail: ThreadDetail, val page: Int, val scrollIndex: Int, val at: Long)

    private val map = object : LinkedHashMap<Int, Entry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Entry>?): Boolean = size > 5
    }

    fun get(tid: Int): Entry? {
        val e = map[tid] ?: return null
        if (System.currentTimeMillis() - e.at > 15 * 60_000L) { map.remove(tid); return null }
        return e
    }

    fun put(tid: Int, detail: ThreadDetail, page: Int, scrollIndex: Int) {
        map[tid] = Entry(detail, page, scrollIndex, System.currentTimeMillis())
    }
}