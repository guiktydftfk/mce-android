package cn.mce.starlight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.UpdateClient

/**
 * 更新提示弹窗：显示版本号与更新内容，支持静默下载进度。
 * 普通应用无法完全静默安装（系统弹窗需用户点一次“安装”）。
 *
 * v0.4.8：vivo/iQOO 的 Super Care 会拦截未注册应用的安装，这里提前说明
 * 「点 authorize its installation 授权安装 / 关闭 i管家 安装安全检测」，
 * 并在缺少「安装未知应用」权限时提供一键去设置。
 */
@Composable
fun UpdateDialog(
    release: UpdateClient.Release,
    downloading: Boolean,
    progress: Int,
    downloadError: String?,
    emergency: Boolean = false,
    vivoHint: Boolean = false,
    installPermissionMissing: Boolean = false,
    onOpenInstallSettings: () -> Unit = {},
    onUpdate: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (emergency) "紧急更新 ${release.versionName}" else "发现新版本 ${release.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildString {
                        if (release.notes.isNotBlank()) {
                            appendLine(release.notes.trim())
                            appendLine()
                        }
                        when {
                            downloadError != null -> append("下载失败：$downloadError")
                            downloading -> append("正在后台下载… $progress%")
                            emergency -> append("这是标记为紧急的版本，已开始自动下载；下载完成后会拉起系统安装器（需点一次「安装」）。")
                            else -> append("点击「立即更新」将在后台下载并安装，装好后下次打开即为新版。")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                )
                if (vivoHint) {
                    Text(
                        "vivo / iQOO 提示：安装时出现「Super Care 应用注册验证」时，请点弹窗里的" +
                            "「authorize its installation（授权安装）」，不要点 Cancel；" +
                            "若仍被拦截，可到 i管家 → 安全防护 关闭「安装安全检测」后再试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (installPermissionMissing) {
                    Text(
                        "尚未允许本应用「安装未知应用」，点下面按钮去开启后再更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onOpenInstallSettings) {
                        Text("去开启「安装未知应用」")
                    }
                }
            }
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
