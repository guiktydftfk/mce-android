package cn.mce.starlight.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import cn.mce.starlight.core.Draft
import cn.mce.starlight.core.DraftStore

/**
 * 草稿箱（v0.3.3）：本地草稿列表。
 *
 * 之前「存草稿」发的是 Discuz 的 save=1（手机模板根本不支持），草稿要么丢失、
 * 要么被当成正常帖发布，用户找不到草稿箱。现在草稿全部存本地，单独一个入口，
 * 不再和「我的主题」里的已发布帖子混在一起。
 *
 * v0.3.7：列表行抽成共用 [DraftListItem]（「我的主题」里也会用到）。
 * v0.4.1：删除草稿前加二次确认，避免误删（用户反馈）。
 */
@Composable
fun DraftBoxScreen(
    onMenu: () -> Unit,
    onOpenDraft: (Long) -> Unit,
    onComposeNew: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val drafts = DraftStore.drafts
    // v0.4.1：待确认删除的草稿
    var pendingDelete by remember { mutableStateOf<Draft?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (drafts.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                EmptyHint("还没有草稿\n在发帖页点「存草稿」会保存到这里")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topInset + 64.dp, bottom = 110.dp),
            ) {
                items(drafts, key = { it.id }) { draft ->
                    DraftListItem(
                        draft = draft,
                        onOpen = { onOpenDraft(draft.id) },
                        onDelete = { pendingDelete = draft },
                    )
                    HorizontalDivider()
                }
            }
        }

        ImmersiveTopBar(
            title = "草稿箱",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
            actionLabel = "新草稿",
            onAction = onComposeNew,
        )
    }

    // v0.4.1：删除草稿二次确认（误删无法恢复）
    pendingDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这份草稿？") },
            text = {
                Text(
                    "草稿「${draft.title.ifBlank { "无标题" }}」删除后无法恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DraftStore.delete(draft.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}
