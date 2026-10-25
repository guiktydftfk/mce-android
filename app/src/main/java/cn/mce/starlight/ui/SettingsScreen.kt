package cn.mce.starlight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.core.SettingsStore

/** 设置页：外观（明暗）、账号、关于 */
@Composable
fun SettingsScreen(
    onMenu: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = 100.dp, start = 20.dp, end = 20.dp)) {
            Text("外观", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("深色模式", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "跟随开关即时切换明暗主题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = SettingsStore.darkMode,
                    onCheckedChange = { SettingsStore.updateDarkMode(it) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Text("账号", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                if (SessionStore.isLoggedIn) {
                    "已登录：${SessionStore.username ?: "成员"}"
                } else {
                    "未登录"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (SessionStore.isLoggedIn) {
                Text(
                    "退出登录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogout)
                        .padding(vertical = 12.dp),
                )
            } else {
                Text(
                    "登录 / 注册",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogin)
                        .padding(vertical = 12.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Text("关于", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("MCE星光班 Android 客户端", style = MaterialTheme.typography.bodyMedium)
            Text(
                "开源仓库：github.com/guiktydftfk/mce-android",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ImmersiveTopBar(title = "设置", onMenu = onMenu)
    }
}
