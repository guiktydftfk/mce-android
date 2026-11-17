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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.UiError
import cn.mce.starlight.data.OnlineClient
import coil.compose.AsyncImage

/**
 * v0.4.8：在线会员 / 论坛在线统计页。
 * 数据来自电脑版 `forum.php?mod=online`（手机模板没有该页）。
 */
@Composable
fun OnlineMembersScreen(
    onMenu: () -> Unit,
    onOpenUser: (Int, String) -> Unit = { _, _ -> },
) {
    var state by remember { mutableStateOf<OnlineClient.OnlinePage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        error = null
        state = null
        runCatching { OnlineClient.load() }
            .onSuccess { state = it }
            .onFailure { error = UiError.friendly(it) }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when {
            error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "重试",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable { reloadKey++ },
                    )
                }
            }
            state == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val page = state!!
                // v0.4.8：结构化统计行（不再原样展示图例+昵称长串）
                val statsLine = buildString {
                    page.total?.let { append("$it 人在线") }
                    page.memberCount?.let { append(" · $it 会员") }
                    page.hiddenCount?.takeIf { it > 0 }?.let { append("（$it 隐身）") }
                    page.guestCount?.let { append(" · $it 游客") }
                    page.maxOnline?.let {
                        append(" · 最高 $it")
                        page.maxOnlineDate?.let { d -> append("（$d）") }
                    }
                    if (isEmpty()) append(page.rawStats ?: page.caption)
                }
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = clampTopInset(topInset) + 96.dp,
                        bottom = 110.dp,
                        start = 12.dp,
                        end = 12.dp,
                    ),
                ) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(page.caption, style = MaterialTheme.typography.titleMedium)
                            Text(
                                statsLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // v0.4.8：用户组图例单独一行
                            if (page.legend.isNotEmpty()) {
                                Row(
                                    Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    page.legend.forEach { label ->
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(8.dp),
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (page.members.isEmpty()) {
                        item { Text("暂无在线会员数据", style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(page.members, key = { it.uid }) { member ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUser(member.uid, member.name) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            member.avatar?.let { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(member.name, style = MaterialTheme.typography.bodyMedium)
                                member.extra?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 50.dp))
                    }
                }
            }
        }
        ImmersiveTopBar(
            title = "在线会员",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}
