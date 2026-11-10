package cn.mce.starlight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.RateClient

/** v0.4.6：楼层跳转对话框（从 ThreadDetailScreen 拆出） */
@Composable
fun JumpToFloorDialog(
    total: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到楼层") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                singleLine = true,
                label = { Text("楼层号（1 起，共 $total 楼）") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onJump(text.toIntOrNull() ?: 1)
            }) { Text("跳转") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** v0.4.6：帖内搜索对话框（从 ThreadDetailScreen 拆出） */
@Composable
fun SearchInThreadDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("帖内搜索") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("关键词（正文/作者）") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val kw = text.trim()
                if (kw.isNotEmpty()) onSearch(kw) else onDismiss()
            }) { Text("查找") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** v0.4.6：评分/赞赏对话框（从 ThreadDetailScreen 拆出） */
@Composable
internal fun RateDialog(
    form: RateClient.Form,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var score by remember { mutableStateOf(form.scoreMin.toString()) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评分 / 赞赏") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "可给 ${form.scoreMin}~${form.scoreMax} 分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (form.scoreMin..form.scoreMax).forEach { v ->
                        TextButton(onClick = { score = v.toString() }) {
                            Text(if (score == v.toString()) "✓ $v 分" else "$v 分")
                        }
                    }
                }
                if (form.reasons.isNotEmpty()) {
                    Text("可选理由", style = MaterialTheme.typography.bodySmall)
                    form.reasons.take(4).forEach { preset ->
                        Text(
                            preset,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reason = preset }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("评分理由") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(score, reason) }) { Text("提交") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
/** v0.4.6：编辑帖子弹窗（从 ThreadDetailScreen 拆出；正文保留 BBCode 原样） */
@Composable
internal fun EditPostDialog(
    post: cn.mce.starlight.data.Post,
    busy: Boolean,
    initialSubject: String,
    initialBody: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var subject by remember { mutableStateOf(initialSubject) }
    var body by remember { mutableStateOf(initialBody) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("编辑楼层") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    singleLine = true,
                    label = { Text("标题") },
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("正文（保留 BBCode 原样）") },
                    minLines = 5,
                    maxLines = 10,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onSave(subject, body) }) {
                Text(if (busy) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
