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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.UiError
import cn.mce.starlight.data.Net
import cn.mce.starlight.data.SpaceClient
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * v0.4.8：个人空间扩展页 —— 日志 / 相册 / 留言板 / 访客。
 * 数据来自电脑版个人空间子页；空结果或打不开时可「在网页中打开」兜底。
 */
@Composable
fun SpaceScreen(
    uid: Int?,
    name: String,
    onMenu: () -> Unit,
    onOpenPage: (String, String) -> Unit = { _, _ -> },
    onOpenUser: (Int, String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val tabs = listOf("日志" to "blog", "相册" to "album", "留言板" to "wall", "访客" to "visitor")
    var tabIndex by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var blogs by remember { mutableStateOf<List<SpaceClient.BlogItem>?>(null) }
    var albums by remember { mutableStateOf<List<SpaceClient.AlbumItem>?>(null) }
    var wall by remember { mutableStateOf<List<SpaceClient.WallItem>?>(null) }
    var visitors by remember { mutableStateOf<List<SpaceClient.VisitorItem>?>(null) }

    suspend fun load(tab: String) {
        val u = uid ?: run { error = "缺少用户 ID"; return }
        loading = true
        error = null
        runCatching {
            when (tab) {
                "blog" -> blogs = SpaceClient.loadBlogs(u)
                "album" -> albums = SpaceClient.loadAlbums(u)
                "wall" -> wall = SpaceClient.loadWall(u)
                else -> visitors = SpaceClient.loadVisitors(u)
            }
        }.onFailure { error = UiError.friendly(it) }
        loading = false
    }

    LaunchedEffect(uid, tabIndex) { load(tabs[tabIndex].second) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 96.dp, start = 14.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { i, (label, _) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (tabIndex == i) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (tabIndex == i) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { tabIndex = i }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else if (error != null) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { scope.launch { load(tabs[tabIndex].second) } }) { Text("重试") }
                    uid?.let {
                        TextButton(onClick = {
                            onOpenPage(
                                "${Net.BASE}/home.php?mod=space&uid=$it&do=${tabs[tabIndex].second}&mobile=no",
                                "$name 的${tabs[tabIndex].first}",
                            )
                        }) { Text("在网页中打开") }
                    }
                }
            } else {
                val current = when (tabIndex) {
                    0 -> blogs
                    1 -> albums
                    2 -> wall
                    else -> visitors
                }
                if (current.isNullOrEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("${tabs[tabIndex].first}暂无内容", style = MaterialTheme.typography.bodyMedium)
                        uid?.let {
                            TextButton(onClick = {
                                onOpenPage(
                                    "${Net.BASE}/home.php?mod=space&uid=$it&do=${tabs[tabIndex].second}&mobile=no",
                                    "$name 的${tabs[tabIndex].first}",
                                )
                            }) { Text("在网页中打开") }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp)) {
                        when (tabIndex) {
                            0 -> items(blogs.orEmpty(), key = { it.id }) { item ->
                                SpaceRow(
                                    title = item.title,
                                    subtitle = item.date,
                                ) {
                                    onOpenPage(
                                        "${Net.BASE}/home.php?mod=space&uid=$uid&do=blog&id=${item.id}&mobile=no",
                                        item.title,
                                    )
                                }
                            }
                            1 -> items(albums.orEmpty(), key = { it.id }) { item ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onOpenPage(
                                                "${Net.BASE}/home.php?mod=space&uid=$uid&do=album&id=${item.id}&mobile=no",
                                                item.title,
                                            )
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    item.cover?.let { url ->
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                        item.countText?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start = 82.dp))
                            }
                            2 -> items(wall.orEmpty().size, key = { i -> "w$i" }) { i ->
                                val item = wall.orEmpty()[i]
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    item.avatar?.let { url ->
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                item.author ?: "匿名",
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                            item.time?.let {
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(
                                            item.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 6,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start = 60.dp))
                            }
                            else -> items(visitors.orEmpty(), key = { it.uid }) { item ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenUser(item.uid, item.name) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    item.avatar?.let { url ->
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.weight(1f))
                                    item.time?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start = 60.dp))
                            }
                        }
                    }
                }
            }
        }
        ImmersiveTopBar(
            title = "$name 的空间",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}

@Composable
private fun SpaceRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(Modifier.padding(start = 14.dp))
}
