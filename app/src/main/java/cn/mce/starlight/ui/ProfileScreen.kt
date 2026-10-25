package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore
import coil.compose.AsyncImage

/** 我的：头像 / 昵称 / UID + 个人中心入口 */
@Composable
fun ProfileScreen(
    onMenu: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenPage: (String, String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = 100.dp, start = 20.dp, end = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val avatar = SessionStore.avatarUrl
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        SessionStore.username ?: "未登录",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        SessionStore.uid?.let { "UID：$it" } ?: "登录后可查看个人中心",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            if (!SessionStore.isLoggedIn) {
                LinkRow("登录 / 注册", onClick = onLogin)
            } else {
                LinkRow("我的主题") { onOpenPage("https://mce.msi.cn/home.php?mod=space&do=thread", "我的主题") }
                LinkRow("我的收藏") { onOpenPage("https://mce.msi.cn/home.php?mod=space&do=favorite", "我的收藏") }
                LinkRow("我的积分") { onOpenPage("https://mce.msi.cn/home.php?mod=space&do=credit", "我的积分") }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                LinkRow("退出登录", color = MaterialTheme.colorScheme.error, onClick = onLogout)
            }
        }

        ImmersiveTopBar(title = "我的", onMenu = onMenu)
    }
}

@Composable
private fun LinkRow(
    label: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
