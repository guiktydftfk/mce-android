package cn.mce.starlight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.ReportClient

/**
 * v0.4.7：原生举报弹窗（理由单选 + 说明），替代之前打开空白网页的做法。
 */
@Composable
fun ReportDialog(
    form: ReportClient.ReportForm,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reasonId: String, message: String) -> Unit,
) {
    var selected by remember(form) { mutableStateOf(form.reasons.firstOrNull()?.first ?: "1") }
    var message by remember(form) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("举报") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "请选择举报理由",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                form.reasons.forEach { (id, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == id, enabled = !busy) { selected = id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == id, onClick = { if (!busy) selected = id })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "补充说明（选填）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    enabled = !busy,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("可填写具体情况，方便版主处理") },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onSubmit(selected, message) }) {
                Text(if (busy) "提交中…" else "提交举报")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") }
        },
    )
}
