@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.mce.starlight.core.RecentSmileyStore
import cn.mce.starlight.data.SmileyClient
import coil.compose.AsyncImage

/**
 * v0.4.7：仿微信/QQ 的底部表情面板。
 *
 * - 底部弹出、圆角面板，顶部横向分类（最近 / 各套表情 / 本地自定义）
 * - 8 列大图标网格，点选插入光标处；长按自定义表情可直接删除
 * - 「最近」自动记录最近使用（[RecentSmileyStore]）
 * - 本地表情支持「＋」从相册导入与「管理」逐个删除
 *
 * 函数签名与旧版保持一致，调用方无需改动。
 */
@Composable
fun SmileyPickerDialog(
    sets: List<SmileyClient.SmileySet>,
    selectedIndex: Int,
    onSelectSet: (Int) -> Unit,
    onPick: (SmileyClient.Smiley) -> Unit,
    customEmojis: List<java.io.File> = emptyList(),
    onAddCustom: (() -> Unit)? = null,
    onPickCustom: ((java.io.File) -> Unit)? = null,
    onDeleteCustom: ((java.io.File) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var showManage by remember { mutableStateOf(false) }
    // 分类：-1 = 最近；0..n-1 = 表情套；-2 = 本地自定义
    val hasCustom = onAddCustom != null || customEmojis.isNotEmpty()
    var tab by remember { mutableIntStateOf(selectedIndex.coerceAtLeast(0)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    // 顶部标题 + 操作
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "表情",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (hasCustom && customEmojis.isNotEmpty()) {
                            Text(
                                "管理",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showManage = true }.padding(horizontal = 8.dp),
                            )
                        }
                        Text(
                            "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 8.dp),
                        )
                    }

                    // 分类标签（横向滚动）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryChip("最近", active = tab == -1) { tab = -1 }
                        sets.forEachIndexed { index, set ->
                            CategoryChip(set.name, active = tab == index) {
                                tab = index
                                onSelectSet(index)
                            }
                        }
                        if (hasCustom) {
                            CategoryChip("自定义", active = tab == -2) { tab = -2 }
                        }
                    }

                    // 内容区
                    when {
                        tab == -1 -> {
                            val recent = remember(tab) { RecentSmileyStore.recent() }
                            if (recent.isEmpty()) {
                                CenterHint("还没有最近使用的表情\n点选任意表情后会出现在这里")
                            } else {
                                EmojiGrid(
                                    count = recent.size,
                                    onBind = { i ->
                                        val s = recent[i]
                                        EmojiCell(s.url) {
                                            RecentSmileyStore.record(s)
                                            onPick(s)
                                        }
                                    },
                                )
                            }
                        }

                        tab == -2 -> {
                            if (customEmojis.isEmpty()) {
                                CenterHint("还没有本地表情\n点下方「＋」从相册导入，长按可删除")
                            }
                            EmojiGrid(
                                count = customEmojis.size + (if (onAddCustom != null) 1 else 0),
                                onBind = { i ->
                                    if (i == 0 && onAddCustom != null) {
                                        Box(
                                            Modifier.aspectRatio(1f),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "＋",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable { onAddCustom?.invoke() },
                                            )
                                        }
                                    } else {
                                        val f = customEmojis[if (onAddCustom != null) i - 1 else i]
                                        AsyncImage(
                                            model = f,
                                            contentDescription = "本地表情",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .padding(4.dp)
                                                .combinedClickable(
                                                    onClick = { onPickCustom?.invoke(f) },
                                                    onLongClick = { onDeleteCustom?.invoke(f) },
                                                ),
                                        )
                                    }
                                },
                            )
                        }

                        else -> {
                            val set = sets.getOrNull(tab) ?: sets.firstOrNull()
                            if (set == null) {
                                CenterHint("正在加载表情…")
                            } else {
                                EmojiGrid(
                                    count = set.smileys.size,
                                    onBind = { i ->
                                        val s = set.smileys[i]
                                        EmojiCell(s.url) {
                                            RecentSmileyStore.record(s)
                                            onPick(s)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 本地表情管理（逐个删除）
    if (showManage) {
        AlertDialog(
            onDismissRequest = { showManage = false },
            title = { Text("本地表情管理") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (customEmojis.isEmpty()) {
                        Text("还没有本地表情，点「＋」从相册添加", style = MaterialTheme.typography.bodySmall)
                    }
                    customEmojis.forEach { f ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = f,
                                contentDescription = "本地表情",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                f.name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                maxLines = 1,
                            )
                            TextButton(onClick = { onDeleteCustom?.invoke(f) }) { Text("删除") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showManage = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun CategoryChip(text: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmojiCell(url: String, onClick: () -> Unit) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clickable(onClick = onClick),
    )
}

/** 统一的 8 列表情网格 */
@Composable
private fun EmojiGrid(count: Int, onBind: @Composable (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(count) { i -> onBind(i) }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}
