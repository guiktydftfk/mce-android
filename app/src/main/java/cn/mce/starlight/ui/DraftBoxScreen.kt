package cn.mce.starlight.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.Draft
import cn.mce.starlight.core.DraftStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val draftTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

/**
 * 草稿箱（v0.3.3）：本地草稿列表。
 *
 * 之前「存草稿」发的是 Discuz 的 save=1（手机模板根本不支持），草稿要么丢失、
 * 要么被当成正常帖发布，用户找不到草稿箱。现在草稿全部存本地，单独一个入口，
 * 不再和「我的主题」里的已发布帖子混在一起。
 */
@Composable
fun DraftBoxScreen(
    onMenu: () -> Unit,
    onOpenDraft: (Long) -> Unit,
    onComposeNew: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val drafts = DraftStore.drafts

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
                    DraftRow(
                        draft = draft,
                        onOpen = { onOpenDraft(draft.id) },
                        onDelete = { DraftStore.delete(draft.id) },
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
}

@Composable
private fun DraftRow(
    draft: Draft,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            draft.title.ifBlank { "（未命名草稿）" },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                append(draft.fidName.ifBlank { "未知板块" })
                append(" · ")
                append(draftTimeFormat.format(Date(draft.savedAt)))
                if (draft.pollMode) append(" · 投票")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (draft.content.isNotBlank()) {
            Text(
                draft.content.replace('\n', ' ').take(80),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row {
            TextButton(onClick = onOpen) { Text("继续编辑") }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
