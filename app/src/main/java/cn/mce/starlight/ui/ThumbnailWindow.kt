package cn.mce.starlight.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/** 缩略图预取条数：当前可见条目之后再多拉几条 */
const val THUMBNAIL_PREFETCH = 3

/**
 * 缩略图加载窗口：只对「可见 + 之后 [prefetch] 条」加载缩略图。
 *
 * 旧实现用 `allowThumbnail = index < 8`（绝对下标）：首屏 8 条无论是否可见都立刻发起
 * 整页抓取，而第 9 条之后即使独占整屏也永远没有缩略图。
 * 这里用 `derivedStateOf` 读 `layoutInfo`，只在窗口真正变化时触发重组。
 */
@Composable
fun rememberThumbnailWindow(
    listState: LazyListState,
    prefetch: Int = THUMBNAIL_PREFETCH,
): State<IntRange> = remember(listState, prefetch) {
    derivedStateOf {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) {
            0..-1
        } else {
            visible.first().index..(visible.last().index + prefetch)
        }
    }
}
