package cn.mce.starlight.core

import cn.mce.starlight.data.ThreadSummary

/**
 * v0.4.6：列表阅读数缓存（方案 B：电脑版列表后台补抓）。
 * 首屏只渲染手机版数据（不阻塞）；阅读数由后台请求电脑版列表补齐，
 * 缓存 5 分钟，下次刷新/再次进入即可直接显示。
 */
object ThreadViewsCache {
    private val map = HashMap<Int, Pair<Long, Int>>()
    private const val TTL = 5 * 60 * 1000L

    @Synchronized
    fun get(tid: Int): Int? {
        val e = map[tid] ?: return null
        if (System.currentTimeMillis() - e.first > TTL) { map.remove(tid); return null }
        return e.second
    }

    @Synchronized
    fun putAll(views: Map<Int, Int>) {
        val now = System.currentTimeMillis()
        views.forEach { (tid, v) -> map[tid] = now to v }
        if (map.size > 800) {
            val cutoff = now - TTL
            map.entries.removeAll { it.value.first < cutoff }
        }
    }

    /** 把已知阅读数合并进列表（已有 views 的不覆盖） */
    fun attach(threads: List<ThreadSummary>): List<ThreadSummary> = threads.map { t ->
        if (t.views != null) t else get(t.id)?.let { t.copy(views = it) } ?: t
    }

    private val fetchedAt = HashMap<Int, Long>()

    /** v0.4.6：同一板块 5 分钟内只补抓一次（首页多板块时避免重复请求） */
    @Synchronized
    fun shouldFetch(fid: Int): Boolean {
        val at = fetchedAt[fid] ?: return true
        return System.currentTimeMillis() - at > TTL
    }

    @Synchronized
    fun markFetching(fid: Int) { fetchedAt[fid] = System.currentTimeMillis() }

    /** 用板块名找 fid（首页卡片只带板块名） */
    fun fidOfName(name: String?): Int? {
        if (name.isNullOrBlank()) return null
        return cn.mce.starlight.data.Boards.all.firstOrNull { it.name == name || name.contains(it.name) }?.fid
    }
}
