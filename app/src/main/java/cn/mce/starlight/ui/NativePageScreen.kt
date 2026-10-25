package cn.mce.starlight.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.GenericPage
import cn.mce.starlight.data.GenericPageClient
import cn.mce.starlight.data.PageBlock
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface PageState {
    data object Loading : PageState
    data class Error(val message: String) : PageState
    data class Content(val page: GenericPage) : PageState
}

/**
 * 通用原生页面：抓取 Discuz 页面 → 解析为文本/链接/图片/表单 → Compose 原生绘制。
 * 用于签到、任务中心等「表单类」页面，取代 WebView。
 */
@Composable
fun NativePageScreen(
    initialUrl: String,
    fallbackTitle: String,
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stack by remember { mutableStateOf(listOf(initialUrl)) }
    var state by remember { mutableStateOf<PageState>(PageState.Loading) }
    var submitting by remember { mutableStateOf(false) }

    val currentUrl = stack.last()

    suspend fun reload() {
        state = PageState.Loading
        runCatching { GenericPageClient.load(currentUrl) }
            .onSuccess { state = PageState.Content(it) }
            .onFailure { state = PageState.Error(it.message ?: "加载失败") }
    }

    LaunchedEffect(currentUrl) { reload() }

    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    val page = (state as? PageState.Content)?.page
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is PageState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is PageState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { scope.launch { reload() } }) { Text("重试") }
                }
            }

            is PageState.Content -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topInset + 56.dp, bottom = 110.dp),
            ) {
                items(s.page.blocks) { block ->
                    when (block) {
                        is PageBlock.Heading -> Text(
                            block.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )

                        is PageBlock.Paragraph -> Text(
                            block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )

                        is PageBlock.Image -> AsyncImage(
                            model = block.url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )

                        is PageBlock.Link -> Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when {
                                        !block.internal -> runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(block.url)))
                                        }

                                        block.url.contains("viewthread") && tidOf(block.url) != null ->
                                            onOpenThread(tidOf(block.url)!!, block.text)

                                        else -> stack = stack + block.url
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        is PageBlock.Form -> Button(
                            onClick = {
                                submitting = true
                                scope.launch {
                                    GenericPageClient.submit(block)
                                    submitting = false
                                    reload()
                                }
                            },
                            enabled = !submitting,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text(if (submitting) "提交中…" else block.submitLabel) }
                    }
                }
            }
        }

        ImmersiveTopBar(
            title = page?.title ?: fallbackTitle,
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}

private fun tidOf(url: String): Int? =
    Regex("""tid=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
