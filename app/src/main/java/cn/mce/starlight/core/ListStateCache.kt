package cn.mce.starlight.core

import androidx.compose.foundation.lazy.LazyListState

/**
 * v0.4.3：列表滚动位置缓存。
 *
 * 打开帖子时列表会被销毁（只渲染栈顶路由），返回后重新组合；
 * 这里把 LazyListState 按 key 缓存下来，返回时恢复原位置（和首页一致）。
 */
object ListStateCache {
    // v0.4.4：容量上限，避免页面越开越多时无限累积
    private val map = object : LinkedHashMap<String, LazyListState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LazyListState>?): Boolean = size > 20
    }
    fun get(key: String): LazyListState = map.getOrPut(key) { LazyListState() }
}
