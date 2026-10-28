package cn.mce.starlight.ui

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mce.starlight.data.BbcodePreview
import coil.compose.AsyncImage

/**
 * 发帖预览页（v0.4.0）。
 *
 * 按用户要求：**预览是独立页面**（点「发帖预览」才生成一次，不随编辑实时刷新），
 * 展示发布后的近似效果：标题、正文样式、图片位置（含本地刚插入的图片）、投票选项。
 */
@Composable
fun DraftPreviewScreen(
    title: String,
    content: String,
    images: List<Pair<Uri, String>>,
    pollOptions: List<String> = emptyList(),
    onBack: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val blocks = androidx.compose.runtime.remember(content) { BbcodePreview.render(content) }
    val attachUri = androidx.compose.runtime.remember(images.toList()) {
        images.associateBy({ it.second }, { it.first })
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = topInset),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("‹ 返回编辑") }
                    Text(
                        "发帖预览",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider()
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title.ifBlank { "（未填标题）" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    color = if (title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider()
                if (pollOptions.isNotEmpty()) {
                    Text(
                        "投票选项",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    pollOptions.forEachIndexed { index, option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(option, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider()
                }
                if (blocks.isEmpty()) {
                    Text(
                        "（正文为空）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                blocks.forEach { block ->
                    when (block) {
                        is BbcodePreview.Block.Text -> {
                            val annotated = buildPreviewString(block.segments)
                            if (annotated.isNotEmpty()) {
                                val style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 23.sp,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                androidx.compose.foundation.text.BasicText(
                                    text = annotated,
                                    style = style.copy(
                                        textAlign = when (block.align) {
                                            "center" -> androidx.compose.ui.text.style.TextAlign.Center
                                            "right" -> androidx.compose.ui.text.style.TextAlign.End
                                            else -> androidx.compose.ui.text.style.TextAlign.Start
                                        },
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        is BbcodePreview.Block.Video -> {
                            // 视频：发布后由论坛播放器展开，这里只做"视频卡片"预览（含地址）
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "▶ 视频",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    block.src,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }

                        is BbcodePreview.Block.Image -> {
                            val model: Any? = block.attachAid?.let { attachUri[it] } ?: block.src
                            if (model != null && model.toString().isNotBlank()) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 420.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 把 [BbcodePreview.BbSegment] 转成 AnnotatedString（颜色/加粗/斜体/下划线/链接） */
private fun buildPreviewString(segments: List<BbcodePreview.BbSegment>): AnnotatedString =
    buildAnnotatedString {
        segments.forEach { seg ->
            val start = length
            append(seg.text)
            val end = length
            if (end <= start) return@forEach
            addStyle(
                SpanStyle(
                    fontWeight = if (seg.bold) FontWeight.Bold else null,
                    fontStyle = if (seg.italic) FontStyle.Italic else null,
                    textDecoration = if (seg.underline) TextDecoration.Underline else null,
                    color = seg.color?.let { raw ->
                        runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull()
                    } ?: Color.Unspecified,
                ),
                start,
                end,
            )
            if (seg.url != null) {
                addStyle(
                    SpanStyle(
                        color = Color(0xFF4A90D9),
                        textDecoration = TextDecoration.Underline,
                    ),
                    start,
                    end,
                )
            }
        }
    }
