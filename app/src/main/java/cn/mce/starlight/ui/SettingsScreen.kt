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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import cn.mce.starlight.core.DarkMode
import cn.mce.starlight.core.Diagnostics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.mce.starlight.ui.theme.AccentTheme
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.core.SettingsStore
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/** Settings: appearance (dark mode / background image), account, about */
@Composable
fun SettingsScreen(
    onMenu: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onCheckUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var diagTid by remember { mutableStateOf("") }
    var diagBusy by remember { mutableStateOf(false) }

    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // v0.3.3 批 2：拷贝整张原图是 MB 级 IO，挪到 IO 协程（旧实现直接在 ActivityResult 主线程回调里 copyTo）
        scope.launch {
            runCatching {
                val target = File(context.filesDir, "background.jpg")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                // 清理历史遗留的 background_<时间戳>.jpg（旧实现每次选图都新建文件、从不删除）
                context.filesDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("background_") && it.name.endsWith(".jpg") }
                    ?.forEach { it.delete() }
                SettingsStore.updateBackground(target.absolutePath)
                message = "背景已更新"
            }.onFailure { message = "设置背景失败：${it.message}" }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, start = 20.dp, end = 20.dp, bottom = 40.dp),
        ) {
            SectionTitle("外观")

            // v0.3.7：明暗模式三态（浅色 / 深色 / 跟随系统）
            Text("明暗模式", style = MaterialTheme.typography.bodyLarge)
            Text(
                "「跟随系统」会随手机的深色模式自动切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkMode.entries.forEach { mode ->
                    val active = SettingsStore.darkModeSetting == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { SettingsStore.updateDarkModeSetting(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // v0.3.6：界面大小与字体大小合并为单一「字体大小」
            Text("字体大小 ${(SettingsStore.fontScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = SettingsStore.fontScale,
                onValueChange = { SettingsStore.updateFontScale(it) },
                valueRange = 0.8f..1.4f,
            )
            Text(
                "只缩放文字，不改变整体布局；即时生效，重启后保持",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 主题色（保留原有「莫奈蓝」为默认，新增 Edge 风格配色）
            Text("主题色", style = MaterialTheme.typography.bodyLarge)
            Text(
                "默认沿用原有莫奈蓝配色；切换后立即生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val current = AccentTheme.fromName(SettingsStore.accentTheme)
                AccentTheme.entries.forEach { theme ->
                    val active = theme == current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { SettingsStore.updateAccentTheme(theme.name) }
                            .padding(4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(theme.lightPrimary)
                                .then(
                                    if (active) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(15.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Text(
                            theme.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                    // 拖动时只改内存 state（实时预览），松手才落盘
                    onValueChange = { SettingsStore.updateBackgroundAlpha(it, persist = false) },
                    onValueChangeFinished = {
                        SettingsStore.updateBackgroundAlpha(SettingsStore.backgroundAlpha)
                    },
                    valueRange = 0.05f..1f,
                )

                Text(
                    "模糊度 ${SettingsStore.backgroundBlur.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = SettingsStore.backgroundBlur,
                    onValueChange = { SettingsStore.updateBackgroundBlur(it, persist = false) },
                    onValueChangeFinished = {
                        SettingsStore.updateBackgroundBlur(SettingsStore.backgroundBlur)
                    },
                    valueRange = 0f..25f,
                )
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // v0.3.8：首页帖子过滤（按字数）
            SectionTitle("内容")
            Text(
                "首页帖子过滤",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (SettingsStore.feedMinChars == 0) {
                    "关闭：首页显示全部帖子"
                } else {
                    "首页隐藏少于 ${SettingsStore.feedMinChars} 字的帖子（不影响板块 / 搜索）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = SettingsStore.feedMinChars.toFloat(),
                onValueChange = { v ->
                    // 每 10 字一档：0 / 10 / 20 … 200；拖动时只改内存，松手才落盘
                    SettingsStore.updateFeedMinChars(Math.round(v / 10f) * 10, persist = false)
                },
                onValueChangeFinished = {
                    SettingsStore.updateFeedMinChars(SettingsStore.feedMinChars)
                },
                valueRange = 0f..200f,
                steps = 19,
            )

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

            SigninReminderSection()

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionTitle("诊断")
            Spacer(Modifier.height(8.dp))
            Text(
                "自检报告（含图片自检，一键复制）",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "包含版本、设备、登录态（不含 Cookie 值）、设置与最近抓取页面，可直接粘贴反馈",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = diagTid,
                onValueChange = { diagTid = it.filter { ch -> ch.isDigit() }.take(8) },
                label = { Text("帖子 ID（可选）") },
                placeholder = { Text("填了会顺带检查该帖图片是否可加载") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !diagBusy,
                onClick = {
                    diagBusy = true
                    scope.launch {
                        val report = Diagnostics.build(context, diagTid.toIntOrNull())
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("MCE 自检报告", report))
                        diagBusy = false
                        message = if (diagTid.isBlank()) "自检报告已复制到剪贴板"
                        else "自检报告（含图片自检）已复制到剪贴板"
                    }
                },
            ) { Text(if (diagBusy) "生成中…" else "生成并复制自检报告") }

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
            Text("aMCE · MCE星光班 Android 客户端", style = MaterialTheme.typography.bodyMedium)
            Text(
                "github.com/guiktydftfk/mce-android",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Text("开源协议", style = MaterialTheme.typography.bodyLarge)
            Text(
                "本应用（含代码与文档）采用 CC BY-NC 4.0 国际许可协议" +
                    "（Creative Commons Attribution-NonCommercial 4.0 International，" +
                    "署名—非商业性使用 4.0 国际）。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "您可以自由地共享（复制、发行）和改编本作品，但必须：① 署名（注明原作者与本项目出处并附协议链接）；" +
                    "② 非商业性使用（不得用于商业目的）。协议全文：https://creativecommons.org/licenses/by-nc/4.0/legalcode.zh-hans",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Text("免责声明", style = MaterialTheme.typography.bodyLarge)
            Text(
                "1. 本应用为第三方非官方客户端，与 MCE星光班、微星科技（MSI）及论坛管理团队无隶属或授权关系，" +
                    "名称与图标仅用于标识所访问的论坛社区。\n" +
                    "2. 应用内展示的帖子、图片、评论等内容版权归原作者及论坛所有，本应用仅做展示与转发，不对内容真实性、合法性负责。\n" +
                    "3. 本应用按「现状」提供，不附带任何明示或暗示的担保；因使用本应用造成的任何直接或间接损失，" +
                    "开发者不承担责任，使用风险由使用者自行承担。\n" +
                    "4. 本应用仅供学习交流与非商业用途，禁止用于任何商业目的；请遵守论坛规则及当地法律法规。\n" +
                    "5. 如权利人认为内容侵权，请联系开发者删除。",
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
