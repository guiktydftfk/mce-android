@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package cn.mce.starlight.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.Draft
import cn.mce.starlight.core.DraftStore
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.PostActions
import cn.mce.starlight.data.SmileyClient
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Box(
            Modifier
                .width(selectedWidth(selected))
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}

internal fun selectedWidth(selected: Boolean) = if (selected) 28.dp else 0.dp

@Composable
internal fun ToolButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
internal fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 移动版编辑器原有的 5 个预设色（`data-color`） */
private val SITE_COLORS = listOf("#222222", "#E64346", "#FF9501", "#AAC238", "#1A497D")

/** v0.4.1：对齐标签（用于替换旧对齐方式，避免嵌套 [align]） */
internal val alignOpenTailRegex = Regex("""\[align=(?:left|center|right)]$""")
internal val alignAnyRegex = Regex("""\[/?align[^\]]*]""", RegexOption.IGNORE_CASE)

/**
 * 网页版论坛编辑器色板（v0.4.0 补充）：
 * 前 30 色按用户提供的网页编辑器色板截图逐像素取样，后 6 色为黑/灰/白与常用亮色。
 */
private val WEB_COLORS = listOf(
    "#70482E", "#505C36", "#18611A", "#3B345D", "#020356", "#3B065E",
    "#A96F26", "#767824", "#226D1B", "#227175", "#070487", "#5A616B",
    "#75481F", "#7D8D42", "#3B6D50", "#428985", "#233877", "#5E0D5C",
    "#AE802B", "#918F2B", "#54B331", "#35B5B4", "#2889A9", "#65307E",
    "#503D1C", "#3C3A13", "#30621D", "#255152", "#25434E", "#4F264E",
    "#000000", "#444444", "#888888", "#CCCCCC", "#FFFFFF", "#E64346",
)

/** 选颜色弹窗：选中文字后点「颜色」→ 直接点色块即可套用（36 色，来自网页版编辑器色板） */
@Composable
internal fun ColorPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = (SITE_COLORS + WEB_COLORS).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文字颜色") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "先选中要变色的文字，再点色块（色板含网页版论坛编辑器的预设色）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                palette.chunked(6).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowColors.forEach { hex ->
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { onPick(hex) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
