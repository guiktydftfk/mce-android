package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v0.4.8：缓存管理页 —— 分类查看占用并分别清理。
 * 清理不会动登录态、草稿、设置，只清可再生成的数据。
 */
@Composable
fun CacheManagerScreen(
    onMenu: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var breakdown by remember { mutableStateOf<CacheManager.Breakdown?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        breakdown = withContext(Dispatchers.IO) { CacheManager.breakdown(ctx) }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = clampTopInset(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()) + 96.dp,
                start = 14.dp,
                end = 14.dp,
                bottom = 110.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text("缓存管理", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "只清理可再生成的缓存，不会影响登录状态、草稿和设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val b = breakdown
            item {
                Column {
                    CacheRow(
                        title = "图片缓存",
                        subtitle = "帖子图片 / 头像缩略图（Coil 磁盘缓存）",
                        size = b?.imageBytes,
                        actionLabel = "清理图片",
                        enabled = !loading,
                    ) {
                        scope.launch {
                            val before = withContext(Dispatchers.IO) { CacheManager.clearImages(ctx) }
                            refresh()
                            toast = "已清理图片缓存 ${CacheManager.format(before)}"
                        }
                    }
                    CacheRow(
                        title = "帖子离线缓存",
                        subtitle = "打开过的帖子正文（弱网/离线可读）",
                        size = b?.threadBytes,
                        actionLabel = "清理帖子",
                        enabled = !loading,
                    ) {
                        scope.launch {
                            val before = withContext(Dispatchers.IO) { CacheManager.clearThreads() }
                            refresh()
                            toast = "已清理离线帖子 ${CacheManager.format(before)}"
                        }
                    }
                    CacheRow(
                        title = "首页列表缓存",
                        subtitle = "首页信息流快速加载缓存",
                        size = b?.feedBytes,
                        actionLabel = "清理列表",
                        enabled = !loading,
                    ) {
                        scope.launch {
                            val before = withContext(Dispatchers.IO) { CacheManager.clearFeedFiles(ctx) }
                            refresh()
                            toast = "已清理列表缓存 ${CacheManager.format(before)}"
                        }
                    }
                    CacheRow(
                        title = "已下载附件",
                        subtitle = "帖子附件（zip / pdf 等，打开后保存在本机）",
                        size = b?.attachmentsBytes,
                        actionLabel = "清理附件",
                        enabled = !loading,
                    ) {
                        scope.launch {
                            val before = withContext(Dispatchers.IO) { CacheManager.clearAttachments(ctx) }
                            refresh()
                            toast = "已清理附件 ${CacheManager.format(before)}"
                        }
                    }
                    CacheRow(
                        title = "其他临时文件",
                        subtitle = "分享卡片 / 更新包等临时文件",
                        size = b?.otherBytes,
                        actionLabel = "清理临时",
                        enabled = !loading,
                    ) {
                        scope.launch {
                            withContext(Dispatchers.IO) { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                            refresh()
                            toast = "已清理临时文件"
                        }
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    onClick = {
                        scope.launch {
                            val before = withContext(Dispatchers.IO) { CacheManager.clear(ctx) }
                            refresh()
                            toast = "已清理全部缓存 ${CacheManager.format(before)}"
                        }
                    },
                ) { Text("清理全部缓存") }
            }
        }
        ImmersiveTopBar(
            title = "缓存管理",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
        toast?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .padding(horizontal = 20.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CacheRow(
    title: String,
    subtitle: String,
    size: Long?,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            size?.let {
                Text(
                    "当前 " + cn.mce.starlight.core.CacheManager.format(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        OutlinedButton(enabled = enabled, onClick = onAction) { Text(actionLabel) }
    }
    HorizontalDivider()
}
