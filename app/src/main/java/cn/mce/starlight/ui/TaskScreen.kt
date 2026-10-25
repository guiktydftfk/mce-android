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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import cn.mce.starlight.data.TaskClient
import kotlinx.coroutines.launch

private sealed interface TaskState {
    data object Loading : TaskState
    data class Error(val message: String) : TaskState
    data class Content(val tasks: List<TaskClient.TaskItem>) : TaskState
}

/** 任务中心：标题 + 描述 + 奖励 + 申请 */
@Composable
fun TaskScreen(
    onMenu: () -> Unit,
    reloadKey: Int = 0,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TaskState>(TaskState.Loading) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        state = TaskState.Loading
        runCatching { TaskClient.load() }
            .onSuccess {
                state = if (it.isEmpty()) TaskState.Error("没有解析到任务（可能未登录或无开放任务）")
                else TaskState.Content(it)
            }
            .onFailure { state = TaskState.Error(it.message ?: "加载失败") }
    }

    LaunchedEffect(reloadKey) { load() }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is TaskState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is TaskState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { scope.launch { load() } }) { Text("重试") }
                }
            }

            is TaskState.Content -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topInset + 64.dp, bottom = 110.dp),
            ) {
                message?.let { msg ->
                    item {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }
                }
                items(s.tasks, key = { it.id }) { task ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                    ) {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            task.reward?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            task.hot?.let {
                                Text(
                                    "人气 $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        task.description?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = busyId == null,
                            onClick = {
                                busyId = task.id
                                message = null
                                scope.launch {
                                    val ok = TaskClient.apply(task)
                                    busyId = null
                                    message = if (ok) "已提交申请：${task.title}" else "提交失败"
                                    load()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busyId == task.id) "提交中…" else "立即申请") }
                    }
                }
            }
        }

        ImmersiveTopBar(title = "任务中心", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}
