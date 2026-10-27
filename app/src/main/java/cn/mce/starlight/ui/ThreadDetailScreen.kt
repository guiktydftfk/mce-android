package cn.mce.starlight.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

private sealed interface DetailState {
    data object Loading : DetailState
    data class Error(val message: String) : DetailState
    data class Content(val detail: ThreadDetail) : DetailState
}

/** 帖子详情：楼层 + 图片 + 楼层回复 + 评分（v0.3.8 起为流水式阅读：后续页自动追加，不翻页） */
@Composable
fun ThreadDetailScreen(
    tid: Int,
    fallbackTitle: String,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    onOpenUser: (Int, String) -> Unit = { _, _ -> },
    onOpenPage: (String, String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    // v0.3.8 流水式阅读：整帖一个列表，用于回帖后滚到最新楼层
    val detailListState = rememberLazyListState()
    val repo = remember { HttpForumRepository() }
    var state by remember { mutableStateOf<DetailState>(DetailState.Loading) }
    var replyText by remember { mutableStateOf(TextFieldValue("")) }
    var replyTo by remember { mutableStateOf<Post?>(null) }
    var sending by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    // v0.3.9：屏内提示 2.6s 后自动消失（旧实现 toast 永不清空 → 小弹窗一直挂在界面上）
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2600)
            toast = null
        }
    }
    var rateForm by remember { mutableStateOf<RateClient.Form?>(null) }
    var ratePid by remember { mutableStateOf<Int?>(null) }
    /** 当前弹窗是「赞赏楼主」(true) 还是「评分某楼」(false)，只用于成功提示文案 */
    var rateIsReward by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var pollSubmitting by remember { mutableStateOf(false) }

    // v0.3.6：回帖输入框支持表情 + @ 提及
    var showSmiley by remember { mutableStateOf(false) }
    var smileySets by remember { mutableStateOf(emptyList<SmileyClient.SmileySet>()) }
    var smileySetIndex by remember { mutableIntStateOf(0) }
    var showMention by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    /**
     * v0.3.8 流水式阅读：第 2 页起把楼层追加到已有列表（不再翻页），
     * 页内楼层号由解析层按 `page` 计算，所以追加后序号连续。
     *
     * v0.3.9 弱网：[keepOnError] = true 时（当前已有内容）失败不清空界面，
     * 只刷新失败的提示，避免弱网「刷新一次整帖变空白」。
     */
    suspend fun load(targetPage: Int = page, keepOnError: Boolean = false) {
        runCatching { repo.loadThreadDetail(tid, targetPage) }
            .onSuccess { d ->
                cn.mce.starlight.data.FormhashStore.seed(d.formhash)
                val existing = (state as? DetailState.Content)?.detail
                val merged = if (existing != null && targetPage > 1) {
                    d.copy(posts = existing.posts + d.posts)
                } else {
                    d
                }
                state = if (merged.posts.isEmpty()) DetailState.Error("没有解析到楼层内容")
                else DetailState.Content(merged)
            }
            .onFailure {
                val existing = (state as? DetailState.Content)?.detail
                if (keepOnError && existing != null) {
                    toast = (it.message ?: "刷新失败") + "（已保留上次内容）"
                } else {
                    state = DetailState.Error(it.message ?: "加载失败")
                }
            }
    }

    /** 依次把后续页全部拉下来追加（流水式阅读）；failed 时保留已有内容 */
    suspend fun loadRemainingPages() {
        var guard = 0
        while (guard < 40) {
            guard++
            val cur = (state as? DetailState.Content)?.detail ?: return
            if (!cur.hasNextPage) return
            val nextPage = page + 1
            val before = cur.posts.size
            runCatching { repo.loadThreadDetail(tid, nextPage) }
                .onSuccess { d ->
                    if (d.posts.isNotEmpty()) {
                        page = nextPage
                        state = DetailState.Content(d.copy(posts = cur.posts + d.posts))
                    } else {
                        // 没有更多楼层：把当前页标记为最后一页即可
                        state = DetailState.Content(cur.copy(hasNextPage = false))
                        return
                    }
                }
                .onFailure { return }
            val after = (state as? DetailState.Content)?.detail?.posts?.size ?: return
            if (after <= before) return
        }
    }

    // v0.3.7：首屏只发一次请求（旧实现 LaunchedEffect(tid) 与 LaunchedEffect(tid, reloadKey) 同时跑，
    // 进帖子必然连发 2 次整页 GET，加载态被置两次，既慢又闪）
    LaunchedEffect(tid, reloadKey) {
        // v0.3.9 弱网：已有内容时重新进入/刷新不再先切成整屏转圈，失败也能保留旧内容
        val hadContent = state is DetailState.Content
        if (!hadContent) state = DetailState.Loading
        // v0.3.8：恢复上次浏览页（默认第 1 页）——回帖会跳到最后一页，退出再进仍停在那一页，
        // 避免「自己刚发的图/回复在最后一页，重新进来又回第 1 页以为没发出去」
        page = 1
        load(1, keepOnError = hadContent)
        // 流水式阅读：进来后自动把后续页追加到同一列表
        loadRemainingPages()
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val detail = (state as? DetailState.Content)?.detail
    val title = detail?.title ?: fallbackTitle

    // 表情集（SmileyClient 内部有进程级缓存，重复调用不会再走网络）
    LaunchedEffect(showSmiley) {
        if (showSmiley && smileySets.isEmpty()) {
            smileySets = SmileyClient.load(detail?.fid ?: Boards.default.fid)
        }
    }

    // @ 提及候选：当前页楼层作者（按 uid / 昵称去重，最多 50 人）
    val mentionAuthors = remember(detail) {
        detail?.posts
            ?.filter { it.author.isNotBlank() }
            ?.distinctBy { it.uid ?: it.author }
            ?.take(50)
            .orEmpty()
    }

    // v0.3.8：回帖附图（本地 uri + 服务端 aid）；上传成功后随回复一起提交
    val replyImages = remember { androidx.compose.runtime.mutableStateListOf<Pair<Uri, String>>() }
    var uploadingImage by remember { mutableStateOf(false) }
    val pickContext = androidx.compose.ui.platform.LocalContext.current
    val pickReplyImages = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val uploadFid = detail?.fid ?: return@rememberLauncherForActivityResult
        scope.launch {
            uploadingImage = true
            uris.forEach { uri ->
                runCatching {
                    val bytes = pickContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching
                    val name = "reply_${System.currentTimeMillis()}.jpg"
                    val aid = PostActions.uploadImage(uploadFid, tid, bytes, name)
                    if (aid != null) replyImages.add(uri to aid)
                }
            }
            uploadingImage = false
            toast = if (replyImages.isEmpty()) {
                "图片上传失败：" + (PostActions.lastUploadError?.take(80)?.replace("\n", " ") ?: "未知原因")
            } else {
                "已上传 ${replyImages.size} 张图片"
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            is DetailState.Loading -> DetailSkeleton(topPadding = topInset + 56.dp)

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
                state = detailListState,
                contentPadding = PaddingValues(top = topInset + 56.dp, bottom = 148.dp),
            ) {
                item {
                    Text(
                        text = s.detail.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
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
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = {
                            val opPid = s.detail.posts.firstOrNull()?.pid
                            if (opPid != null) {
                                scope.launch {
                                    val r = RateClient.loadForm(tid, opPid)
                                    val f = r.form
                                    if (f == null) {
                                        toast = r.message ?: "赞赏表单加载失败（可能未登录）"
                                    } else {
                                        rateIsReward = true
                                        ratePid = opPid
                                        rateForm = f
                                    }
                                }
                            }
                        }) { Text("赞赏") }

                        TextButton(onClick = {
                            scope.launch {
                                val r = PostActions.favorite(tid)
                                toast = r.message
                            }
                        }) { Text("收藏") }

                        Text(
                            "共 ${s.detail.posts.size} 楼 · 流水阅读",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
                // 投票贴：直接在帖子内参与投票（v0.3.3）
                s.detail.poll?.let { poll ->
                    item {
                        PollCard(
                            poll = poll,
                            submitting = pollSubmitting,
                            onSubmit = { ids ->
                                pollSubmitting = true
                                scope.launch {
                                    val r = PostActions.votePoll(
                                        action = poll.action,
                                        formhash = poll.formhash.ifBlank { s.detail.formhash.orEmpty() },
                                        optionIds = ids,
                                    )
                                    pollSubmitting = false
                                    toast = r.message
                                    if (r.ok) {
                                        val fresh = runCatching {
                                            repo.loadThreadDetail(tid, page, noCache = true)
                                        }.getOrNull()
                                        if (fresh != null) state = DetailState.Content(fresh)
                                    }
                                }
                            },
                            onOpenVoters = {
                                poll.viewVoteUrl?.let { onOpenPage(it, "投票参与人") }
                            },
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
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
                                val r = RateClient.loadForm(tid, post.pid)
                                val f = r.form
                                if (f == null) {
                                    toast = r.message ?: "评分表单加载失败（可能未登录）"
                                } else {
                                    rateIsReward = false
                                    ratePid = post.pid
                                    rateForm = f
                                }
                            }
                        },
                    )
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        ImmersiveTopBar(title = title, modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)

        detail?.let { d ->
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = {
                            Text(replyTo?.let { "回复 ${it.author}" } ?: "说点什么…")
                        },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = !sending && (replyText.text.isNotBlank() || replyImages.isNotEmpty()) && d.fid != null,
                        onClick = {
                            val fid = d.fid ?: return@TextButton
                            sending = true
                            val aids = replyImages.map { it.second }
                            // v0.3.8：图片用 [attach]aid[/attach] 内嵌到正文（只传 attachnew 会堆在楼末）
                            val message = buildString {
                                val body = replyText.text.trim()
                                if (body.isNotEmpty()) append(body)
                                aids.forEach { aid ->
                                    if (isNotEmpty()) append('\n')
                                    append("[attach]").append(aid).append("[/attach]")
                                }
                            }
                            scope.launch {
                                val r = PostActions.reply(tid, fid, message, replyTo?.pid, aids = aids)
                                sending = false
                                toast = r.message
                                if (r.ok) {
                                    replyText = TextFieldValue("")
                                    replyTo = null
                                    replyImages.clear()
                                    // v0.3.8 流水式阅读：回帖成功后从第 1 页重新聚合所有楼层，
                                    // 新回复会自动出现在列表末尾，并滚到底部。
                                    page = 1
                                    load(1)
                                    loadRemainingPages()
                                    kotlinx.coroutines.delay(150)
                                    val total = (state as? DetailState.Content)?.detail?.posts?.size ?: 0
                                    if (total > 0) {
                                        detailListState.requestScrollToItem(
                                            index = (total - 1).coerceAtLeast(0),
                                        )
                                    }
                                }
                            }
                        },
                    ) { Text(if (sending) "发送中" else "回复") }
                }
                // v0.3.8：已上传的回帖图片预览（点 × 移除）
                if (replyImages.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        replyImages.forEach { (uri, aid) ->
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                Text(
                                    "×",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clickable { replyImages.remove(uri to aid) }
                                        .padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
                // v0.3.6：表情 / @ 工具栏（先收键盘再弹窗，避免弹窗被 IME 挤压）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        keyboard?.hide()
                        showSmiley = true
                    }) { Text("表情") }
                    TextButton(onClick = {
                        keyboard?.hide()
                        showMention = true
                    }) { Text("@ 提及") }
                    TextButton(
                        enabled = !uploadingImage,
                        onClick = { pickReplyImages.launch("image/*") },
                    ) { Text(if (uploadingImage) "上传中…" else "图片") }
                    if (replyTo != null) {
                        TextButton(onClick = { replyTo = null }) { Text("取消楼层回复") }
                    }
                }
            }
        }

        toast?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 132.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    // 表情弹窗（与发帖页共用 SmileyPickerDialog）
    if (showSmiley) {
        SmileyPickerDialog(
            sets = smileySets,
            selectedIndex = smileySetIndex,
            onSelectSet = { smileySetIndex = it },
            onPick = { smiley ->
                replyText = replyText.insertAtCursor(smiley.code)
                showSmiley = false
            },
            onDismiss = { showSmiley = false },
        )
    }

    // @ 提及弹窗：从本页楼层作者里选人，插入 Discuz @ BBCode
    if (showMention) {
        AlertDialog(
            onDismissRequest = { showMention = false },
            title = { Text("选择要 @ 的人") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (mentionAuthors.isEmpty()) {
                        Text("本页还没有可 @ 的楼层作者", style = MaterialTheme.typography.bodySmall)
                    }
                    mentionAuthors.forEach { post ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    replyText = replyText.insertAtCursor(mentionBbcode(post.uid, post.author))
                                    showMention = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = post.author,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = post.floorLabel.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    replyText = replyText.insertAtCursor("@")
                    showMention = false
                }) { Text("只输入 @") }
            },
            dismissButton = {
                TextButton(onClick = { showMention = false }) { Text("取消") }
            },
        )
    }

    // 评分 / 赞赏弹窗
    val form = rateForm
    if (form != null) {
        RateDialog(
            form = form,
            onDismiss = { rateForm = null; ratePid = null },
            onSubmit = { score, reason ->
                rateForm = null
                scope.launch {
                    // v0.3.9：赞赏/评分的「赞赏人数」在提交前后用来兜底确认是否真的成功
                    // （Discuz 的 AJAX 回包不一定带「评分成功」标记，旧实现只弹一句 toast，
                    //  界面不刷新，用户根本不知道有没有生效）
                    val beforeReward = (state as? DetailState.Content)?.detail?.rewardCount ?: 0
                    val (ok, msg) = RateClient.submit(
                        form,
                        score,
                        reason,
                        successText = if (rateIsReward) "赞赏成功" else "评分成功",
                    )
                    toast = msg
                    // 不管回包判定结果如何，都重新聚合整帖：楼层里的赞赏状态 / 赞赏人数由服务端页面决定
                    page = 1
                    load(1, keepOnError = true)
                    loadRemainingPages()
                    if (!ok) {
                        val afterReward = (state as? DetailState.Content)?.detail?.rewardCount ?: 0
                        if (afterReward > beforeReward) toast = "赞赏成功（已刷新赞赏状态）"
                    }
                }
            },
        )
    }
}

/**
 * 帖子内投票卡片（v0.3.3 新增，v0.3.7 重做可读性）：
 *  - 标题区显示「单选/多选 · 参与人数 · 累计票数」
 *  - 每个选项：选中图标 + 选项文字 + 结果条 + 票数/百分比（未投票时按服务端是否公开结果显示）
 *  - 已投票 / 投票已结束分别提示，不再用「禁用的单选框 + 一行 N 票」糊在一起
 */
@Composable
private fun PollCard(
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
private fun PollBar(fraction: Float, highlight: Boolean) {
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        // v0.3.8 楼层头部：头像 40dp、楼层徽标 + 时间（单行省略），评分/回复移到楼层底部
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
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
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpenUser)) {
                Text(
                    post.author,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    post.dateText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // v0.3.9：楼层号统一右对齐（1楼/2楼…，不再显示「楼主」）
                    Spacer(Modifier.weight(1f))
                    post.floorLabel?.let { floor ->
                        Text(
                            floor,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // v0.3.8 正文：块间留白、正文行距加宽、彩色文字/引用块还原
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            post.blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Text -> PostTextBlock(block)
                    is ContentBlock.Image -> if (block.url.contains("/smiley/")) {
                        // 正文表情（小红龙等）：按原尺寸小图显示，不要拉满整行
                        AsyncImage(
                            model = block.url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(30.dp),
                        )
                    } else AsyncImage(
                        model = block.url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
        // 操作按钮放楼层底部右侧，头部只保留「谁 / 何时 / 几楼」
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!post.isOp) {
                TextButton(onClick = onRate) { Text("评分", style = MaterialTheme.typography.labelSmall) }
            }
            TextButton(onClick = onReply) { Text("回复", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/**
 * 正文文字块（v0.3.8）：
 * - 彩色文字 / 加粗 / 斜体 / 下划线 / 删除线 / `<font size>` 通过 AnnotatedString 还原；
 * - 行距比默认 bodyMedium 略宽（中文长帖更好读）；
 * - 引用块（kind = Quote）用左侧竖条 + 灰色小字区分。
 * `remember(block)` 避免每次重组都重建 AnnotatedString。
 */
@Composable
private fun PostTextBlock(block: ContentBlock.Text) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val quotedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(block) {
        buildAnnotatedString {
            append(block.text)
            block.spans.forEach { span ->
                val start = span.start.coerceIn(0, block.text.length)
                val end = span.end.coerceIn(start, block.text.length)
                if (end <= start) return@forEach
                val color = span.color?.let { Color(it) }
                addStyle(
                    SpanStyle(
                        color = color ?: (if (block.kind == TextKind.Quote) quotedColor else baseColor),
                        fontWeight = if (span.bold) FontWeight.Bold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                        textDecoration = when {
                            span.underline && span.strike -> TextDecoration.combine(
                                listOf(TextDecoration.Underline, TextDecoration.LineThrough),
                            )
                            span.underline -> TextDecoration.Underline
                            span.strike -> TextDecoration.LineThrough
                            else -> null
                        },
                        fontSize = span.sizeEm?.let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Em) } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                    ),
                    start,
                    end,
                )
            }
        }
    }
    val textStyle = if (block.kind == TextKind.Quote) {
        MaterialTheme.typography.bodySmall.copy(lineHeight = 21.sp)
    } else {
        MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp, fontSize = 15.sp)
    }
    if (block.kind == TextKind.Quote) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(3.dp)
                    .heightIn(min = 18.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = annotated,
                style = textStyle,
                color = quotedColor,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Text(text = annotated, style = textStyle, color = baseColor)
    }
}
