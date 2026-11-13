@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package cn.mce.starlight.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cn.mce.starlight.data.TextKind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.Post
import cn.mce.starlight.data.ContentBlock
import cn.mce.starlight.data.PollData
import cn.mce.starlight.data.PostActions
import cn.mce.starlight.data.RateClient
import cn.mce.starlight.data.SmileyClient
import cn.mce.starlight.data.ThreadDetail
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun PostItem(
    post: Post,
    onReply: () -> Unit,
    onRate: () -> Unit,
    onOpenUser: () -> Unit,
    // v0.4.2：点击正文图片 → 全屏大图
    onImageClick: (String) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
) {
    // v0.4.7：帖子正文字号独立缩放（设置 → 外观 → 帖子正文字号）
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val postScale = cn.mce.starlight.core.SettingsStore.threadFontScale
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * postScale),
    ) {
    // v0.4.1：类酷安观感——每个楼层一张卡片（圆角 + 浅底色），与页面背景区分开
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .then(
                if (onLongPress == null) Modifier
                else Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        // v0.3.8 楼层头部：头像 40dp、楼层徽标 + 时间（单行省略），评分/回复移到楼层底部
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenUser),
            ) {
                val url = post.avatarUrl
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = "查看图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpenUser)) {
                Text(
                    post.author,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    post.dateText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // v0.3.9：楼层号统一右对齐（1楼/2楼…，不再显示「楼主」）
                    Spacer(Modifier.weight(1f))
                    post.floorLabel?.let { floor ->
                        Text(
                            floor,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // v0.3.8 正文：块间留白、正文行距加宽、彩色文字/引用块还原
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            post.blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Text -> PostTextBlock(block)
                    is ContentBlock.Table -> PostTableBlock(block)
                    is ContentBlock.Image -> if (block.url.contains("/smiley/")) {
                        // 正文表情（小红龙等）：按原尺寸小图显示，不要拉满整行
                        AsyncImage(
                            model = block.url,
                            contentDescription = "查看图片",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(30.dp),
                        )
                    } else AsyncImage(
                        model = block.url,
                        contentDescription = "查看图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(block.url) },
                    )
                }
            }
        }
        // 操作按钮放楼层底部右侧，头部只保留「谁 / 何时 / 几楼」
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!post.isOp) {
                TextButton(onClick = onRate) { Text("评分", style = MaterialTheme.typography.labelSmall) }
            }
            TextButton(onClick = onReply) { Text("回复", style = MaterialTheme.typography.labelSmall) }
        }
    }
    }
}
    }

/**
 * 正文文字块（v0.3.8）：
 * - 彩色文字 / 加粗 / 斜体 / 下划线 / 删除线 / `<font size>` 通过 AnnotatedString 还原；
 * - 行距比默认 bodyMedium 略宽（中文长帖更好读）；
 * - 引用块（kind = Quote）用左侧竖条 + 灰色小字区分。
 * `remember(block)` 避免每次重组都重建 AnnotatedString。
 */
/** 正文里的网址（v0.3.10）：点击直接用系统默认浏览器打开 */
private val postUrlRegex = Regex("""https?://[^\s<>"'，。；、）】\[\]]+""")

@Composable
internal fun PostTextBlock(block: ContentBlock.Text) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val quotedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    // v0.3.10：可点击的网址 = 解析层给出的 <a href> 区间 + 正文里裸写的 http(s) 链接
    val urlRanges = remember(block) {
        val fromHtml = block.links.map { Triple(it.start, it.end, it.url) }
        val fromText = postUrlRegex.findAll(block.text)
            .map { m -> Triple(m.range.first, m.range.last + 1, m.value) }
            .toList()
        (fromHtml + fromText)
            .filter { it.first >= 0 && it.second <= block.text.length && it.second > it.first }
            .distinctBy { it.first to it.second }
    }
    val annotated = remember(block, baseColor, quotedColor, linkColor) {
        buildAnnotatedString {
            append(block.text)
            block.spans.forEach { span ->
                val start = span.start.coerceIn(0, block.text.length)
                val end = span.end.coerceIn(start, block.text.length)
                if (end <= start) return@forEach
                val color = span.color?.let { Color(it) }
                addStyle(
                    SpanStyle(
                        color = color ?: (if (block.kind == TextKind.Quote) quotedColor else baseColor),
                        fontWeight = if (span.bold) FontWeight.Bold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                        textDecoration = when {
                            span.underline && span.strike -> TextDecoration.combine(
                                listOf(TextDecoration.Underline, TextDecoration.LineThrough),
                            )
                            span.underline -> TextDecoration.Underline
                            span.strike -> TextDecoration.LineThrough
                            else -> null
                        },
                        fontSize = span.sizeEm?.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Em) } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                    ),
                    start,
                    end,
                )
            }
            // 网址高亮 + 可点击（点击自动拉起系统默认浏览器）
            urlRanges.forEach { (start, end, _) ->
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    start,
                    end,
                )
            }
        }
    }
    val uriHandler = LocalUriHandler.current
    val onUrlClick: (Int) -> Unit = { offset ->
        urlRanges.firstOrNull { offset >= it.first && offset < it.second }?.let { (_, _, url) ->
            runCatching { uriHandler.openUri(url) }
        }
    }
    val textStyle = if (block.kind == TextKind.Quote) {
        MaterialTheme.typography.bodySmall.copy(lineHeight = 21.sp)
    } else {
        MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp, fontSize = 15.sp)
    }
    if (block.kind == TextKind.Quote) {
        // v0.4.1：被引用的内容加独立引用框（圆角 + 浅底色 + 「引用」标签），与正文区分
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                "引用",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.padding(top = 2.dp))
            ClickableText(
                text = annotated,
                style = textStyle.copy(color = quotedColor),
                onClick = onUrlClick,
            )
        }
    } else {
        ClickableText(
            text = annotated,
            style = textStyle.copy(color = baseColor),
            onClick = onUrlClick,
        )
    }
}

/**
 * v0.4.0：帖子里的表格（`[table]`/HTML 表格）按行列渲染：
 * 表头高亮、单元格描边、超宽表格可横向滚动（长表格比以前摊平成文字好读很多）。
 */
@Composable
internal fun PostTableBlock(table: ContentBlock.Table) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
    ) {
        table.rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    val isHeader = rowIndex == 0 && table.hasHeader
                    Box(
                        Modifier
                            .widthIn(min = 96.dp, max = 240.dp)
                            .background(if (isHeader) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .border(0.5.dp, borderColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isHeader) FontWeight.SemiBold else null,
                        )
                    }
                }
            }
        }
    }
}

