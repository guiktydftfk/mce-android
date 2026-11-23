@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
@androidx.annotation.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onMenu: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    /** v0.4.8：打开缓存管理页 */
    onOpenCacheManager: () -> Unit = {},
    /** v0.4.8：打开崩溃日志页 */
    onOpenCrashLogs: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var diagTid by remember { mutableStateOf("") }
    // v0.4.3：最近生成的自检报告预览（仅展示，不做导出）
    var diagPreview by remember { mutableStateOf<String?>(null) }
    var diagBusy by remember { mutableStateOf(false) }
    // v0.4.7：最近错误日志
    var showErrLog by remember { mutableStateOf(false) }
    // v0.4.7：启动页首图（切换后刷新预览）
    var splashTick by remember { mutableStateOf(0) }

    // v0.4.7：启动页首图选择器
    val pickSplash = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    cn.mce.starlight.core.SplashImageStore.save(context, uri)
                }
                splashTick++
                message = if (ok) "启动页首图已设置（下次启动生效）" else "图片保存失败"
            }
        }
    }
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


    /** v0.4.8：清除自定义背景时删除本地图片文件（含历史 background_*.jpg 遗留） */
    fun clearBackgroundFiles() {
        runCatching {
            context.filesDir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name == "background.jpg" || (f.name.startsWith("background_") && f.name.endsWith(".jpg")))) {
                    f.delete()
                }
            }
        }
    }
    // v0.4.7：下拉刷新（替代顶栏刷新按钮）
    var pullRefreshing by remember { mutableStateOf(false) }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            scope.launch {
                kotlinx.coroutines.delay(400)
                pullRefreshing = false
                message = "设置已刷新"
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, start = 20.dp, end = 20.dp, bottom = 40.dp),
        ) {
            SectionTitle("外观")
            Spacer(Modifier.height(8.dp))
            // v0.4.8：平板/横屏布局模式（自动 / 双栏 / 三栏）
            Text("大屏布局", style = MaterialTheme.typography.bodyLarge)
            Text(
                "平板 / 横屏时使用双栏（列表+帖子）或三栏（列表+帖子+目录）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "auto" to "自动",
                    "dual" to "双栏",
                    "triple" to "三栏",
                ).forEach { (value, label) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (SettingsStore.layoutMode == value) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (SettingsStore.layoutMode == value) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { SettingsStore.updateLayoutMode(value) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

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

            // v0.4.5：定时深色时段（放在外观/明暗模式下方）
            Spacer(Modifier.height(6.dp))
            // 定时深色时间段
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("定时深色时段", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "当前：${SettingsStore.darkStartHour}:00 – ${SettingsStore.darkEndHour}:00（明暗模式选「定时」时生效）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    SettingsStore.updateDarkHours((SettingsStore.darkStartHour + 1) % 24, SettingsStore.darkEndHour)
                }) { Text("开始 +1") }
                TextButton(onClick = {
                    SettingsStore.updateDarkHours(SettingsStore.darkStartHour, (SettingsStore.darkEndHour + 1) % 24)
                }) { Text("结束 +1") }
            }

            Spacer(Modifier.height(8.dp))

            // v0.3.6：界面大小与字体大小合并为单一「字体大小」
            // v0.3.9：拖动时只改本地预览值，松手才写回设置并重建字体（旧实现每帧改全局字体 → 整页重排卡顿）
            var fontScaleLocal by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableFloatStateOf(SettingsStore.fontScale)
            }
            Text("字体大小 ${(fontScaleLocal * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = fontScaleLocal,
                onValueChange = { fontScaleLocal = it },
                onValueChangeFinished = { SettingsStore.updateFontScale(fontScaleLocal) },
                valueRange = 0.8f..1.4f,
            )
            Spacer(Modifier.height(8.dp))
            // v0.4.7：帖子详情正文字号（独立于全局字体大小）
            Text("帖子正文字号 ${(SettingsStore.threadFontScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = SettingsStore.threadFontScale,
                onValueChange = { SettingsStore.updateThreadFontScale(it) },
                valueRange = 0.85f..1.3f,
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
                Text(
                    "清除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (SettingsStore.backgroundPath != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .clickable(enabled = SettingsStore.backgroundPath != null) {
                            clearBackgroundFiles()
                            SettingsStore.updateBackground(null)
                            message = "自定义背景已清除（本地图片已删除）"
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
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

            Spacer(Modifier.height(10.dp))
            // v0.4.7：自定义启动页首图
            val splashFile = remember(splashTick) { cn.mce.starlight.core.SplashImageStore.current(context) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启动页首图", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (splashFile != null) "已设置首图（下次启动生效）" else "未设置首图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "清除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (splashFile != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .clickable(enabled = splashFile != null) {
                            cn.mce.starlight.core.SplashImageStore.clear(context)
                            // 顺带清掉 splash 目录里可能残留的其它图片
                            runCatching {
                                java.io.File(context.filesDir, "splash").listFiles()?.forEach { f ->
                                    if (f.isFile) f.delete()
                                }
                            }
                            splashTick++
                            message = "启动页首图已清除"
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Text(
                    "选择图片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { pickSplash.launch("image/*") }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            splashFile?.let { f ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = f,
                    contentDescription = "启动页首图预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)),
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

            // v0.3.9：低数据流量模式（首页/列表不加载帖子配图，详情页正文图不受影响）
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "低数据流量模式",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "首页/板块列表不加载帖子配图（省流量）；点进帖子仍可正常看图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(
                    checked = SettingsStore.lowDataMode,
                    onCheckedChange = { SettingsStore.updateLowDataMode(it) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // v0.4.1：存储 —— 显示缓存占用 + 一键清理（图片/首页列表等可再生成缓存）
            SectionTitle("存储")
            Spacer(Modifier.height(8.dp))
            var cacheBytes by remember { mutableStateOf(0L) }
            var clearingCache by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                cacheBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    cn.mce.starlight.core.CacheManager.sizeBytes(context)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("缓存占用", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "图片 / 首页列表 / 帖子离线缓存，当前约 " +
                            cn.mce.starlight.core.CacheManager.format(cacheBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (clearingCache) return@TextButton
                        clearingCache = true
                        scope.launch {
                            val before = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                cn.mce.starlight.core.CacheManager.clear(context)
                            }
                            cacheBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                cn.mce.starlight.core.CacheManager.sizeBytes(context)
                            }
                            message = "已清理 " + cn.mce.starlight.core.CacheManager.format(before)
                            clearingCache = false
                        }
                    },
                ) { Text(if (clearingCache) "清理中…" else "清理缓存") }
            }
            // v0.4.8：缓存管理页（分类查看/清理）
            Text(
                "缓存管理（分类查看与清理）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCacheManager() }
                    .padding(vertical = 10.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionTitle("更新")
            Spacer(Modifier.height(8.dp))
            Text(
                "点击检查更新",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCheckUpdate() }
                    .padding(vertical = 12.dp),
            )
            Text(
                "当前版本 ${cn.mce.starlight.data.UpdateClient.currentVersionName(context)}" +
                    "（${cn.mce.starlight.data.UpdateClient.currentVersionCode(context)}）" + (if (cn.mce.starlight.data.UpdateClient.currentVersionName(context).contains("beta")) " · 灰度测试通道" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cn.mce.starlight.core.InstallGate.isVivoDevice) {
                Spacer(Modifier.height(10.dp))
                Text(
                    cn.mce.starlight.core.InstallGate.vivoInstallHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "打开「安装未知应用」设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            val ok = cn.mce.starlight.core.InstallGate.openUnknownSourcesSettings(context)
                            message = if (ok) "已打开安装权限设置" else "请到系统设置 → 应用 → aMCE 中允许安装未知应用"
                        }
                        .padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // v0.4.7：灰度测试开关（仅在 version.json 灰度名单内的 UID 生效）
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("参与灰度测试", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "仅当账号 UID 在灰度名单内时才会收到 beta 更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = SettingsStore.canaryOptIn,
                    onCheckedChange = { SettingsStore.updateCanaryOptIn(it) },
                )
            }
            // v0.4.7：beta 用户一键回滚到正式版（正式版用户不显示）
            if (cn.mce.starlight.data.UpdateClient.isBetaBuild(context)) {
                var showRollback by remember { mutableStateOf(false) }
                var rollbackBusy by remember { mutableStateOf(false) }
                Text(
                    if (rollbackBusy) "正在回滚…" else "一键回滚到最新正式版",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (rollbackBusy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !rollbackBusy) { showRollback = true }
                        .padding(vertical = 10.dp),
                )
                Text(
                    "当前为灰度测试版；回滚将下载并安装最新正式版",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showRollback) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { if (!rollbackBusy) showRollback = false },
                        title = { Text("回滚到正式版？") },
                        text = {
                            Text(
                                "将下载最新正式版并调起系统安装。\n" +
                                    "注意：正式版版本号低于当前 beta，系统可能提示「版本降级」而拒绝安装；" +
                                    "若安装失败，可选择卸载当前 beta 后再安装（本地设置/收藏备注会清空）。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(enabled = !rollbackBusy, onClick = {
                                rollbackBusy = true
                                scope.launch {
                                    val stable = cn.mce.starlight.data.UpdateClient.latestStable(context)
                                    val url = stable?.apkUrl
                                    if (url.isNullOrBlank()) {
                                        rollbackBusy = false
                                        showRollback = false
                                        message = "无法获取正式版安装包，请稍后重试"
                                    } else {
                                        val file = cn.mce.starlight.data.UpdateClient.download(context, url)
                                        rollbackBusy = false
                                        showRollback = false
                                        if (file != null) {
                                            val ok = cn.mce.starlight.data.UpdateClient.install(context, file)
                                            message = if (ok) "已开始安装正式版 ${stable?.versionName ?: ""}" else "安装器启动失败"
                                        } else {
                                            message = "正式版下载失败，请检查网络"
                                        }
                                    }
                                }
                            }) { Text(if (rollbackBusy) "下载中…" else "下载并安装") }
                        },
                        dismissButton = {
                            androidx.compose.foundation.layout.Row {
                                androidx.compose.material3.TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_DELETE,
                                                android.net.Uri.parse("package:" + context.packageName),
                                            ),
                                        )
                                    }
                                    showRollback = false
                                }) { Text("卸载后重装") }
                                androidx.compose.material3.TextButton(onClick = { showRollback = false }) { Text("取消") }
                            }
                        },
                    )
                }
            }
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

            SectionTitle("通知")
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("未读私信通知", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "后台每 15 分钟检查一次未读私信并提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = SettingsStore.notifyUnreadEnabled, onCheckedChange = {
                    SettingsStore.updateNotifyUnread(it)
                })
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("提醒通知（@ / 回复 / 系统）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "后台巡检到新提醒时单独发通知，与私信通知互不影响",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = SettingsStore.notifyNoticeEnabled, onCheckedChange = {
                    SettingsStore.updateNotifyNotice(it)
                })
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("夜间免打扰（23:00–7:00）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "开启后夜间不弹未读提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = SettingsStore.notifyQuietEnabled, onCheckedChange = {
                    SettingsStore.updateNotifyQuiet(it)
                })
            }

            // v0.4.8：应用锁（使用系统锁屏凭据）
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("应用锁", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "开启后，打开应用 / 从后台回来需验证系统锁屏；不会保存密码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = SettingsStore.appLockEnabled, onCheckedChange = { on ->
                    if (on && !cn.mce.starlight.core.AppLock.isDeviceSecure(context)) {
                        message = "请先在系统设置里设置锁屏密码 / 指纹后再开启应用锁"
                    } else {
                        SettingsStore.updateAppLock(on)
                        message = if (on) "应用锁已开启" else "应用锁已关闭"
                    }
                })
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("诊断")
            Spacer(Modifier.height(8.dp))
            // v0.4.8：崩溃日志（本地滚动归档）
            Text(
                "崩溃日志（查看 / 复制 / 清空）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCrashLogs() }
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            // v0.4.7：布局诊断（窗口/配置 dp、是否双栏、旋转、密度），用于排查宽屏不生效
            val cfgDiag = androidx.compose.ui.platform.LocalConfiguration.current
            val viewDiag = androidx.compose.ui.platform.LocalView.current
            val denDiag = androidx.compose.ui.platform.LocalDensity.current
            val winW = with(denDiag) { viewDiag.width.toDp().value.toInt() }
            val winH = with(denDiag) { viewDiag.height.toDp().value.toInt() }
            val dualDiag = cn.mce.starlight.core.WindowLayout.isDualPane(winW, winH) ||
                cn.mce.starlight.core.WindowLayout.isDualPane(cfgDiag.screenWidthDp, cfgDiag.screenHeightDp)
            Text(
                "布局诊断：窗口 ${winW}×${winH}dp · 配置 ${cfgDiag.screenWidthDp}×${cfgDiag.screenHeightDp}dp · 双栏 " +
                    (if (dualDiag) "是" else "否") + " · 旋转 " + viewDiag.display?.rotation + " · 密度 " + denDiag.density,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
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
                    diagPreview = report
                        diagBusy = false
                        message = if (diagTid.isBlank()) "自检报告已复制到剪贴板"
                        else "自检报告（含图片自检）已复制到剪贴板"
                    }
                },
            ) { Text(if (diagBusy) "生成中…" else "生成并复制自检报告") }
            // v0.4.3：报告预览（含设备标识 / 网络 / 解析 / 错误统计）
            diagPreview?.let { preview ->
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                    ) {
                        Text(
                            "报告预览（已复制到剪贴板）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                preview,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
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

        }

        ImmersiveTopBar(
            title = "设置",
            onMenu = onMenu,
        )
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
