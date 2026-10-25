package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.ThreadSummary
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface ListState {
    data object Loading : ListState
    data class Error(val message: String) : ListState
    data class Content(val threads: List<ThreadSummary>) : ListState
}

/**
 * 沉浸式帖子列表（原生）：
 * - 内容铺满全屏，滚动时从悬浮顶栏下方滑过
 * - 打开即自动加载一次；切回前台也会自动刷新
 * - 右下角可拖动刷新按钮
 */
@Composable
fun ThreadListScreen(
    title: String,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    loader: suspend () -> List<ThreadSummary>,
    onOpenThread: (ThreadSummary) -> Unit,
    onOpenWeb: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ListState>(ListState.Loading) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        runCatching { loader() }
            .onSuccess {
                state = if (it.isEmpty()) ListState.Error("没有解析到帖子") else ListState.Content(it)
            }
            .onFailure { state = ListState.Error(it.message ?: "加载失败") }
    }

    // 打开页面时自动刷新一次；登录态变化（reloadKey）时重新加载
    LaunchedEffect(title, reloadKey) { load() }

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
                            load()
                        }
                    }) { Text("重试") }
                    TextButton(onClick = onOpenWeb) { Text("改用网页版") }
                }
            }

            is ListState.Content -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topInset + 56.dp,
                    bottom = 120.dp,
                ),
            ) {
                items(s.threads, key = { it.id }) { thread ->
                    ThreadRow(thread) { onOpenThread(thread) }
                    HorizontalDivider(Modifier.padding(start = 14.dp))
                }
            }
        }

        ImmersiveTopBar(
            title = title,
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
            actionLabel = "网页版",
            onAction = onOpenWeb,
        )

        DraggableRefreshFab(
            onClick = {
                scope.launch {
                    refreshing = true
                    load()
                    refreshing = false
                }
            },
        )
    }
}

@Composable
private fun ThreadRow(thread: ThreadSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val avatar = thread.avatarUrl
            if (!avatar.isNullOrBlank()) {
                AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                thread.typeLabel?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = listOfNotNull(thread.author, thread.metaText).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
