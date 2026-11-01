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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.UserThreadsClient
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * v0.4.3：「TA 的主题」列表 —— 与首页信息流一致的卡片样式。
 *
 * 数据：电脑版空间主题页（见 [UserThreadsClient]），只解析主题表格，
 * 卡片展示：版块标签 / 最后发帖人 / 时间 / 标题 / 回复·查看 数。
 */
@Composable
fun UserThreadsScreen(
    uid: Int,
    name: String,
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // v0.4.3：返回时恢复列表与滚动位置（打开帖子会销毁本页）
    val cached = remember(uid) { cn.mce.starlight.core.UserThreadsCache.get(uid) }
    var items by remember { mutableStateOf(cached?.items ?: emptyList()) }
    var page by remember { mutableStateOf(cached?.page ?: 0) }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(cached?.endReached ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = cn.mce.starlight.core.ListStateCache.get("user_threads_$uid")

    suspend fun load(next: Int) {
        if (loading || endReached) return
        loading = true
        error = null
        runCatching { UserThreadsClient.load(uid, next) }
            .onSuccess { list ->
                page = next
                val known = items.mapTo(HashSet()) { it.tid }
                val fresh = list.filterNot { it.tid in known }
                items = items + fresh
                // 没有新内容（或本页不足一屏）→ 视为到底
                if (fresh.isEmpty()) {
                    endReached = true
                    if (next == 1) error = "没有查到主题"
                }
            }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    LaunchedEffect(uid) { if (items.isEmpty()) load(1) }

    LaunchedEffect(items, page, endReached) {
        cn.mce.starlight.core.UserThreadsCache.put(uid, items, page, endReached)
    }

    // v0.4.3：瀑布流式自动加载 —— 滚到接近底部就自动拉下一页，无需点「加载更多」
    LaunchedEffect(listState, items.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (total > 0 && last >= total - 5 && !loading && !endReached) {
                    load(page + 1)
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topInset + 60.dp, bottom = 110.dp, start = 10.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "${item.tid}#$index" }) { _, item ->
                UserThreadCard(item) { onOpenThread(item.tid, item.title) }
            }
            item {
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.padding(12.dp))
                        endReached -> Text(
                            "没有更多了",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                        error != null -> TextButton(onClick = { scope.launch { load(page + 1) } }) {
                            Text("加载失败，点击重试")
                        }
                        else -> Text(
                            "上滑加载更多…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            error?.let { msg ->
                item {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        ImmersiveTopBar(
            title = "$name 的主题",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}

@Composable
private fun UserThreadCard(item: UserThreadsClient.Item, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .iosGlass(16)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.board?.let { board ->
                Text(
                    "#$board",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            item.lastPoster?.let { poster ->
                Text(
                    poster,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
            }
            item.time?.let { time ->
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.replies?.let { replies ->
                Text(
                    "回复 $replies",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
            }
            item.views?.let { views ->
                Text(
                    "查看 $views",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
