package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.ForumPage
import cn.mce.starlight.data.ThreadSummary

private val FILTERS = listOf(
    null to "全部",
    "lastpost" to "最新",
    "heat" to "热门",
    "digest" to "精华",
)

/**
 * 沉浸式帖子列表：下拉刷新 + 上拉自动加载更多 + 版块筛选。
 *
 * v0.3.3 批 5：与首页共用 [PagedThreadList]（状态机/刷新/加载更多/空态/滚动重置），
 * 换筛选条件时列表和滚动位置一起重置，去掉重复的顶部进度条。
 */
@Composable
fun ThreadListScreen(
    title: String,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    enableFilters: Boolean = false,
    loader: suspend (page: Int, filter: String?) -> ForumPage,
    onOpenThread: (ThreadSummary) -> Unit,
    onCompose: (() -> Unit)? = null,
) {
    var filter by remember { mutableStateOf<String?>(null) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val loadPage: suspend (Int) -> ForumPage = { page -> loader(page, filter) }
    // v0.3.10：版块列表也用缓存（返回时秒开）
    val cacheKey = "board_${title}_${filter ?: "all"}"
    val cached = remember(cacheKey, reloadKey) { cn.mce.starlight.core.FeedCache.get(cacheKey) }

    Box(modifier.fillMaxSize()) {
        PagedThreadList(
            resetKey = Triple(title, filter, reloadKey),
            topPadding = topInset + if (enableFilters) 100.dp else 56.dp,
            showDivider = true,
            emptyText = "这个版块还没有帖子，下拉刷新试试",
            loader = loadPage,
            initialItems = cached?.threads.orEmpty(),
            initialHasNext = cached?.hasNext ?: true,
            onFirstPage = { page -> cn.mce.starlight.core.FeedCache.put(cacheKey, page) },
            onOpenThread = onOpenThread,
        )

        ImmersiveTopBar(
            title = title,
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
            actionLabel = if (onCompose != null) "发帖" else null,
            onAction = onCompose,
        )

        if (enableFilters) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 52.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FILTERS.forEach { (value, label) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (filter == value) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (filter == value) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .clickable { if (filter != value) filter = value }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
