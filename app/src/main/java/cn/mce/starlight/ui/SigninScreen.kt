package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** 签到页：统计 + 签到按钮 + 排行榜（今日/本月/总） */
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
    var rankType by remember { mutableStateOf("today") }

    suspend fun load() {
        state = SignState.Loading
        runCatching { SigninClient.load(rankType) }
            .onSuccess { state = SignState.Content(it) }
            .onFailure { state = SignState.Error(it.message ?: "加载失败") }
    }

    LaunchedEffect(reloadKey, rankType) { load() }

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
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topInset + 64.dp, bottom = 48.dp),
                ) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
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
                    }

                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                when {
                                    data.alreadySigned -> "今天已签到 ✔"
                                    else -> "今天还没有签到"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (data.alreadySigned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            data.level?.let {
                                Text(
                                    "签到等级 Lv $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            message?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                enabled = !busy && !data.alreadySigned && data.formhash != null,
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        val (ok, msg) = SigninClient.signIn(data)
                                        busy = false
                                        message = msg
                                        if (ok) load()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (busy) "签到中…" else "立即签到") }
                        }
                    }

                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RankTab("今日", rankType == "today") { rankType = "today" }
                            RankTab("本月", rankType == "month") { rankType = "month" }
                            RankTab("总排行", rankType == "total") { rankType = "total" }
                        }
                    }

                    if (data.ranks.isEmpty()) {
                        item {
                            Text(
                                "暂无排行榜数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        items(data.ranks) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    entry.username,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    entry.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        ImmersiveTopBar(title = "签到", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

@Composable
private fun RankTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
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
