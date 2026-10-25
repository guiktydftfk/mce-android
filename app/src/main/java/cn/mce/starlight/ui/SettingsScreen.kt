package cn.mce.starlight.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.core.SettingsStore
import coil.compose.AsyncImage
import java.io.File

/** Settings: appearance (dark mode / background image), account, about */
@Composable
fun SettingsScreen(
    onMenu: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val target = File(context.filesDir, "background_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            SettingsStore.updateBackground(target.absolutePath)
            message = "背景已更新"
        }.onFailure { message = "设置背景失败：${it.message}" }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, start = 20.dp, end = 20.dp, bottom = 40.dp),
        ) {
            SectionTitle("外观")

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

            Spacer(Modifier.height(12.dp))

            // background image
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自定义背景", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (SettingsStore.backgroundPath != null) "已设置背景图" else "未设置背景图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (SettingsStore.backgroundPath != null) {
                    Text(
                        "清除",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable {
                                SettingsStore.updateBackground(null)
                                message = "背景已清除"
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Text(
                    "选择图片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { pickBackground.launch("image/*") }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            SettingsStore.backgroundPath?.let { path ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "透明度 ${(SettingsStore.backgroundAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = SettingsStore.backgroundAlpha,
                    onValueChange = { SettingsStore.updateBackgroundAlpha(it) },
                    valueRange = 0.05f..1f,
                )

                Text(
                    "模糊度 ${SettingsStore.backgroundBlur.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = SettingsStore.backgroundBlur,
                    onValueChange = { SettingsStore.updateBackgroundBlur(it) },
                    valueRange = 0f..25f,
                )
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionTitle("账号")
            Spacer(Modifier.height(8.dp))
            Text(
                if (SessionStore.isLoggedIn) "已登录：${SessionStore.username ?: "成员"}" else "未登录",
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

            SectionTitle("关于")
            Spacer(Modifier.height(8.dp))
            Text("MCE星光班 Android 客户端", style = MaterialTheme.typography.bodyMedium)
            Text(
                "github.com/guiktydftfk/mce-android",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ImmersiveTopBar(title = "设置", onMenu = onMenu)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}
