package cn.mce.starlight.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
