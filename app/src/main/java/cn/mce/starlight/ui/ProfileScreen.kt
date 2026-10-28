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
import androidx.compose.ui.graphics.Color
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
    // v0.3.10：今日星光=当日新增，改成随资料加载一起刷新（旧实现每次重组现算，还会在组合期写 SharedPreferences）
    var todayDelta by remember { mutableStateOf(0) }

    suspend fun load() {
        if (!SessionStore.isLoggedIn) {
            state = ProfileState.Error("未登录")
            return
        }
        try {
            val p = ProfileClient.load()
            if (p == null) {
                state = ProfileState.Error("资料加载失败")
                return
            }
            state = ProfileState.Content(p)
            val pointsTotal = p.stats["积分"]?.filter { it.isDigit() }?.toIntOrNull()
            todayDelta = if (pointsTotal != null) {
                cn.mce.starlight.core.StarlightDailyStore.deltaFor(pointsTotal)
            } else {
                0
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 组合被重建导致请求取消：保持加载态，交给下一次 LaunchedEffect 重试
            return
        } catch (e: Exception) {
            state = ProfileState.Error(e.message?.takeIf { it.isNotBlank() } ?: "加载失败")
        }
    }

    LaunchedEffect(reloadKey, SessionStore.isLoggedIn, reloadTick) { load() }

    // v0.3.10：从后台回来时静默刷新一次（积分/星光/分组可能已变），20s 节流避免频繁请求
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        var last = 0L
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val now = System.currentTimeMillis()
                if (now - last > 20_000L) {
                    last = now
                    reloadTick += 1
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    p.group?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    // v0.3.10：管理员/超级版主/版主按实际用户组显示身份徽标；普通用户不显示
                                    p.staff?.let { staff ->
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            staff,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(staffColor(staff))
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
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
                                            displayStatName(label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                // v0.3.10：今日星光 = 当日新增（峰值 − 当天基准），随资料加载一起刷新；
                                // 花积分不会把今天已获得的抹掉，回到页面/回前台会重新拉取
                                val pointsTotal = p.stats["积分"]
                                    ?.filter { it.isDigit() }
                                    ?.toIntOrNull()
                                if (pointsTotal != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "+$todayDelta",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            "今日新增星光",
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
                                            displayStatName(label),
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

/** 身份徽标底色（v0.3.10）：管理员红 / 超级版主橙 / 版主绿 */
private fun staffColor(staff: String): androidx.compose.ui.graphics.Color = when {
    staff.contains("\u7ba1\u7406\u5458") -> androidx.compose.ui.graphics.Color(0xFFC62828)
    staff.contains("\u8d85\u7ea7\u7248\u4e3b") -> androidx.compose.ui.graphics.Color(0xFFEF6C00)
    else -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
}

/**
 * v0.4.0：论坛字段名沿用「积分」，产品对外统一叫「星光值」。
 * 只在显示层改名，解析/提交仍用站点的「积分」字段，避免影响其它逻辑。
 */
private fun displayStatName(name: String): String = when (name) {
    "积分" -> "星光值"
    else -> name
}
