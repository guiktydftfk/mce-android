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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.UserProfileClient
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface UState {
    data object Loading : UState
    data class Error(val message: String) : UState
    data class Content(val profile: UserProfileClient.Profile) : UState
}

/** 个人空间页（从好友列表 / 帖子作者头像进入） */
@Composable
fun UserProfileScreen(
    uid: Int?,
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
    onOpenPage: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember(uid) { mutableStateOf<UState>(UState.Loading) }

    suspend fun load() {
        state = UState.Loading
        runCatching { UserProfileClient.load(uid) }
            .onSuccess { state = if (it == null) UState.Error("无法加载该用户资料") else UState.Content(it) }
            .onFailure { state = UState.Error(it.message ?: "加载失败") }
    }

    LaunchedEffect(uid) { load() }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is UState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is UState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { scope.launch { load() } }) { Text("重试") }
                }
            }

            is UState.Content -> {
                val p = s.profile
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topInset + 64.dp, bottom = 120.dp,
                        start = 16.dp, end = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 头部
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                p.avatarUrl?.let {
                                    AsyncImage(
                                        model = it,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                p.group?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    "UID ${p.uid}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // 统计
                    if (p.stats.isNotEmpty()) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                val preferred = listOf("主题", "回帖", "积分")
                                val ordered = preferred.mapNotNull { key ->
                                    p.stats[key]?.let { key to it }
                                }.ifEmpty { p.stats.entries.take(3).map { it.key to it.value } }
                                ordered.forEach { (label, value) ->
                                    val shown = cn.mce.starlight.data.ProfileCommon.displayStatName(label)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            value,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            shown,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // v0.4.1：我的资料 —— 头衔 / 签名 / 邮箱状态
                    if (p.customTitle != null || p.signature != null || p.emailStatus != null) {
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                p.customTitle?.let { titleText ->
                                    Row {
                                        Text("自定义头衔", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(80.dp))
                                        Text(titleText, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                p.signature?.let { sig ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("个人签名", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(sig, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                p.emailStatus?.let { email ->
                                    Row {
                                        Text("邮箱状态", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(80.dp))
                                        Text(email, style = MaterialTheme.typography.bodySmall,
                                            color = if (email.contains("验证")) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // v0.4.1：活跃概况
                    if (p.active.isNotEmpty()) {
                        item {
                            Text(
                                "活跃概况",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                p.active.forEach { (label, value) ->
                                    Row {
                                        Text(label, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(96.dp))
                                        Text(value, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    // 资料
                    if (p.fields.isNotEmpty()) {
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                p.fields.entries.take(8).forEach { (label, value) ->
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(72.dp),
                                        )
                                        Text(value, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    // 操作（v0.4.1：自己的「我的资料」不显示打招呼/发消息/加好友）
                    if (!p.isSelf) {
                        item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = {
                                onOpenPage(
                                    "https://mce.msi.cn/home.php?mod=spacecp&ac=poke&op=send&uid=${p.uid}",
                                    "打招呼",
                                )
                            }) { Text("打招呼") }
                            TextButton(onClick = {
                                onOpenPage(
                                    "https://mce.msi.cn/home.php?mod=spacecp&ac=pm&touid=${p.uid}",
                                    "发消息",
                                )
                            }) { Text("发消息") }
                            TextButton(onClick = {
                                onOpenPage(
                                    "https://mce.msi.cn/home.php?mod=spacecp&ac=friend&op=add&uid=${p.uid}",
                                    "加好友",
                                )
                            }) { Text("加好友") }
                        }
                        }
                    }

                    // 最近主题
                    if (p.recentThreads.isNotEmpty()) {
                        item {
                            Text(
                                "最近发表的主题",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(p.recentThreads, key = { it.first }) { (tid, title) ->
                            Text(
                                title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenThread(tid, title) }
                                    .padding(vertical = 10.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        ImmersiveTopBar(title = "个人空间", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}
