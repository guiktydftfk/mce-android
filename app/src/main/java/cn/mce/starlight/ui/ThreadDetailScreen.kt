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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.Post
import cn.mce.starlight.data.ContentBlock
import cn.mce.starlight.data.PostActions
import cn.mce.starlight.data.RateClient
import cn.mce.starlight.data.ThreadDetail
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private sealed interface DetailState {
    data object Loading : DetailState
    data class Error(val message: String) : DetailState
    data class Content(val detail: ThreadDetail) : DetailState
}

/** 帖子详情：楼层 + 图片 + 楼层回复 + 评分 */
@Composable
fun ThreadDetailScreen(
    tid: Int,
    fallbackTitle: String,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    onOpenUser: (Int, String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val repo = remember { HttpForumRepository() }
    var state by remember { mutableStateOf<DetailState>(DetailState.Loading) }
    var replyText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Post?>(null) }
    var sending by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var rateForm by remember { mutableStateOf<RateClient.Form?>(null) }
    var ratePid by remember { mutableStateOf<Int?>(null) }
    var page by remember { mutableStateOf(1) }

    // 切换帖子必须重置状态，避免上一个帖子的错误/内容串到新帖子
    LaunchedEffect(tid) {
        state = DetailState.Loading
        page = 1
        runCatching { repo.loadThreadDetail(tid, 1) }
            .onSuccess { detail ->
                state = if (detail.posts.isEmpty()) DetailState.Error("没有解析到楼层内容")
                else DetailState.Content(detail)
            }
            .onFailure { state = DetailState.Error(it.message ?: "加载失败") }
    }

    suspend fun load(targetPage: Int = page) {
        runCatching { repo.loadThreadDetail(tid, targetPage) }
            .onSuccess {
                state = if (it.posts.isEmpty()) DetailState.Error("没有解析到楼层内容")
                else DetailState.Content(it)
            }
            .onFailure { state = DetailState.Error(it.message ?: "加载失败") }
    }

    LaunchedEffect(tid, reloadKey) {
        page = 1
        load(1)
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val detail = (state as? DetailState.Content)?.detail
    val title = detail?.title ?: fallbackTitle

    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            is DetailState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            is DetailState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { scope.launch { state = DetailState.Loading; load() } }) {
                        Text("重试")
                    }
                }
            }

            is DetailState.Content -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topInset + 56.dp, bottom = 100.dp),
            ) {
                item {
                    Text(
                        text = s.detail.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )
                    s.detail.rewardCount?.let { count ->
                        Text(
                            "已有 $count 人打赏楼主",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = {
                            val opPid = s.detail.posts.firstOrNull()?.pid
                            if (opPid != null) {
                                scope.launch {
                                    val f = RateClient.loadForm(tid, opPid)
                                    if (f == null) toast = "赞赏表单加载失败（可能未登录）" else rateForm = f
                                }
                            }
                        }) { Text("赞赏") }

                        TextButton(onClick = {
                            scope.launch {
                                val r = PostActions.favorite(tid)
                                toast = r.message
                            }
                        }) { Text("收藏") }

                        if (s.detail.hasNextPage) {
                            TextButton(onClick = {
                                page += 1
                                scope.launch { load(page) }
                            }) { Text("下一页") }
                        } else {
                            Text(
                                "已是最后一页",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
                items(s.detail.posts, key = { it.pid }) { post ->
                    PostItem(
                        post = post,
                        onReply = {
                            replyTo = post
                            toast = "正在回复 ${post.author}（${post.floorLabel}）"
                        },
                        onOpenUser = { onOpenUser(post.uid ?: 0, post.author ?: "") },
                        onRate = {
                            scope.launch {
                                val f = RateClient.loadForm(tid, post.pid)
                                if (f == null) toast = "评分表单加载失败（可能未登录）" else rateForm = f
                            }
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }
        }

        ImmersiveTopBar(title = title, modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)

        detail?.let { d ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = {
                        Text(replyTo?.let { "回复 ${it.author}" } ?: "说点什么…")
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = !sending && replyText.isNotBlank() && d.fid != null,
                    onClick = {
                        val fid = d.fid ?: return@TextButton
                        sending = true
                        scope.launch {
                            val r = PostActions.reply(tid, fid, replyText.trim(), replyTo?.pid)
                            sending = false
                            toast = r.message
                            if (r.ok) {
                                replyText = ""
                                replyTo = null
                                // 回帖的 POST 响应是提示/跳转页，不能直接当帖子页解析，
                                // 必须重新 GET（带时间戳绕过缓存）再渲染。
                                val fresh = runCatching { repo.loadThreadDetail(tid, page, noCache = true) }.getOrNull()
                                if (fresh != null && fresh.posts.isNotEmpty()) {
                                    state = DetailState.Content(fresh)
                                } else {
                                    // 兜底：稍等再试一次，仍失败则提示手动刷新
                                    kotlinx.coroutines.delay(1200)
                                    val retry = runCatching { repo.loadThreadDetail(tid, page, noCache = true) }.getOrNull()
                                    if (retry != null && retry.posts.isNotEmpty()) {
                                        state = DetailState.Content(retry)
                                    } else {
                                        // 保留当前页面内容，仅提示（不要清空导致"没有解析到楼层"）
                                        toast = "回复成功，列表暂未刷新，可下拉重试"
                                    }
                                }
                            }
                        }
                    },
                ) { Text(if (sending) "发送中" else "回复") }
            }
        }

        toast?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    // 评分弹窗
    val form = rateForm
    if (form != null) {
        RateDialog(
            form = form,
            onDismiss = { rateForm = null; ratePid = null },
            onSubmit = { score, reason ->
                rateForm = null
                scope.launch {
                    val (ok, msg) = RateClient.submit(form, score, reason)
                    toast = msg
                    if (ok) load()
                }
            },
        )
    }
}

@Composable
private fun RateDialog(
    form: RateClient.Form,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var score by remember { mutableStateOf(form.scoreMin.toString()) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评分 / 赞赏") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "可给 ${form.scoreMin}~${form.scoreMax} 分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (form.scoreMin..form.scoreMax).forEach { v ->
                        TextButton(onClick = { score = v.toString() }) {
                            Text(if (score == v.toString()) "✓ $v 分" else "$v 分")
                        }
                    }
                }
                if (form.reasons.isNotEmpty()) {
                    Text("可选理由", style = MaterialTheme.typography.bodySmall)
                    form.reasons.take(4).forEach { preset ->
                        Text(
                            preset,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reason = preset }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("评分理由") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(score, reason) }) { Text("提交") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PostItem(
    post: Post,
    onReply: () -> Unit,
    onRate: () -> Unit,
    onOpenUser: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenUser),
            ) {
                val url = post.avatarUrl
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpenUser)) {
                Text(post.author, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(post.floorLabel, post.dateText).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!post.isOp) {
                TextButton(onClick = onRate) { Text("评分", style = MaterialTheme.typography.labelSmall) }
            }
            TextButton(onClick = onReply) { Text("回复", style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.height(8.dp))
        post.blocks.forEach { block ->
            when (block) {
                is ContentBlock.Text -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is ContentBlock.Image -> AsyncImage(
                    model = block.url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}
