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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cn.mce.starlight.data.PersonalClient
import cn.mce.starlight.data.ThreadSummary
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface PState<out T> {
    data object Loading : PState<Nothing>
    data class Error(val message: String) : PState<Nothing>
    data class Content<T>(val items: List<T>) : PState<T>
}

@Composable
private fun topPadding(): androidx.compose.ui.unit.Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** 我的主题：与首页一致的卡片流 */
@Composable
fun MyThreadsScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PState<ThreadSummary>>(PState.Loading) }
    val listState = rememberLazyListState()
    var page by remember { mutableStateOf(1) }
    var hasNext by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun loadFirst() {
        runCatching { PersonalClient.loadMyThreads(1) }
            .onSuccess {
                page = 1
                hasNext = it.threads.isNotEmpty()
                state = if (it.threads.isEmpty()) PState.Error("没有主题") else PState.Content(it.threads)
            }
            .onFailure { state = PState.Error(it.message ?: "加载失败") }
    }

    suspend fun loadMore() {
        if (!hasNext || loadingMore) return
        val current = (state as? PState.Content<ThreadSummary>)?.items ?: return
        loadingMore = true
        runCatching { PersonalClient.loadMyThreads(page + 1) }.onSuccess { next ->
            if (next.threads.isEmpty()) hasNext = false else {
                page += 1
                val merged = (current + next.threads).distinctBy { it.id }
                if (merged.size == current.size) hasNext = false
                state = PState.Content(merged)
            }
        }
        loadingMore = false
    }

    LaunchedEffect(Unit) { loadFirst() }
    LaunchedEffect(listState, hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            val total = (state as? PState.Content<ThreadSummary>)?.items?.size ?: 0
            if (hasNext && total > 0 && last >= total - 2) loadMore()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is PState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is PState.Error -> ErrorBlock(s.message) { scope.launch { state = PState.Loading; loadFirst() } }
            is PState.Content -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = topPadding() + 64.dp, bottom = 120.dp, start = 14.dp, end = 14.dp),
            ) {
                items(s.items.size) { index ->
                    val thread = s.items[index]
                    FeedCard(thread = thread, onClick = { onOpenThread(thread.id, thread.title) })
                    CardDivider()
                }
                if (loadingMore) {
                    item { Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) } }
                }
            }
        }
        ImmersiveTopBar(title = "我的主题", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

/** 我的回复 */
@Composable
fun MyRepliesScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PState<PersonalClient.ReplyItem>>(PState.Loading) }

    suspend fun load() {
        runCatching { PersonalClient.loadMyReplies(1) }
            .onSuccess { state = if (it.isEmpty()) PState.Error("没有回复") else PState.Content(it) }
            .onFailure { state = PState.Error(it.message ?: "加载失败") }
    }
    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is PState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is PState.Error -> ErrorBlock(s.message) { scope.launch { state = PState.Loading; load() } }
            is PState.Content -> LazyColumn(
                contentPadding = PaddingValues(top = topPadding() + 64.dp, bottom = 100.dp, start = 14.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(s.items.size) { i ->
                    val item = s.items[i]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onOpenThread(item.tid, item.title) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.content.isNotBlank()) {
                            Text(item.content, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
                                overflow = TextOverflow.Ellipsis)
                        }
                        item.time?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        ImmersiveTopBar(title = "我的回复", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

/** 我的收藏 */
@Composable
fun FavoritesScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PState<PersonalClient.FavoriteItem>>(PState.Loading) }

    suspend fun load() {
        runCatching { PersonalClient.loadFavorites(1) }
            .onSuccess { state = if (it.isEmpty()) PState.Error("没有收藏") else PState.Content(it) }
            .onFailure { state = PState.Error(it.message ?: "加载失败") }
    }
    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is PState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is PState.Error -> ErrorBlock(s.message) { scope.launch { state = PState.Loading; load() } }
            is PState.Content -> LazyColumn(
                contentPadding = PaddingValues(top = topPadding() + 64.dp, bottom = 100.dp),
            ) {
                items(s.items.size) { i ->
                    val item = s.items[i]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(item.tid, item.title) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        item.forumName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        ImmersiveTopBar(title = "我的收藏", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

/** 消息提醒（重做：头像 + 作者 + 时间 + 内容） */
@Composable
fun NoticesScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PState<PersonalClient.NoticeItem>>(PState.Loading) }

    suspend fun load() {
        runCatching { PersonalClient.loadNotices(1) }
            .onSuccess { state = if (it.isEmpty()) PState.Error("暂无消息") else PState.Content(it) }
            .onFailure { state = PState.Error(it.message ?: "加载失败") }
    }
    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is PState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is PState.Error -> ErrorBlock(s.message) { scope.launch { state = PState.Loading; load() } }
            is PState.Content -> LazyColumn(
                contentPadding = PaddingValues(top = topPadding() + 64.dp, bottom = 100.dp),
            ) {
                items(s.items.size) { i ->
                    val item = s.items[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                item.tid?.let { onOpenThread(it, item.note?.take(20) ?: "消息") }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            item.avatar?.let {
                                AsyncImage(model = it, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.author ?: "通知", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                                item.time?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            item.note?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 68.dp))
                }
            }
        }
        ImmersiveTopBar(title = "消息提醒", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}
