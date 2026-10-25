package cn.mce.starlight.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.SigninClient
import kotlinx.coroutines.launch

private sealed interface SignState {
    data object Loading : SignState
    data class Error(val message: String) : SignState
    data class Content(val state: SigninClient.State) : SignState
}

/** 签到页（专用解析，不是通用页面渲染） */
@Composable
fun SigninScreen(
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    onNeedLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SignState>(SignState.Loading) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        state = SignState.Loading
        runCatching { SigninClient.load() }
            .onSuccess { state = SignState.Content(it) }
            .onFailure { state = SignState.Error(it.message ?: "加载失败") }
    }

    LaunchedEffect(reloadKey) { load() }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is SignState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is SignState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { scope.launch { load() } }) { Text("重试") }
                    if (s.message.contains("登录")) {
                        TextButton(onClick = onNeedLogin) { Text("去登录") }
                    }
                }
            }

            is SignState.Content -> {
                val data = s.state
                Column(Modifier.padding(top = topInset + 64.dp, start = 20.dp, end = 20.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem("今日排名", data.todayRank ?: "-")
                        StatItem("连续签到", data.continuous ?: "-")
                        StatItem("累计签到", data.total ?: "-")
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (data.alreadySigned) "今天已签到 ✔" else "今天还没有签到",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (data.alreadySigned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )

                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.height(18.dp))
                    Button(
                        enabled = !busy && !data.alreadySigned &&
                            (data.signForm != null || data.signActionUrl != null),
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                val ok = SigninClient.signIn(data)
                                busy = false
                                message = if (ok) "签到成功" else "签到失败，请稍后重试"
                                load()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "签到中…" else "立即签到") }

                    if (data.signForm == null && data.signActionUrl == null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "没有找到签到按钮（页面结构可能变化）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        ImmersiveTopBar(
            title = "签到",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
