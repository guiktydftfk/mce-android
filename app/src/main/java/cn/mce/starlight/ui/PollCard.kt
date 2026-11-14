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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*


@Composable
internal fun PollCard(
    poll: PollData,
    submitting: Boolean,
    onSubmit: (List<String>) -> Unit,
    onOpenVoters: () -> Unit,
) {
    var selected by remember(poll.action, poll.voted, poll.options) { mutableStateOf(setOf<String>()) }
    var overLimitHint by remember(poll.action) { mutableStateOf(false) }

    val sumVotes = poll.totalVotes ?: poll.options.sumOf { it.votes }
    val showResult = poll.voted || poll.closed || sumVotes > 0
    val canVote = !poll.voted && !poll.closed

    fun toggle(id: String) {
        if (!canVote || submitting) return
        if (!poll.multi) {
            selected = if (id in selected) emptySet() else setOf(id)
            overLimitHint = false
            return
        }
        selected = when {
            id in selected -> selected - id
            poll.maxChoices == Int.MAX_VALUE || selected.size < poll.maxChoices -> {
                overLimitHint = false
                selected + id
            }
            else -> {
                overLimitHint = true
                selected
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("投票", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        // 类型 + 参与数据
        val meta = buildList {
            add(if (poll.multi) "多选" else "单选")
            if (poll.multi && poll.maxChoices != Int.MAX_VALUE) add("最多 ${poll.maxChoices} 项")
            poll.totalVoters?.let { add("$it 人参与") }
            if (sumVotes > 0) add("共 $sumVotes 票")
        }.joinToString(" · ")
        Text(
            meta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )

        poll.options.forEach { option ->
            val checked = option.chosen || option.id in selected
            val percent = option.percent
                ?: if (sumVotes > 0) option.votes * 100f / sumVotes else 0f
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = canVote && !submitting) { toggle(option.id) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (canVote) {
                    if (poll.multi) {
                        Checkbox(
                            checked = checked,
                            enabled = !submitting,
                            onCheckedChange = { toggle(option.id) },
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        RadioButton(
                            selected = checked,
                            enabled = !submitting,
                            onClick = { toggle(option.id) },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    // 已投票/已结束：用圆点标记我投的选项，而不是禁用控件
                    Box(
                        Modifier
                            .size(24.dp)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (showResult) {
                        Spacer(Modifier.height(4.dp))
                        PollBar(
                            fraction = (percent / 100f).coerceIn(0f, 1f),
                            highlight = checked,
                        )
                        Text(
                            "${option.votes} 票 · ${percent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }

        if (overLimitHint) {
            Text(
                "最多只能选 ${poll.maxChoices} 项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        when {
            poll.closed -> Text(
                "投票已结束",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            poll.voted -> Text(
                "您已经投过票，谢谢您的参与",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            else -> {
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = selected.isNotEmpty() && !submitting,
                    onClick = { onSubmit(selected.toList()) },
                ) {
                    Text(if (submitting) "提交中…" else "提交投票")
                }
            }
        }

        if (poll.viewVoteUrl != null) {
            Text(
                "查看投票参与人",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onOpenVoters() }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

/** 投票结果条：6dp 圆角，带增长动画 */
@Composable
internal fun PollBar(fraction: Float, highlight: Boolean) {
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 420),
        label = "pollBar",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                ),
        )
    }
}

