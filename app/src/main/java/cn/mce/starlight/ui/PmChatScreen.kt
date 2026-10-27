package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import cn.mce.starlight.core.SessionStore
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.PersonalClient
import cn.mce.starlight.data.PostActions
import kotlinx.coroutines.launch

/** 私信聊天（v0.3.4）：原生气泡列表 + 发送框，替代此前不可用的网页壳 */
@Composable
fun PmChatScreen(
    touid: Int,
    name: String,
    onMenu: () -> Unit,
    onOpenUser: (Int, String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var chat by remember { mutableStateOf<PersonalClient.PmChat?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    // v0.3.9：屏内提示 2.6s 后自动消失（旧实现 toast 永不清空 → 小弹窗一直挂在界面上）
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2600)
            toast = null
        }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    suspend fun load(scrollToEnd: Boolean = true) {
        runCatching { PersonalClient.loadPmChat(touid) }
            .onSuccess {
                chat = it
                error = null
                if (scrollToEnd && it.messages.isNotEmpty()) {
                    listState.requestScrollToItem((it.messages.size - 1).coerceAtLeast(0))
                }
            }
            .onFailure { error = it.message ?: "加载失败" }
    }
    LaunchedEffect(touid) { load() }
    // 消息数量变化（首次加载 / 发送成功重拉）后自动滚到底部
    LaunchedEffect(chat?.messages?.size) {
        val n = chat?.messages?.size ?: 0
        if (n > 0) listState.requestScrollToItem(n - 1)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // v0.3.8：气泡最大宽度按屏宽 72% 计算（叠加头像与间距后视觉≈微信）
        val bubbleMax = maxWidth * 0.72f
        when {
            error != null && chat == null -> ErrorBlock(error!!) { scope.launch { load() } }
            chat == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = topInset + 56.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val msgs = chat!!.messages
                    if (msgs.isEmpty()) {
                        item { EmptyHint("还没有消息，打个招呼吧") }
                    }
                    items(msgs.size, key = { i -> "$i-${msgs[i].incoming}-${msgs[i].text.hashCode()}" }) { i ->
                        val m = msgs[i]
                        // v0.3.8：类微信气泡 —— 对方头像在左、我的头像在右，气泡带「小尾巴」圆角与时间
                        val showTime = i == 0 || msgs[i - 1].time != m.time
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (m.incoming) Arrangement.Start else Arrangement.End,
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (m.incoming) {
                                ChatAvatar(
                                    url = m.avatar,
                                    fallbackUid = touid,
                                    onClick = { onOpenUser(touid, name) },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(
                                horizontalAlignment = if (m.incoming) Alignment.Start else Alignment.End,
                            ) {
                                if (showTime && !m.time.isNullOrBlank()) {
                                    Text(
                                        m.time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 3.dp),
                                    )
                                }
                                Text(
                                    m.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (m.incoming) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .widthIn(max = bubbleMax)
                                        .background(
                                            if (m.incoming) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.primaryContainer,
                                            if (m.incoming) {
                                                RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                                            } else {
                                                RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                                            },
                                        )
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                )
                            }
                            if (!m.incoming) {
                                Spacer(Modifier.width(8.dp))
                                ChatAvatar(
                                    url = SessionStore.avatarUrl,
                                    fallbackUid = SessionStore.uid,
                                    onClick = null,
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .imePadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("发送私信…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    TextButton(
                        enabled = !sending && input.isNotBlank() && chat != null,
                        onClick = {
                            val c = chat ?: return@TextButton
                            sending = true
                            scope.launch {
                                val r = PostActions.sendPm(c.action, c.formhash, touid, input.trim())
                                sending = false
                                toast = r.message
                                if (r.ok) {
                                    input = ""
                                    load(scrollToEnd = true)
                                }
                            }
                        },
                    ) { Text(if (sending) "发送中" else "发送") }
                }
                toast?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        ImmersiveTopBar(
            title = name.ifBlank { "私信" },
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}

/** 聊天头像（v0.3.8）：对方 / 我 都显示；头像缺失时用 uid 拼站点默认头像，再不行显示占位圆 */
@Composable
private fun ChatAvatar(
    url: String?,
    fallbackUid: Int?,
    onClick: (() -> Unit)? = null,
) {
    val model = url?.takeIf { it.isNotBlank() }
        ?: fallbackUid?.takeIf { it > 0 }?.let { "https://mce.msi.cn/uc_server/avatar.php?uid=$it&size=small" }
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                "?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}