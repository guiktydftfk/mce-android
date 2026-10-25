package cn.mce.starlight.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.Post
import cn.mce.starlight.data.ThreadDetail
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface DetailState {
    data object Loading : DetailState
    data class Error(val message: String) : DetailState
    data class Content(val detail: ThreadDetail) : DetailState
}

/** 沉浸式帖子详情：楼层 + 头像 + 正文 + 正文图片 */
@Composable
fun ThreadDetailScreen(
    tid: Int,
    fallbackTitle: String,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    reloadKey: Int = 0,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { HttpForumRepository() }
    var state by remember { mutableStateOf<DetailState>(DetailState.Loading) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        runCatching { repo.loadThreadDetail(tid, 1) }
            .onSuccess {
                state = if (it.posts.isEmpty()) DetailState.Error("没有解析到楼层内容")
                else DetailState.Content(it)
            }
            .onFailure { state = DetailState.Error(it.message ?: "加载失败") }
    }

    LaunchedEffect(tid, reloadKey) { load() }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val title = (state as? DetailState.Content)?.detail?.title ?: fallbackTitle

    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            is DetailState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is DetailState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        scope.launch {
                            state = DetailState.Loading
                            load()
                        }
                    }) { Text("重试") }
                }
            }

            is DetailState.Content -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topInset + 56.dp,
                    bottom = 120.dp,
                ),
            ) {
                item {
                    Text(
                        text = s.detail.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )
                    HorizontalDivider()
                }
                items(s.detail.posts, key = { it.pid }) { post ->
                    PostItem(post)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }
        }

        ImmersiveTopBar(
            title = title,
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
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
private fun PostItem(post: Post) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val url = post.avatarUrl
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    post.author,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    listOfNotNull(post.floorLabel, post.dateText).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (post.contentText.isNotBlank()) {
            Text(text = post.contentText, style = MaterialTheme.typography.bodyMedium)
        }
        post.images.take(4).forEach { image ->
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            )
        }
        if (post.images.size > 4) {
            Text(
                "还有 ${post.images.size - 4} 张图片未显示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
