package cn.mce.starlight.core

import cn.mce.starlight.data.UserThreadsClient

/** v0.4.3：「TA 的主题」列表数据缓存 —— 打开帖子返回后直接恢复列表，不重新加载、不回到顶部 */
object UserThreadsCache {
    data class Snapshot(
        val items: List<UserThreadsClient.Item>,
        val page: Int,
        val endReached: Boolean,
    )

    // v0.4.4：容量上限，避免长时间使用后无限增长
    private val map = object : LinkedHashMap<Int, Snapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Snapshot>?): Boolean = size > 20
    }

    fun get(uid: Int): Snapshot? = map[uid]

    fun put(uid: Int, items: List<UserThreadsClient.Item>, page: Int, endReached: Boolean) {
        if (items.isNotEmpty()) map[uid] = Snapshot(items, page, endReached)
    }
}
