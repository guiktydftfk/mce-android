package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var chat by remember { mutableStateOf<PersonalClient.PmChat?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    suspend fun load(scrollToEnd: Boolean = true) {
        runCatching { PersonalClient.loadPmChat(touid) }
            .onSuccess {
                chat = it
                error = null
                if (scrollToEnd && it.messages.isNotEmpty()) {
                    listState.requestScrollToItem(it.messages.size)
                }
            }
            .onFailure { error = it.message ?: "加载失败" }
    }
    LaunchedEffect(touid) { load() }

    Box(Modifier.fillMaxSize()) {
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (m.incoming) Arrangement.Start else Arrangement.End,
                        ) {
                            Text(
                                m.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (m.incoming) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .widthIn(max = 300.dp)
                                    .background(
                                        if (m.incoming) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
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
