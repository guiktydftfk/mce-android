package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.CrashLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v0.4.8：崩溃日志页 —— 查看最近 10 次崩溃并复制/清空。
 * 数据只在本地，不上传；复制后可自行反馈给开发者。
 */
@Composable
fun CrashLogsScreen(onMenu: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<CrashLogs.Entry>>(emptyList()) }
    var detail by remember { mutableStateOf<CrashLogs.Entry?>(null) }

    suspend fun reload() {
        entries = withContext(Dispatchers.IO) { CrashLogs.entries(ctx) }
    }
    LaunchedEffect(Unit) { reload() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = clampTopInset(topInset) + 96.dp,
                start = 14.dp,
                end = 14.dp,
                bottom = 110.dp,
            ),
        ) {
            item {
                Column {
                    Text("崩溃日志", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "最近 ${entries.size} 次崩溃（仅本地保存，可复制反馈）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "暂无崩溃记录 🎉",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
            items(entries, key = { it.timeText + it.summary }) { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { detail = entry }
                        .padding(vertical = 10.dp),
                ) {
                    Text(entry.timeText, style = MaterialTheme.typography.labelLarge)
                    Text(
                        entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
            }
            if (entries.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { CrashLogs.clearAll(ctx) }
                                reload()
                            }
                        },
                    ) { Text("清空崩溃日志") }
                }
            }
        }
        ImmersiveTopBar(
            title = "崩溃日志",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }

    detail?.let { entry ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(entry.timeText) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                ) {
                    Text(
                        entry.lines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 30,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "崩溃日志",
                                entry.lines.joinToString("\n"),
                            ),
                        )
                    }
                    detail = null
                }) { Text("复制") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { detail = null }) { Text("关闭") }
                }
            },
        )
    }
}
