package cn.mce.starlight.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.ImageDiagClient
import kotlinx.coroutines.launch

/** 图片自检：输入帖子 ID，输出图片请求探测报告（可复制） */
@Composable
fun ImageDiagScreen(
    onMenu: () -> Unit,
    initialTid: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tidText by remember { mutableStateOf(initialTid) }
    var loading by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = topInset + 64.dp, start = 14.dp, end = 14.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = tidText,
                onValueChange = { tidText = it.filter { ch -> ch.isDigit() } },
                label = { Text("帖子 ID（tid）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !loading && tidText.isNotBlank(),
                    onClick = {
                        loading = true
                        report = null
                        scope.launch {
                            val r = runCatching { ImageDiagClient.run(tidText.toInt()) }
                            loading = false
                            report = r.map { buildString {
                                appendLine("帖子：${it.threadUrl}")
                                appendLine("HTML 长度：${it.htmlLength}")
                                appendLine("<img> 数量：${it.imgTags}")
                                appendLine("解析出图片：${it.parsedCount} 张")
                                it.notes.forEach { note -> appendLine("提示：$note") }
                                appendLine("---- 探测结果 ----")
                                it.probes.forEach { p ->
                                    appendLine("[${p.code}] ${p.contentType} len=${p.length} html=${p.looksHtml}")
                                    appendLine("   ${p.url}")
                                    appendLine("   ${p.preview}")
                                }
                            } }.getOrElse { "自检失败：${it.message}" }
                        }
                    },
                ) { Text(if (loading) "自检中…" else "开始自检") }

                if (report != null) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("图片自检", report))
                    }) { Text("复制报告") }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            report?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        ImmersiveTopBar(title = "图片自检", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}
