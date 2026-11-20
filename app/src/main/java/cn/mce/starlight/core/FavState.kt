package cn.mce.starlight.core

/**
 * v0.4.8：收藏状态的内存提示。
 *
 * 帖子详情页可能命中磁盘缓存（收藏/取消收藏之前的 HTML），仅靠解析页面会显示旧状态；
 * 这里把用户操作过的状态与「从收藏列表进入」的提示记在内存里，保证按钮文字与操作一致。
 */
object FavState {
    private val overrides = mutableMapOf<Int, Boolean>()

    fun set(tid: Int, favorited: Boolean) { overrides[tid] = favorited }

    fun get(tid: Int): Boolean? = overrides[tid]
}