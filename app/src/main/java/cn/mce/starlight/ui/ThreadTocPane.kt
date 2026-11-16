package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * v0.4.8：大屏三栏时右侧的「目录」栏。
 * 读取 [cn.mce.starlight.core.ThreadTocState]（详情页同步的楼层列表），
 * 点击条目通过 [cn.mce.starlight.core.ThreadScrollRequest] 让详情页滚动到对应楼层。
 */
@Composable
fun ThreadTocPane(modifier: Modifier = Modifier) {
    val posts = cn.mce.starlight.core.ThreadTocState.posts
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Text(
            "目录（${posts.size} 楼）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        if (posts.isEmpty()) {
            Text(
                "打开帖子后显示楼层目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(posts, key = { it.pid }) { post ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { cn.mce.starlight.core.ThreadScrollRequest.pid = post.pid }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            "${post.floorLabel ?: "楼主"} · ${post.author}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            post.contentText.replace("\n", " ").take(30),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}