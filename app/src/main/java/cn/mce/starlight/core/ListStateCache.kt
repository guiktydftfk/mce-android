package cn.mce.starlight.core

import androidx.compose.foundation.lazy.LazyListState

/**
 * v0.4.3：列表滚动位置缓存。
 *
 * 打开帖子时列表会被销毁（只渲染栈顶路由），返回后重新组合；
 * 这里把 LazyListState 按 key 缓存下来，返回时恢复原位置（和首页一致）。
 */
object ListStateCache {
    private val map = mutableMapOf<String, LazyListState>()
    fun get(key: String): LazyListState = map.getOrPut(key) { LazyListState() }
}
