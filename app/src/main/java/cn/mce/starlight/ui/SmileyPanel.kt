package cn.mce.starlight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.SmileyClient
import coil.compose.AsyncImage

/**
 * 表情选择弹窗（v0.3.6 从 NewThreadScreen 抽出，发帖页 / 回帖框共用）。
 *
 * 表情集由 [SmileyClient.load] 提供（4 套完整表情，code 形如 `{:5_176:}` 为论坛原生表情代码）。
 * 选中表情后由调用方把 [SmileyClient.Smiley.code] 插入输入框光标处。
 */
@Composable
fun SmileyPickerDialog(
    sets: List<SmileyClient.SmileySet>,
    selectedIndex: Int,
    onSelectSet: (Int) -> Unit,
    onPick: (SmileyClient.Smiley) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("表情") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (sets.isEmpty()) {
                    Text("正在加载表情…", style = MaterialTheme.typography.bodySmall)
                } else {
                    val set = sets.getOrNull(selectedIndex) ?: sets.first()
                    if (sets.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            sets.forEachIndexed { index, item ->
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index == selectedIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .clickable { onSelectSet(index) }
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        set.smileys.chunked(8).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { s ->
                                    AsyncImage(
                                        model = s.url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable { onPick(s) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
