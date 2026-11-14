package cn.mce.starlight.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * v0.4.7：帖子回帖输入区（从 ThreadDetailScreen 抽出，便于维护）。
 * 只负责 UI；发送逻辑、图片上传结果处理仍在调用方。
 */
@Composable
fun ReplyComposer(
    fid: Int?,
    replyText: TextFieldValue,
    onReplyTextChange: (TextFieldValue) -> Unit,
    replyToAuthor: String?,
    onCancelReply: () -> Unit,
    sending: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    images: List<Pair<Uri, String>>,
    onRemoveImage: (Pair<Uri, String>) -> Unit,
    uploadingImage: Boolean,
    onPickImage: () -> Unit,
    onOpenSmiley: () -> Unit,
    onOpenMention: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replyText,
                onValueChange = onReplyTextChange,
                placeholder = {
                    Text(replyToAuthor?.let { "回复 $it" } ?: "说点什么…")
                },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = !sending && canSend && fid != null,
                onClick = onSend,
            ) { Text(if (sending) "发送中" else "回复") }
        }
        // 已上传图片预览（点 × 移除）
        if (images.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                images.forEach { item ->
                    val (uri, _) = item
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = "回帖图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Text(
                            "×",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable { onRemoveImage(item) }
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        // 表情 / @ / 图片工具栏（可横滑，窄屏友好）
        ScrollableActionRow(horizontalPadding = 0) {
            TextButton(onClick = onOpenSmiley) { Text("表情") }
            TextButton(onClick = onOpenMention) { Text("@ 提及") }
            TextButton(enabled = !uploadingImage, onClick = onPickImage) {
                Text(if (uploadingImage) "上传中…" else "图片")
            }
            if (replyToAuthor != null) {
                TextButton(onClick = onCancelReply) { Text("取消楼层回复") }
            }
        }
    }
}
