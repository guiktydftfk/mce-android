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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.UiError
import cn.mce.starlight.data.SpaceComposeClient
import kotlinx.coroutines.launch

/**
 * v0.4.8：个人空间发布 —— 日志（原生）/ 相册（网页兜底）。
 *
 * 日志走桌面版 `spacecp&ac=blog` 表单；相册上传涉及多文件 multipart，
 * 暂时提供「在网页中上传相册」入口，避免半成品上传导致图片丢失。
 */
@Composable
fun SpaceComposeScreen(
    onMenu: () -> Unit,
    onOpenPage: (String, String) -> Unit = { _, _ -> },
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) } // 0 日志 / 1 相册
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<SpaceComposeClient.BlogForm?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching { SpaceComposeClient.loadBlogForm() }
            .onSuccess { f ->
                form = f
                if (f == null) error = "日志发布页解析失败（可能未登录或论坛模板变更）"
                f?.fields?.forEach { field ->
                    if (field.name !in setOf("message") && field.value.isNotBlank()) {
                        values.putIfAbsent(field.name, field.value)
                    }
                }
            }
            .onFailure { error = UiError.friendly(it) }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = clampTopInset(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()) + 60.dp,
                        start = 14.dp,
                        end = 14.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("日志发布", "相册上传").forEachIndexed { i, label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (mode == i) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (mode == i) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { mode = i }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            when {
                mode == 1 -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("相册上传", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "相册上传需要多文件表单与相册 ID 状态，客户端暂未内置；\n请在网页中完成上传（登录态已共享）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Button(
                        onClick = { onOpenPage(SpaceComposeClient.albumPageUrl(), "上传相册") },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("在网页中上传相册") }
                }
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                error != null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            runCatching { SpaceComposeClient.loadBlogForm() }
                                .onSuccess { form = it; if (it == null) error = "日志发布页解析失败" }
                                .onFailure { error = UiError.friendly(it) }
                            loading = false
                        }
                    }) { Text("重试") }
                    TextButton(onClick = {
                        onOpenPage(
                            "https://mce.msi.cn/home.php?mod=spacecp&ac=blog&mobile=no",
                            "在网页中发布日志",
                        )
                    }) { Text("在网页中发布日志") }
                }
                else -> {
                    val f = form
                    if (f == null) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("日志发布页不可用", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 110.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val visible = f.fields.filter { it.name !in setOf("formhash") }
                            items(visible.size) { idx ->
                                val field = visible[idx]
                                when {
                                    field.options.isNotEmpty() -> {
                                        Text(
                                            field.label.ifBlank { field.name },
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            field.options.take(8).forEach { (value, label) ->
                                                Text(
                                                    label.ifBlank { value },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if ((values[field.name] ?: field.value) == value)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if ((values[field.name] ?: field.value) == value)
                                                                MaterialTheme.colorScheme.primaryContainer
                                                            else MaterialTheme.colorScheme.surfaceVariant,
                                                        )
                                                        .clickable { values[field.name] = value }
                                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                    field.multiline -> OutlinedTextField(
                                        value = values[field.name] ?: field.value,
                                        onValueChange = { values[field.name] = it },
                                        label = { Text(field.label.ifBlank { "正文" }) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 180.dp),
                                    )
                                    else -> OutlinedTextField(
                                        value = values[field.name] ?: field.value,
                                        onValueChange = { values[field.name] = it },
                                        label = { Text(field.label.ifBlank { field.name }) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            item {
                                message?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Button(
                                    enabled = !busy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val snapshot = f
                                            val result = runCatching {
                                                SpaceComposeClient.submitBlog(snapshot, values.toMap())
                                            }.getOrNull()
                                            busy = false
                                            message = result?.second ?: "发布失败，请重试"
                                            if (result?.first == true) {
                                                android.widget.Toast.makeText(
                                                    ctx, "日志已发布", android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                ) { Text(if (busy) "发布中…" else "发布日志") }
                            }
                        }
                    }
                }
            }
        }
        ImmersiveTopBar(
            title = "空间发布",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}
