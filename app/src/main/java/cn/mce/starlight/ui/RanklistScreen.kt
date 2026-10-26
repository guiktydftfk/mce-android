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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.RanklistClient
import kotlinx.coroutines.launch

private sealed interface RankState {
    data object Loading : RankState
    data class Error(val message: String) : RankState
    data class Content(val tabs: List<RanklistClient.Tab>) : RankState
}

/** 排行榜：帖子 / 版块 / 用户 多榜单 */
@Composable
fun RanklistScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RankState>(RankState.Loading) }
    var tabIndex by remember { mutableStateOf(0) }

    suspend fun load() {
        state = RankState.Loading
        runCatching { RanklistClient.load() }
            .onSuccess {
                state = if (it.isEmpty()) RankState.Error("没有解析到榜单") else RankState.Content(it)
            }
            .onFailure { state = RankState.Error(it.message ?: "加载失败") }
    }

    LaunchedEffect(Unit) { load() }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is RankState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is RankState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    // v0.3.3 批 4：以前这里是空 lambda（{ /* retry */ }），出错后页面不可恢复
                    TextButton(onClick = { scope.launch { load() } }) { Text("重试") }
                }
            }

            is RankState.Content -> {
                val tabs = s.tabs
                val current = tabs.getOrNull(tabIndex) ?: tabs.first()

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = topInset + 64.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Text(
                                text = tab.name.ifBlank { "榜单${index + 1}" },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (index == tabIndex) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (index == tabIndex) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    )
                                    .clickable { tabIndex = index }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 110.dp),
                    ) {
                        itemsIndexed(current.items, key = { index, item -> "${item.tid ?: item.title}#$index" }) { _, item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = item.tid != null) {
                                        item.tid?.let { onOpenThread(it, item.title) }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item.rank ?: "-",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if ((item.rank?.toIntOrNull() ?: 99) <= 3) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.width(28.dp),
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                item.hot?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 44.dp))
                        }
                    }
                }
            }
        }

        ImmersiveTopBar(title = "排行榜", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)

        // 榜单页出错时不再只能退出重进
        DraggableRefreshFab(
            onClick = { scope.launch { load() } },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
