package cn.mce.starlight.ui

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.SearchClient
import kotlinx.coroutines.launch

/** 原生搜索页 */
@Composable
fun SearchScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf(emptyList<SearchClient.Hit>()) }
    var error by remember { mutableStateOf<String?>(null) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = topInset + 56.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("搜索帖子") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    enabled = !loading && keyword.isNotBlank(),
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            runCatching { SearchClient.search(keyword.trim()) }
                                .onSuccess {
                                    hits = it
                                    searched = true
                                    if (it.isEmpty()) error = "没有搜到结果"
                                }
                                .onFailure { error = it.message ?: "搜索失败" }
                            loading = false
                        }
                    },
                ) { Icon(Icons.Filled.Search, contentDescription = "搜索") }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(hits, key = { it.tid }) { hit ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenThread(hit.tid, hit.title) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                hit.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    if (searched && hits.isNotEmpty()) {
                        item {
                            TextButton(onClick = { /* 已展示全部 */ }) {
                                Text("共 ${hits.size} 条结果", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        ImmersiveTopBar(
            title = "搜索",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}
