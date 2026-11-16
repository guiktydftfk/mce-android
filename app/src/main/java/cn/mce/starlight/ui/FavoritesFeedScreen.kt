package cn.mce.starlight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.BoardFavorites
import cn.mce.starlight.core.FeedTime
import cn.mce.starlight.core.UiError
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.ThreadSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * v0.4.8：关注板块聚合页。
 *
 * 并发拉取所有收藏板块第 1 页，按发帖时间合并去重后展示；
 * 入口在侧栏「关注板块」（与收藏板块管理同区）。
 */
@Composable
fun FavoritesFeedScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val favorites = BoardFavorites.favorites
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var threads by remember { mutableStateOf<List<ThreadSummary>>(emptyList()) }

    suspend fun load() {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                favorites.keys.take(12).map { fid ->
                    async {
                        runCatching { HttpForumRepository().loadThreads(fid, 1) }
                            .getOrNull()?.threads.orEmpty()
                    }
                }.awaitAll().flatten()
            }.distinctBy { it.id }
                .sortedByDescending { FeedTime.parseApproxMs(it.metaText) ?: 0L }
        }.onSuccess { threads = it }
            .onFailure { error = UiError.friendly(it) }
        loading = false
    }

    LaunchedEffect(favorites.keys.toList()) { load() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error!!, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { scope.launch { load() } }) { Text("重试") }
            }
            favorites.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "还没有收藏板块：长按侧栏里的板块即可收藏，之后这里聚合显示关注板块的新帖",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            threads.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "收藏板块暂时没有新帖",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = clampTopInset(topInset) + 96.dp,
                    bottom = 110.dp,
                ),
            ) {
                item {
                    Text(
                        "关注板块 · ${favorites.size} 个",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                items(threads, key = { it.id }) { thread ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(thread.id, thread.title) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                thread.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOfNotNull(
                                thread.forumName,
                                thread.author,
                                thread.metaText,
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        ImmersiveTopBar(
            title = "关注板块",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}
