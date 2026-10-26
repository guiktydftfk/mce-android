package cn.mce.starlight.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.UpdateClient

/**
 * 更新提示弹窗：显示版本号与更新内容，支持静默下载进度。
 * 普通应用无法完全静默安装（系统弹窗需用户点一次“安装”）。
 */
@Composable
fun UpdateDialog(
    release: UpdateClient.Release,
    downloading: Boolean,
    progress: Int,
    downloadError: String?,
    onUpdate: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${release.versionName}") },
        text = {
            Text(
                buildString {
                    if (release.notes.isNotBlank()) {
                        appendLine(release.notes.trim())
                        appendLine()
                    }
                    when {
                        downloadError != null -> append("下载失败：$downloadError")
                        downloading -> append("正在后台下载… $progress%")
                        else -> append("点击「立即更新」将在后台下载并安装，装好后下次打开即为新版。")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(enabled = !downloading, onClick = onUpdate) {
                Text(if (downloading) "下载中 $progress%" else "立即更新")
            }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = onSnooze) { Text("暂停 7 天") }
        },
    )
}
