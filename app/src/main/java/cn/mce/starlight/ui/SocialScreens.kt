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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.SocialClient
import coil.compose.AsyncImage

/** v0.4.1：我的勋章 —— 勋章墙：显示全部勋章（含未解锁），点未解锁看进度 */
@Composable
fun MedalsScreen(
    onMenu: () -> Unit,
) {
    var medals by remember { mutableStateOf<List<SocialClient.Medal>?>(null) }
    var detail by remember { mutableStateOf<SocialClient.Medal?>(null) }
    LaunchedEffect(Unit) { medals = SocialClient.loadMedals() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val list = medals) {
            null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("还没有勋章数据", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val unlockedCount = list.count { it.unlocked }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        top = topInset + 96.dp, bottom = 110.dp,
                        start = 12.dp, end = 12.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Text(
                            "已点亮 $unlockedCount / ${list.size} 枚勋章 · 点击未点亮勋章查看进度",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    items(list, key = { it.name }) { medal ->
                        MedallionCard(medal = medal, onClick = { detail = medal })
                    }
                }
            }
        }
        ImmersiveTopBar(title = "我的勋章", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }

    detail?.let { medal ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(medal.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        medal.image?.let {
                            AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(56.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (medal.unlocked) "已点亮" else if (medal.claimable) "可领取（条件已满足）" else "未点亮",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (medal.unlocked) androidx.compose.ui.graphics.Color(0xFF3CC66A)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            medal.progress?.let {
                                Text("进度：$it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    medal.desc?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!medal.unlocked && medal.progress == null) {
                        Text(
                            "该勋章需要满足论坛条件后才能点亮（如发帖数、签到天数、活动参与等），可在网页端勋章页查看具体要求。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { detail = null }) { Text("知道了") }
            },
        )
    }
}

/** 单枚勋章卡片：未点亮时灰化 + 锁标 */
@Composable
private fun MedallionCard(medal: SocialClient.Medal, onClick: () -> Unit) {
    val dim = if (medal.unlocked) 1f else 0.38f
    Column(
        Modifier
            .iosGlass(14)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            medal.image?.let {
                AsyncImage(
                    model = it,
                    contentDescription = medal.name,
                    modifier = Modifier.size(52.dp).alpha(dim),
                )
            }
            if (!medal.unlocked) {
                Text("🔒", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            medal.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (medal.unlocked) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (medal.unlocked) {
            Text("已点亮", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF3CC66A))
        } else if (medal.claimable) {
            Text("可领取", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } else {
            Text(
                medal.progress?.let { "进度 $it" } ?: "未点亮",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
/** 我的好友：头像 + 昵称，点击进入个人空间 */
@Composable
fun FriendsScreen(
    onMenu: () -> Unit,
    onOpenUser: (Int, String) -> Unit,
) {
    var friends by remember { mutableStateOf<List<SocialClient.Friend>?>(null) }
    LaunchedEffect(Unit) { friends = SocialClient.loadFriends() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val list = friends) {
            null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("还没有好友", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(top = topInset + 64.dp, bottom = 110.dp)) {
                    items(list, key = { it.uid }) { friend ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUser(friend.uid, friend.name) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                friend.avatar?.let {
                                    AsyncImage(
                                        model = it,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    friend.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // v0.4.1：在线状态圆点（绿=在线 / 灰=离线）
                                    friend.online?.let { on ->
                                        Box(
                                            Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (on) androidx.compose.ui.graphics.Color(0xFF3CC66A) else MaterialTheme.colorScheme.outline),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            if (on) "在线" else "离线",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (on) androidx.compose.ui.graphics.Color(0xFF3CC66A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    friend.extra?.let {
                                        Text(
                                            "好友 · $it",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 70.dp))
                    }
                }
            }
        }
        ImmersiveTopBar(title = "我的好友", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

