package cn.mce.starlight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.PostActions
import kotlinx.coroutines.launch

/** 发帖弹窗（原生 POST） */
@Composable
fun NewThreadDialog(
    fid: Int,
    onDismiss: () -> Unit,
    onPosted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("发新帖") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("标题") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("内容") },
                    enabled = !loading,
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (loading) {
                    Spacer(Modifier.height(10.dp))
                    CircularProgressIndicator(Modifier.height(20.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && subject.isNotBlank() && message.isNotBlank(),
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        val result = PostActions.newThread(fid, subject.trim(), message.trim())
                        loading = false
                        if (result.ok) onPosted() else error = result.message ?: "发布失败"
                    }
                },
            ) { Text("发布") }
        },
        dismissButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text("取消") } },
    )
}
