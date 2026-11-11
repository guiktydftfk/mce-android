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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.BoardFavorites

/** v0.4.5：收藏板块管理（点击进入板块，右侧可取消收藏） */
@Composable
fun BoardFavoritesScreen(
    onMenu: () -> Unit,
    onOpenBoard: (Int, String) -> Unit,
) {
    var favorites by remember { mutableStateOf(BoardFavorites.favorites) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(top = topInset.coerceAtMost(androidx.compose.ui.unit.Dp(96f)) + 60.dp, bottom = 90.dp, start = 10.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (favorites.isEmpty()) {
                item {
                    Text(
                        "还没有收藏板块\n在侧栏长按板块即可收藏",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(favorites.entries.toList(), key = { it.key }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .iosGlass(14)
                        .clickable { onOpenBoard(entry.key, entry.value) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "\u2605 " + entry.value,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        BoardFavorites.remove(entry.key)
                        favorites = BoardFavorites.favorites
                    }) { Text("取消收藏") }
                }
            }
        }
        ImmersiveTopBar(title = "收藏板块", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}
