package cn.mce.starlight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.RecentThreads
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v0.4.4：最近浏览（本地记录，点一行回到该帖） */
@Composable
fun RecentThreadsScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    var items by remember { mutableStateOf(RecentThreads.items()) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(top = topInset + 60.dp, bottom = 100.dp, start = 10.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        "还没有浏览记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(items, key = { "${it.tid}-${it.at}" }) { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .iosGlass(14)
                        .clickable { onOpenThread(item.tid, item.title) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        fmt.format(Date(item.at)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        TextButton(onClick = {
                            RecentThreads.clear()
                            items = emptyList()
                        }) { Text("清空浏览记录") }
                    }
                }
            }
        }
        ImmersiveTopBar(title = "最近浏览", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}