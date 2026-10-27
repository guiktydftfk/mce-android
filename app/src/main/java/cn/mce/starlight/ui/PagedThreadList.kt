package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.ForumPage
import cn.mce.starlight.data.ThreadImageCache
import cn.mce.starlight.data.ThreadSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 帖子列表统一状态机（v0.3.3 批 5）。
 *
 * 首页 / 版块列表 / 我的主题三处原本各复制了一份 状态 + 刷新 + 加载更多 + 错误态 + FAB
 * （约 180 行 × 3），且各自有同一批问题：
 *  - 刷新与加载更多并发时用旧列表覆盖新结果、page 错位
 *  - 换 tab 不重置 listState（新分类沿用旧滚动位置）
 *  - 空列表被当成红色错误（没有下拉刷新通道）
 *  - 顶部同时有 PullToRefresh 指示器和手写的 LinearProgressIndicator
 *
 * 现在统一到这里：请求代际号（generation）作废旧请求、刷新/加载更多互斥、
 * 换 [resetKey] 重建状态与滚动位置、空态中性提示 + 下拉刷新、单一刷新指示器。
 */
private class PagedState {
    var items by mutableStateOf<List<ThreadSummary>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var hasNext by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var page = 1
    var generation = 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagedThreadList(
    /** 变化即重置列表：导读 tab / 版块筛选 / 外部刷新信号 */
    resetKey: Any?,
    topPadding: Dp,
    loader: suspend (page: Int) -> ForumPage,
    onOpenThread: (ThreadSummary) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    bottomPadding: Dp = 130.dp,
    emptyText: String = "暂无内容，下拉刷新试试",
    showDivider: Boolean = false,
    showRefreshFab: Boolean = true,
    /** 列表顶部固定内容（如「草稿箱」入口），空列表时也显示 */
    header: (@Composable () -> Unit)? = null,
    /** 帖子过滤（v0.3.8）：返回 false 的帖子不展示；分页仍按服务端原始页判断「还有没有下一页」 */
    filter: ((ThreadSummary) -> Boolean)? = null,
) {
    val scope = rememberCoroutineScope()
    val st = remember(resetKey) { PagedState() }
    val listState = remember(resetKey) { LazyListState() }

    // v0.3.7：缩略图改为「列表级 + 滚动停止后再抓」。
    // 旧实现每张卡片在自己的 LaunchedEffect 里抓一次完整 viewthread 页面（网络 + 整页 DOM 解析），
    // 滑动穿过一屏就并发十几次整页请求，是首页卡顿的主因。
    // 现在：滚动中完全不请求；停下后只给「可见 + 预取 3 条」批量抓，结果写入列表级 map，
    // item 滚出再滚回也不会重新请求/重新跳动。
    val thumbs = remember(resetKey) { mutableStateMapOf<Int, String>() }

    suspend fun fetchThumbWindow() {
        val info = listState.layoutInfo.visibleItemsInfo
        if (info.isEmpty()) return
        val headerOffset = if (header != null) 1 else 0
        for (index in info.first().index..(info.last().index + THUMBNAIL_PREFETCH)) {
            val thread = st.items.getOrNull(index - headerOffset) ?: continue
            if (thumbs.containsKey(thread.id)) continue
            val url = runCatching { ThreadImageCache.firstImage(thread.id) }.getOrNull()
            thumbs[thread.id] = url.orEmpty() // 空串 = 已查过、该帖没有图
        }
    }

    LaunchedEffect(listState, resetKey, header) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) return@collectLatest
                fetchThumbWindow()
            }
    }

    // 首屏：snapshotFlow 的初值可能在列表完成布局前就发出（那时 visibleItemsInfo 为空），
    // 所以数据到达后再补一次延迟抓取，保证不滚动也能看到首屏缩略图
    LaunchedEffect(st.items, resetKey) {
        if (st.items.isEmpty()) return@LaunchedEffect
        delay(350)
        if (!listState.isScrollInProgress) fetchThumbWindow()
    }

    fun applyFilter(list: List<ThreadSummary>): List<ThreadSummary> =
        if (filter == null) list else list.filter(filter)

    suspend fun loadFirst() {
        val gen = ++st.generation
        st.loading = st.items.isEmpty()
        st.error = null
        runCatching { loader(1) }
            .onSuccess { page ->
                if (gen != st.generation) return@onSuccess
                st.page = 1
                st.items = applyFilter(page.threads)
                // 用服务端原始页判断是否还有下一页（过滤后可能本页全被隐藏）
                st.hasNext = page.threads.isNotEmpty()
                st.error = null
            }
            .onFailure { if (gen == st.generation) st.error = it.message ?: "加载失败" }
        if (gen == st.generation) st.loading = false
    }

    suspend fun loadMore() {
        // v0.3.8：不要求 st.items 非空 —— 首页被过滤空了也要能继续往后翻
        if (!st.hasNext || st.loadingMore || st.refreshing) return
        val gen = st.generation
        st.loadingMore = true
        // 过滤可能把整页都隐藏：连续补抓不超过 3 页，避免列表「静默停住」
        var attempts = 0
        while (attempts < 3 && gen == st.generation && st.hasNext) {
            attempts++
            var added = false
            runCatching { loader(st.page + 1) }
                .onSuccess { page ->
                    if (gen != st.generation) return@onSuccess
                    if (page.threads.isEmpty()) {
                        st.hasNext = false
                    } else {
                        val merged = (st.items + applyFilter(page.threads)).distinctBy { it.id }
                        added = merged.size > st.items.size
                        st.items = merged
                        st.page += 1
                        st.hasNext = true
                    }
                }
                .onFailure { if (gen == st.generation) st.message = "加载更多失败，请下拉刷新重试" }
            if (added || !st.hasNext) break
        }
        st.loadingMore = false
    }

    fun refresh() {
        if (st.refreshing) return
        st.refreshing = true
        scope.launch {
            val gen = ++st.generation
            runCatching { loader(1) }
                .onSuccess { page ->
                    if (gen != st.generation) {
                        st.refreshing = false
                        return@onSuccess
                    }
                    st.page = 1
                    st.items = applyFilter(page.threads)
                    st.hasNext = page.threads.isNotEmpty()
                    st.error = null
                    st.message = null
                    runCatching { listState.requestScrollToItem(0) }
                }
                .onFailure {
                    if (gen == st.generation) st.message = "刷新失败：" + (it.message ?: "网络错误")
                }
            st.refreshing = false
        }
    }

    LaunchedEffect(resetKey) { loadFirst() }

    // 触底自动加载（列表为空/没有下一页时不挂监听，避免空转）
    LaunchedEffect(listState, st.hasNext, resetKey) {
        if (!st.hasNext) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                // header 会占掉 LazyColumn 的第 0 项，阈值要补偏移
                val headerOffset = if (header != null) 1 else 0
                if (st.items.isNotEmpty() && last >= headerOffset + st.items.size - 3) loadMore()
            }
    }

    // 失败提示 3 秒后自动消失
    LaunchedEffect(st.message) {
        if (st.message != null) {
            delay(3000)
            st.message = null
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            // v0.3.7：首屏用骨架屏代替整屏转圈，减少"转圈一闪"的闪烁感
            st.loading && st.items.isEmpty() -> ThreadListSkeleton(
                topPadding = topPadding,
                horizontalPadding = horizontalPadding,
            )

            st.items.isEmpty() && st.error != null -> ErrorBlock(st.error!!) {
                scope.launch { loadFirst() }
            }

            else -> PullToRefreshBox(
                isRefreshing = st.refreshing,
                onRefresh = { refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topPadding,
                        bottom = bottomPadding,
                        start = horizontalPadding,
                        end = horizontalPadding,
                    ),
                ) {
                    header?.let { head -> item { head() } }
                    if (st.items.isEmpty()) {
                        item { EmptyHint(emptyText) }
                    }
                    itemsIndexed(
                        items = st.items,
                        key = { _, thread -> thread.id },
                        contentType = { _, _ -> "thread" },
                    ) { _, thread ->
                        FeedCard(
                            thread = thread,
                            thumbUrl = thumbs[thread.id],
                            onClick = { onOpenThread(thread) },
                        )
                        if (showDivider) CardDivider()
                    }
                    when {
                        st.loadingMore -> item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                Alignment.Center,
                            ) { CircularProgressIndicator(Modifier.size(20.dp)) }
                        }

                        st.items.isNotEmpty() && !st.hasNext -> item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                Alignment.Center,
                            ) {
                                Text(
                                    "没有更多了",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        st.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        if (showRefreshFab) {
            DraggableRefreshFab(
                onClick = { refresh() },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}
