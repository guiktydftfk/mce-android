package cn.mce.starlight.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.data.Net
import cn.mce.starlight.data.Post

/**
 * v0.4.6：楼层长按操作对话框（从 ThreadDetailScreen 拆出）。
 * 复制链接 / 举报 / 编辑（自己的）/ 删除（自己的）。
 */
@Composable
fun PostActionDialog(
    post: Post,
    tid: Int,
    title: String,
    onDismiss: () -> Unit,
    onReport: (String, String) -> Unit,
    onEdit: (Post) -> Unit,
    onDelete: (Post) -> Unit,
) {
    val ctx = LocalContext.current
    val isMine = post.uid != null && post.uid == SessionStore.uid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("楼层操作") },
        text = { Text("${post.author} · ${post.floorLabel ?: "楼主"}") },
        confirmButton = {
            TextButton(onClick = {
                val link = "${Net.BASE}/forum.php?mod=viewthread&tid=$tid&pid=${post.pid}"
                runCatching {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("帖子链接", link))
                }
                onDismiss()
            }) { Text("复制链接") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onReport(
                        "${Net.BASE}/forum.php?mod=misc&action=report&tid=$tid&pid=${post.pid}&mobile=no",
                        "举报",
                    )
                }) { Text("举报") }
                if (isMine) {
                    TextButton(onClick = { onEdit(post) }) { Text("编辑") }
                    TextButton(onClick = { onDelete(post) }) { Text("删除") }
                }
            }
        },
    )
}
