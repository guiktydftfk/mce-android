package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.ForumPage
import cn.mce.starlight.data.ThreadImageCache
import cn.mce.starlight.data.ThreadSummary
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface ListState {
    data object Loading : ListState
    data class Error(val message: String) : ListState
    data class Content(val threads: List<ThreadSummary>) : ListState
}

private val FILTERS = listOf(
    null to "全部",
    "lastpost" to "最新",
    "heat" to "热门",
    "digest" to "精华",
)

/**
 * 沉浸式帖子列表：下拉刷新 + 上拉自动加载更多 + 版块筛选。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ListState>(ListState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var filter by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()


    suspend fun loadFirst() {
        runCatching { loader(1, filter) }
            .onSuccess {
                page = 1
                // 无法可靠判断"是否有下一页"时，默认认为还有，直到某一页为空
                hasNext = it.threads.isNotEmpty()
                state = if (it.threads.isEmpty()) ListState.Error("没有解析到帖子")
                else ListState.Content(it.threads)
            }
            .onFailure { state = ListState.Error(it.message ?: "加载失败") }
    }

    suspend fun loadMore() {
        if (!hasNext || loadingMore || refreshing) return
        val current = (state as? ListState.Content)?.threads ?: return
        loadingMore = true
        runCatching { loader(page + 1, filter) }
            .onSuccess { next ->
                if (next.threads.isEmpty()) {
                    hasNext = false
                } else {
                    page += 1
                    val merged = (current + next.threads).distinctBy { it.id }
                    // 如果合并后没有新增，说明到底了
                    if (merged.size == current.size) hasNext = false
                    state = ListState.Content(merged)
                }
            }
        loadingMore = false
    }
    // 刷新时保持当前滚动位置（不回顶）
    fun refreshKeepPosition() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            // 先回到最顶部，再刷新
            runCatching { listState.scrollToItem(0) }
            loadFirst()
            refreshing = false
        }
    }

    LaunchedEffect(title, reloadKey, filter) {
        state = ListState.Loading
        loadFirst()
    }

    // 触底自动加载
    LaunchedEffect(listState, hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val total = (state as? ListState.Content)?.threads?.size ?: 0
                if (hasNext && total > 0 && last >= total - 3) loadMore()
            }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            is ListState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is ListState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        scope.launch {
                            state = ListState.Loading
                            loadFirst()
                        }
                    }) { Text("重试") }
                }
            }

            is ListState.Content -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshKeepPosition() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topInset + if (enableFilters) 100.dp else 56.dp,
                        bottom = 140.dp,
                    ),
                ) {
                    itemsIndexed(s.threads, key = { _, t -> t.id }) { index, thread ->
                        FeedCard(thread = thread, allowThumbnail = index < 6, onClick = { onOpenThread(thread) })
                        CardDivider()
                    }
                    if (loadingMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                Alignment.Center,
                            ) { CircularProgressIndicator(Modifier.size(22.dp)) }
                        }
                    } else if (!hasNext) {
                        item {
                            Text(
                                "没有更多了",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
                }
            }
        }

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
                            .clickable { filter = value }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (refreshing) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = topInset + 46.dp),
            )
        }

        DraggableRefreshFab(
            onClick = { refreshKeepPosition() },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
