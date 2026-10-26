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
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.ThreadImageCache
import cn.mce.starlight.data.ThreadSummary
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface FeedState {
    data object Loading : FeedState
    data class Error(val message: String) : FeedState
    data class Content(val threads: List<ThreadSummary>) : FeedState
}

/** 导读分类（对应 forum.php?mod=guide&view=xxx） */
private val GUIDE_TABS = listOf(
    "newthread" to "最新",
    "hot" to "热帖",
    "digest" to "精华",
    "sofa" to "沙发",
)

/**
 * 首页动态流（借鉴鸿蒙版设计）：
 * 卡片式布局 —— 头像/作者 + 版块标签 + 时间、标题、摘要、大图、分类与回复数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    onOpenThread: (ThreadSummary) -> Unit,
    onCompose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { HttpForumRepository() }
    val listState = rememberLazyListState()

    var view by remember { mutableStateOf("newthread") }
    var state by remember { mutableStateOf<FeedState>(FeedState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var hasNext by remember { mutableStateOf(true) }

    suspend fun loadFirst() {
        runCatching { repo.loadGuide(view, 1) }
            .onSuccess {
                page = 1
                hasNext = it.threads.isNotEmpty()
                state = if (it.threads.isEmpty()) FeedState.Error("没有解析到帖子")
                else FeedState.Content(it.threads)
            }
            .onFailure { state = FeedState.Error(it.message ?: "加载失败") }
    }

    suspend fun loadMore() {
        if (!hasNext || loadingMore || refreshing) return
        val current = (state as? FeedState.Content)?.threads ?: return
        loadingMore = true
        runCatching { repo.loadGuide(view, page + 1) }
            .onSuccess { next ->
                if (next.threads.isEmpty()) {
                    hasNext = false
                } else {
                    page += 1
                    val merged = (current + next.threads).distinctBy { it.id }
                    if (merged.size == current.size) hasNext = false
                    state = FeedState.Content(merged)
                }
            }
        loadingMore = false
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            // 先回到最顶部，再刷新（用户要求的刷新语义）
            runCatching { listState.scrollToItem(0) }
            loadFirst()
            refreshing = false
        }
    }

    LaunchedEffect(view, reloadKey) {
        state = FeedState.Loading
        loadFirst()
    }

    LaunchedEffect(listState, hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val total = (state as? FeedState.Content)?.threads?.size ?: 0
                if (hasNext && total > 0 && last >= total - 2) loadMore()
            }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is FeedState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is FeedState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = {
                        scope.launch {
                            state = FeedState.Loading
                            loadFirst()
                        }
                    }) { Text("重试") }
                }
            }

            is FeedState.Content -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topInset + 104.dp,
                        bottom = 130.dp,
                        start = 12.dp,
                        end = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(s.threads, key = { _, t -> t.id }) { index, thread ->
                        FeedCard(thread, allowThumbnail = index < 6) { onOpenThread(thread) }
                    }
                    if (loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        ImmersiveTopBar(
            title = "全部最新",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
            actionLabel = "发帖",
            onAction = onCompose,
        )

        // 导读分类
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset + 52.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GUIDE_TABS.forEach { (value, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (view == value) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (view == value) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { if (view != value) view = value }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
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
            onClick = { refresh() },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
