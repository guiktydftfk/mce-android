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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.SearchClient
import kotlinx.coroutines.launch

/**
 * v0.4.3：「TA 的全部主题」。
 *
 * 站点的「他人主题列表」页对客户端返回提示信息页（不可用），因此改走**网页端按作者搜索接口**
 * （`search.php` + `searchuser=昵称`），可以分页拿到该用户的全部主题。
 */
@Composable
fun UserThreadsScreen(
    name: String,
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SearchClient.Hit>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    suspend fun load(next: Int) {
        if (loading) return
        loading = true
        error = null
        runCatching { SearchClient.searchThreadsByAuthor(name, next) }
            .onSuccess { list ->
                page = next
                val known = items.mapTo(HashSet()) { it.tid }
                items = items + list.filterNot { it.tid in known }
                if (list.isEmpty() && next == 1) error = "没有查到这个用户的主题（或搜索被限制）"
            }
            .onFailure { error = it.message ?: "搜索失败" }
        loading = false
    }

    LaunchedEffect(name) { if (items.isEmpty()) load(1) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(top = topInset + 64.dp, bottom = 110.dp, start = 14.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "${item.tid}#$index" }) { _, hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .iosGlass(12)
                        .clickable { onOpenThread(hit.tid, hit.title) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        hit.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.padding(12.dp))
                    } else {
                        TextButton(onClick = { scope.launch { load(page + 1) } }) {
                            Text(if (page <= 1) "加载更多" else "继续加载（第 ${page + 1} 页）")
                        }
                    }
                }
            }
            error?.let { msg ->
                item {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Text(
                            "可改用网页版搜索：搜索 → 高级搜索 → 作者",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
