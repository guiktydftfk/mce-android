package cn.mce.starlight.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.Draft
import cn.mce.starlight.core.DraftStore
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.PostActions
import cn.mce.starlight.data.SmileyClient
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Compose page (native), covering the web compose essentials:
 *  board + topic category + title + BBCode content
 *  tabs: 发表帖子 / 发起投票 (v0.3.2 新增投票)
 *  poll: 选项（每行一个）/ 最多可选 / 记票天数 / 投票后结果可见 / 公开投票参与人
 *  image upload (multi) via misc.php?mod=swfupload
 *  extra options (hidden replies / order / notify / signature / disable smiley / no-parse url)
 *  reply credit, draft saving
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewThreadScreen(
    onMenu: () -> Unit,
    initialFid: Int? = null,
    initialDraft: Draft? = null,
    onDraftSaved: () -> Unit = {},
    onPosted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageScroll = rememberScrollState()

    // 发帖默认板块固定为「综合交流」；从板块页进入时用传入的 fid
    var selectedFid by remember { mutableStateOf(initialFid ?: Boards.default.fid) }
    var expandedBoardGroups by remember { mutableStateOf(setOf("全国交流")) }
    var boardExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    // 页签：帖子 / 投票
    var pollMode by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf(emptyList<PostActions.Category>()) }
    var selectedCategory by remember { mutableStateOf<PostActions.Category?>(null) }
    var loadingForm by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var formError by remember { mutableStateOf<String?>(null) }

    var subject by remember { mutableStateOf("") }
    var contentField by remember { mutableStateOf(TextFieldValue("")) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 投票字段
    var pollOptions by remember { mutableStateOf("") }
    var maxChoices by remember { mutableStateOf("") }
    var expireDays by remember { mutableStateOf("") }
    var resultVisible by remember { mutableStateOf(false) }
    var publicVoters by remember { mutableStateOf(false) }

    // extra options
    var hiddenReplies by remember { mutableStateOf(false) }
    var orderType by remember { mutableStateOf(false) }
    var noticeAuthor by remember { mutableStateOf(true) }
    var useSign by remember { mutableStateOf(true) }
    var disableSmiley by remember { mutableStateOf(false) }
    var parseUrlOff by remember { mutableStateOf(false) }
    var creditTimes by remember { mutableStateOf("") }
    var creditValue by remember { mutableStateOf("") }
    var showOptions by remember { mutableStateOf(false) }
    // 草稿 id：从草稿箱进入时带上，发布成功或重新保存时复用
    var draftId by remember { mutableStateOf(initialDraft?.id) }
    var draftTip by remember { mutableStateOf<String?>(null) }

    // uploaded attachments (local uri + remote aid)
    val uploaded = remember { mutableStateListOf<Pair<Uri, String>>() }
    var uploading by remember { mutableStateOf(false) }

    val fid = selectedFid
    val pollChoices = pollOptions.split('\n').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    // 表情：完整表情集（含小红龙 reddragon 全套）
    var showSmiley by remember { mutableStateOf(false) }
    var smileySets by remember { mutableStateOf(emptyList<SmileyClient.SmileySet>()) }
    var smileySetIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(showSmiley) {
        if (showSmiley && smileySets.isEmpty()) smileySets = SmileyClient.load(fid)
    }

    // 从草稿箱恢复内容（只在首次组合时执行；之后用户可自由编辑）
    LaunchedEffect(initialDraft?.id) {
        val d = initialDraft ?: return@LaunchedEffect
        selectedFid = d.fid
        subject = d.title
        contentField = TextFieldValue(d.content)
        pollMode = d.pollMode
        pollOptions = d.pollOptions
        maxChoices = d.maxChoices
        expireDays = d.expireDays
        resultVisible = d.resultVisible
        publicVoters = d.publicVoters
    }

    LaunchedEffect(selectedFid, SessionStore.isLoggedIn, reloadTick, pollMode) {
        loadingForm = true
        formError = null
        categories = emptyList()
        selectedCategory = null
        val form = PostActions.loadComposeForm(fid, poll = pollMode)
        loadingForm = false
        if (form == null) {
            formError = if (pollMode) "无法加载投票表单（可能未登录 / 该板块不允许投票）" else "无法加载发帖表单（可能未登录）"
        } else {
            categories = form.categories
            form.formhash?.let { cn.mce.starlight.data.FormhashStore.seed(it) }
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            uris.forEach { uri ->
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching
                    val name = "image_${System.currentTimeMillis()}.jpg"
                    val aid = PostActions.uploadImage(fid, bytes, name)
                    if (aid != null) uploaded.add(uri to aid)
                }
            }
            uploading = false
        }
    }

    // 在光标处插入 BBCode（实现见 ui/TextEditing.kt，与回帖框共用）
    fun wrap(open: String, close: String, placeholder: String) {
        contentField = contentField.wrapSelection(open, close, placeholder)
    }

    fun insertText(text: String) {
        contentField = contentField.insertAtCursor(text)
    }

    /** 存草稿：只存本地（Discuz 手机模板没有草稿箱），不会发布 */
    fun saveDraft() {
        if (subject.isBlank() && contentField.text.isBlank()) {
            error = "标题和内容不能都为空"
            return
        }
        val id = draftId ?: System.currentTimeMillis()
        DraftStore.save(
            Draft(
                id = id,
                fid = fid,
                fidName = Boards.byFid(fid)?.name.orEmpty(),
                title = subject.trim(),
                content = contentField.text,
                pollMode = pollMode,
                pollOptions = pollOptions,
                maxChoices = maxChoices,
                expireDays = expireDays,
                resultVisible = resultVisible,
                publicVoters = publicVoters,
                savedAt = System.currentTimeMillis(),
            ),
        )
        draftId = id
        error = null
        draftTip = "已保存到草稿箱（抽屉 → 草稿箱 可找到）"
        onDraftSaved()
    }

    suspend fun submit(draft: Boolean) {
        sending = true
        error = null
        val poll = if (pollMode) {
            PostActions.Poll(
                choices = pollChoices,
                maxChoices = maxChoices.trim().toIntOrNull(),
                expireDays = expireDays.trim().toIntOrNull(),
                resultVisible = resultVisible,
                publicVoters = publicVoters,
            )
        } else {
            null
        }
        val r = PostActions.newThread(
            fid = fid,
            subject = subject.trim(),
            message = contentField.text.trim(),
            typeid = selectedCategory?.id,
            aids = uploaded.map { it.second },
            options = PostActions.Options(
                hiddenReplies = hiddenReplies,
                orderType = orderType,
                noticeAuthor = noticeAuthor,
                useSign = useSign,
                disableSmiley = disableSmiley,
                parseUrlOff = parseUrlOff,
                replyCreditTimes = creditTimes,
                replyCreditValue = creditValue,
                draft = draft,
            ),
            poll = poll,
        )
        sending = false
        if (r.ok) {
            draftId?.let { DraftStore.delete(it) }
            onPosted()
        } else error = r.message ?: "发布失败"
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val canSubmit = !sending && subject.isNotBlank() && contentField.text.isNotBlank() &&
        (categories.isEmpty() || selectedCategory != null) &&
        (!pollMode || pollChoices.size >= 2)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(pageScroll)
            .padding(top = topInset + 64.dp, start = 14.dp, end = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 页签：发表帖子 / 发起投票
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ModeTab("\u53d1\u8868\u5e16\u5b50", !pollMode) { pollMode = false }
            ModeTab("\u53d1\u8d77\u6295\u7968", pollMode) { pollMode = true }
        }
        HorizontalDivider()

        // board selector
        ExposedDropdownMenuBox(expanded = boardExpanded, onExpandedChange = { boardExpanded = !boardExpanded }) {
            OutlinedTextField(
                value = Boards.byFid(selectedFid)?.let { "${it.group} · ${it.name}" } ?: "选择板块",
                onValueChange = {},
                readOnly = true,
                label = { Text("板块") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = boardExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = boardExpanded, onDismissRequest = { boardExpanded = false }) {
                // 与侧边栏同款：分组折叠
                Boards.groups.forEach { (groupName, groupBoards) ->
                    val expanded = groupName in expandedBoardGroups
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (expanded) "\u25be " else "\u25b8 ") + groupName,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {
                            expandedBoardGroups = if (expanded) expandedBoardGroups - groupName
                            else expandedBoardGroups + groupName
                        },
                    )
                    if (expanded) {
                        groupBoards.forEach { board ->
                            DropdownMenuItem(
                                text = { Text("    " + board.name) },
                                onClick = {
                                    selectedFid = board.fid
                                    boardExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // topic category
        if (categories.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded },
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "选择主题分类（必选）",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("主题分类") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = { selectedCategory = category; categoryExpanded = false },
                        )
                    }
                }
            }
        } else if (loadingForm) {
            Text("正在加载表单…", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // BBCode toolbar
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolButton("B") { wrap("[b]", "[/b]", "加粗文字") }
            ToolButton("I") { wrap("[i]", "[/i]", "斜体文字") }
            ToolButton("U") { wrap("[u]", "[/u]", "下划线文字") }
            ToolButton("颜色") { wrap("[color=#ff0000]", "[/color]", "彩色文字") }
            ToolButton("链接") { wrap("[url]", "[/url]", "https://") }
            ToolButton("图片") { wrap("[img]", "[/img]", "图片链接") }
            ToolButton("引用") { wrap("[quote]", "[/quote]", "引用内容") }
            ToolButton("代码") { wrap("[code]", "[/code]", "代码") }
            ToolButton("隐藏") { wrap("[hide]", "[/hide]", "隐藏内容（回复可见）") }
            ToolButton("@") { insertText("@") }
            ToolButton("\u8868\u60c5") { showSmiley = true }
        }

        OutlinedTextField(
            value = contentField,
            onValueChange = { contentField = it },
            label = { Text("内容（支持 BBCode）") },
            minLines = if (pollMode) 4 else 8,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---------- 投票设置 ----------
        if (pollMode) {
            OutlinedTextField(
                value = pollOptions,
                onValueChange = { pollOptions = it },
                label = { Text("投票选项（每行 1 个，2~${PostActions.Poll.MAX_CHOICES} 项）") },
                placeholder = { Text("选项一\n选项二\n选项三") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (pollChoices.isNotEmpty()) {
                Text(
                    "已填写 ${pollChoices.size} 个选项" +
                        if (pollChoices.size < 2) "（至少 2 个才能发布）" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pollChoices.size < 2) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = maxChoices,
                    onValueChange = { maxChoices = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("最多可选") },
                    placeholder = { Text("1") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = expireDays,
                    onValueChange = { expireDays = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("记票天数") },
                    placeholder = { Text("不限") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OptionRow("投票后结果可见", resultVisible) { resultVisible = it }
            OptionRow("公开投票参与人", publicVoters) { publicVoters = it }
            HorizontalDivider()
        }

        // image upload row
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = !uploading,
                onClick = { pickImages.launch("image/*") },
            ) { Text(if (uploading) "上传中…" else "＋ 添加图片") }
            if (uploaded.isNotEmpty()) {
                Text(
                    "已上传 ${uploaded.size} 张",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (uploaded.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                uploaded.forEach { (uri, _) ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                        )
                        Text(
                            "×",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable { uploaded.remove(uri to uploaded.first { it.first == uri }.second) }
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }

        // options toggle
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showOptions = !showOptions },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "附加选项" + if (showOptions) " ▲" else " ▼",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (showOptions) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OptionRow("回帖仅作者可见", hiddenReplies) { hiddenReplies = it }
                OptionRow("回帖倒序排列", orderType) { orderType = it }
                OptionRow("接收回复通知", noticeAuthor) { noticeAuthor = it }
                OptionRow("使用个人签名", useSign) { useSign = it }
                OptionRow("禁用表情", disableSmiley) { disableSmiley = it }
                OptionRow("禁用链接识别", parseUrlOff) { parseUrlOff = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("回帖奖励", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = creditTimes,
                        onValueChange = { creditTimes = it },
                        label = { Text("次数") },
                        singleLine = true,
                        modifier = Modifier.size(width = 90.dp, height = 56.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    OutlinedTextField(
                        value = creditValue,
                        onValueChange = { creditValue = it },
                        label = { Text("每次值") },
                        singleLine = true,
                        modifier = Modifier.size(width = 90.dp, height = 56.dp),
                    )
                }
            }
        }

        (formError ?: error)?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { reloadTick += 1 }) { Text("重试") }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = canSubmit,
                onClick = { scope.launch { submit(false) } },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when {
                        sending -> "提交中…"
                        pollMode -> "发布投票"
                        else -> "发布"
                    },
                )
            }

            TextButton(
                enabled = !sending && (subject.isNotBlank() || contentField.text.isNotBlank()),
                onClick = { saveDraft() },
            ) { Text(if (draftId != null) "更新草稿" else "存草稿") }
        }

        draftTip?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))
    }

    if (showSmiley) {
        SmileyPickerDialog(
            sets = smileySets,
            selectedIndex = smileySetIndex,
            onSelectSet = { smileySetIndex = it },
            onPick = { smile ->
                contentField = contentField.insertAtCursor(smile.code)
                showSmiley = false
            },
            onDismiss = { showSmiley = false },
        )
    }
    ImmersiveTopBar(title = if (pollMode) "发起投票" else "发帖", onMenu = onMenu)
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Box(
            Modifier
                .width(selectedWidth(selected))
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}

private fun selectedWidth(selected: Boolean) = if (selected) 28.dp else 0.dp

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
