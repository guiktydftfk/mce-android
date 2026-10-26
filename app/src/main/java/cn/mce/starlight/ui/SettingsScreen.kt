package cn.mce.starlight.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import cn.mce.starlight.core.Diagnostics
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
    onOpenImageDiag: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
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

            SectionTitle("更新")
            Spacer(Modifier.height(8.dp))
            Text(
                "检查更新",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCheckUpdate() }
                    .padding(vertical = 12.dp),
            )
            Text(
                "当前版本 ${cn.mce.starlight.data.UpdateClient.currentVersionName(context)}" +
                    "（${cn.mce.starlight.data.UpdateClient.currentVersionCode(context)}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (SettingsStore.isUpdateSnoozed())
                    "已暂停自动检查更新，剩 ${SettingsStore.snoozeRemainingDays()} 天"
                else "启动时会自动检查更新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "暂停 7 天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { SettingsStore.snoozeUpdates(7); message = "已暂停自动更新 7 天" }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                )
                Text(
                    "暂停 15 天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { SettingsStore.snoozeUpdates(15); message = "已暂停自动更新 15 天" }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                )
                if (SettingsStore.isUpdateSnoozed()) {
                    Text(
                        "取消暂停",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable { SettingsStore.clearSnooze(); message = "已恢复自动检查更新" }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionTitle("诊断")
            Spacer(Modifier.height(8.dp))
            Text(
                "自检报告（点击复制到剪贴板）",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val report = Diagnostics.build(context)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("MCE 自检报告", report))
                        message = "自检报告已复制到剪贴板"
                    }
                    .padding(vertical = 12.dp),
            )
            Text(
                "图片自检（帖子图片看不到时用）",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenImageDiag() }
                    .padding(vertical = 12.dp),
            )
            Text(
                "输入帖子 ID，逐个探测正文图片请求，输出失败原因",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "包含版本、设备、登录态（不含 Cookie 值）、设置与最近抓取页面，可直接粘贴反馈",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
