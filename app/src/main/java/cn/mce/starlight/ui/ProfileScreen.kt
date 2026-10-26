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
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.data.ProfileClient
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface ProfileState {
    data object Loading : ProfileState
    data class Error(val message: String) : ProfileState
    data class Content(val profile: ProfileClient.Profile) : ProfileState
}

/**
 * “我的”页（参考鸿蒙版个人资料页）：
 * 头像 + 昵称 + 用户组 → 统计（主题/回帖/积分）→ 资料字段 → 入口列表。
 */
@Composable
fun ProfileScreen(
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenPage: (String, String) -> Unit,
    onOpenFriendQr: () -> Unit = {},
    onOpenScan: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ProfileState>(ProfileState.Loading) }
    var reloadTick by remember { mutableStateOf(0) }

    suspend fun load() {
        if (!SessionStore.isLoggedIn) {
            state = ProfileState.Error("未登录")
            return
        }
        try {
            val p = ProfileClient.load()
            state = if (p == null) ProfileState.Error("资料加载失败") else ProfileState.Content(p)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 组合被重建导致请求取消：保持加载态，交给下一次 LaunchedEffect 重试
            return
        } catch (e: Exception) {
            state = ProfileState.Error(e.message?.takeIf { it.isNotBlank() } ?: "加载失败")
        }
    }

    LaunchedEffect(reloadKey, SessionStore.isLoggedIn, reloadTick) { load() }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is ProfileState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is ProfileState.Error -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = topInset + 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (!SessionStore.isLoggedIn) {
                    TextButton(onClick = onLogin) { Text("登录 / 注册") }
                } else {
                    TextButton(onClick = { reloadTick += 1 }) { Text("重试") }
                }
            }

            is ProfileState.Content -> {
                val p = s.profile
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topInset + 64.dp,
                        bottom = 120.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                val avatar = p.avatarUrl ?: SessionStore.avatarUrl
                                if (!avatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = avatar,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    p.username ?: SessionStore.username ?: "成员",
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
                                val ordered = preferred.mapNotNull { key -> p.stats[key]?.let { key to it } }
                                    .ifEmpty { p.stats.entries.take(3).map { it.key to it.value } }
                                ordered.forEach { (label, value) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            value,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 常用入口
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            listOf(
                                "\u6211\u7684\u4e3b\u9898" to "do=thread&view=me",
                                "\u6211\u7684\u56de\u590d" to "do=thread&type=reply",
                                "\u6211\u7684\u6536\u85cf" to "do=favorite",
                                "\u6d88\u606f\u63d0\u9192" to "do=notice",
                                "\u6211\u7684\u52cb\u7ae0" to "do=medal",
                                "\u6211\u7684\u597d\u53cb" to "do=friend",
                                "\u9053\u5177\u4e2d\u5fc3" to "mod=magic",
                                "\u7f16\u8f91\u8d44\u6599" to "spacecp&ac=profile",
                            ).forEach { (label, path) ->
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenPage(path, label) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                            }
                            // v0.3.4：好友码 / 扫一扫（放在入口列表末尾，不改变原有跳转逻辑）
                            Text(
                                "我的好友码",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFriendQr() }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                            Text(
                                "扫一扫（加好友）",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenScan() }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                    }

                    // 资料字段
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
                                p.fields.entries.forEach { (label, value) ->
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

                    // 入口
                    if (p.entries.isNotEmpty()) {
                        items(
                            p.entries.filterNot {
                                it.second.contains("do=thread") || it.second.contains("do=favorite") ||
                                    it.second.contains("do=notice") || it.second.contains("do=friend")
                            },
                            key = { it.second },
                        ) { (name, url) ->
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPage(url, name) }
                                    .padding(vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }

                    item {
                        Text(
                            "退出登录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onLogout)
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }

        ImmersiveTopBar(title = "我的", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}
