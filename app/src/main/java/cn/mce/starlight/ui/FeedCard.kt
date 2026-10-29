package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SettingsStore
import cn.mce.starlight.data.ThreadSummary
import coil.compose.AsyncImage

/**
 * 信息流卡片（首页与各板块列表共用）。
 * 结构：头像 + 作者 + 分类标签 + 时间 / 标题 / 摘要 / 大图 / 回复数 + 版块
 * 注意：不使用卡片底色，只在内容间留白（视觉分层靠间距与图片）。
 */
@Composable
fun FeedCard(
    thread: ThreadSummary,
    modifier: Modifier = Modifier,
    showBoard: Boolean = true,
    /**
     * 缩略图 URL（v0.3.7 起由列表层统一管理：滚动停止后再批量抓取，命中后写入列表级 map）。
     * null = 还没抓过；"" = 抓过但该帖没有图。
     */
    thumbUrl: String? = null,
    onClick: () -> Unit,
) {
    // v0.4.1：与帖子详情一致的卡片化（圆角 + 浅底色 + 卡片间距），不再靠纯留白分层
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .iosGlass(16),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val avatar = thread.avatarUrl
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Text(thread.author ?: "匿名", style = MaterialTheme.typography.labelMedium)
            thread.typeLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                thread.metaText ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = thread.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        thread.summary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 缩略图：URL 由列表层在滚动停止后统一抓取（见 PagedThreadList），这里只负责渲染。
        // v0.3.9 低数据流量模式：开启后整卡不渲染配图（列表层同时不再抓取缩略图）。
        if (!SettingsStore.lowDataMode && !thumbUrl.isNullOrBlank()) {
            AsyncImage(
                // v0.4.1：按展示尺寸解码（不再整图解码）+ 关闭淡入，首页图更快出
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(thumbUrl)
                    .size(720)
                    .crossfade(false)
                    .memoryCacheKey(thumbUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            thread.replies?.let {
                Text(
                    "回复 $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (showBoard) {
                thread.forumName?.let {
                    Text(
                        "#$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    }
}

/** 列表分割用细线（可选） */
@Composable
fun CardDivider() {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}
