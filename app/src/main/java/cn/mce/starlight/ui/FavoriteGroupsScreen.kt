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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.PostFavoriteStore

/**
 * v0.4.6：收藏分组/备注管理页（本地数据；服务端收藏不受影响）。
 * 按分组展示，点一条进入帖子，右侧可移除本地收藏记录。
 */
@Composable
fun FavoriteGroupsScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    var items by remember { mutableStateOf(PostFavoriteStore.items()) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val grouped = remember(items) { items.groupBy { it.group } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(top = topInset + 60.dp, bottom = 90.dp, start = 10.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        "还没有本地收藏分组\n在帖子页点「收藏备注」可添加分组与备注",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            grouped.forEach { (group, list) ->
                item(key = "g-$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                items(list.size, key = { i -> "${list[i].tid}" }) { i ->
                    val item = list[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .iosGlass(14)
                            .clickable { onOpenThread(item.tid, item.title) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.note.isNotBlank()) {
                                Text(
                                    item.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TextButton(onClick = {
                            PostFavoriteStore.remove(item.tid)
                            items = PostFavoriteStore.items()
                        }) { Text("移除") }
                    }
                }
            }
        }
        ImmersiveTopBar(title = "收藏分组", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}
