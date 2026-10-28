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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
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
    /** v0.3.10：退出编辑页（存草稿 / 放弃修改后由 MceApp 出栈） */
    onExit: () -> Unit = {},
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
    // v0.3.10：颜色选择器 / 退出提醒
    var showColorPicker by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    // v0.4.0：独立的发帖预览页（非实时）
    var showPreview by remember { mutableStateOf(false) }
    // v0.4.0：选区跟踪（工具栏/弹窗失焦后仍能作用于原选区）+ 视频插入
    var lastSelection by remember { mutableStateOf(androidx.compose.ui.text.TextRange(0, 0)) }
    var lastSelectionTextLen by remember { mutableStateOf(-1) }
    var pendingColorSelection by remember { mutableStateOf<androidx.compose.ui.text.TextRange?>(null) }
    var showVideoDialog by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf("") }

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
            var inserted = 0
            uris.forEach { uri ->
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching
                    val name = "image_${System.currentTimeMillis()}.jpg"
                    val aid = PostActions.uploadImage(fid, null, bytes, name)
                    if (aid != null) {
                        uploaded.add(uri to aid)
                        // v0.3.10：类「小黑盒」图文 —— 选完图就在光标处插入，图片跟着文字段落走，
                        // 不再像旧版那样先攒着、等发布时统一堆到正文末尾
                        contentField = contentField.insertAtCursor("\n[attach]$aid[/attach]\n")
                        inserted++
                    }
                }
            }
            uploading = false
            draftTip = if (inserted > 0) {
                "已插入 $inserted 张图片（光标处）"
            } else {
                "图片上传失败：" + (PostActions.lastUploadError?.take(80)?.replace("\n", " ") ?: "未知原因")
            }
        }
    }

    /** 删除一张已插入的图片（同时把正文里的 [attach] 标记去掉） */
    fun removeUploaded(item: Pair<Uri, String>) {
        uploaded.remove(item)
        val marker = "[attach]${item.second}[/attach]"
        contentField = TextFieldValue(
            text = contentField.text.replace(marker, "").replace(Regex("""\n{3,}"""), "\n\n"),
        )
    }

    val hasDraftContent = subject.isNotBlank() || contentField.text.isNotBlank() || uploaded.isNotEmpty()

    // v0.3.10：直接退出编辑页时先提醒存草稿（有内容才拦）
    androidx.activity.compose.BackHandler(enabled = hasDraftContent && !sending) {
        showExitConfirm = true
    }

    // 在光标处插入 BBCode（实现见 ui/TextEditing.kt，与回帖框共用）
    fun wrap(open: String, close: String, placeholder: String) {
        contentField = contentField.wrapSelection(open, close, placeholder)
    }

    fun insertText(text: String) {
        contentField = contentField.insertAtCursor(text)
    }

    /**
     * 取「有效选区」：优先当前选区；如果因为点工具栏/弹窗失焦被输入法清掉，
     * 就用最近一次非空选区（v0.4.0：修「全选文字后点颜色改不了颜色」）。
     */
    fun effectiveSelection(): TextRange? {
        val cur = contentField.selection
        if (cur.min != cur.max && contentField.text.substring(cur.min, cur.max).isNotBlank()) return cur
        val last = lastSelection
        // 只有「文本长度未变」时才信任记住的选区，避免用户改过正文后把标签包错位置
        if (lastSelectionTextLen == contentField.text.length &&
            last.min != last.max && last.max <= contentField.text.length &&
            contentField.text.substring(last.min, last.max).isNotBlank()
        ) {
            return last
        }
        return null
    }

    /**
     * v0.4.0：B/I/U 只对选中文字生效；没选中就只给提示，
     * 不再往内容框里塞 `[b]加粗文字[/b]` 这类占位文字。
     */
    fun styleSelection(open: String, close: String, label: String) {
        val sel = effectiveSelection()
        if (sel == null) {
            draftTip = "请先选中要${label}的文字，再点「${label}」"
            return
        }
        contentField = contentField.wrapRange(sel.min, sel.max, open, close)
        lastSelection = contentField.selection
    }

    /** 打开颜色弹窗时记住选区（弹窗打开后输入框会失焦，选区可能被清掉） */
    fun openColorPicker() {
        val sel = effectiveSelection()
        if (sel == null) {
            draftTip = "请先选中要变色的文字，再点「颜色」"
            return
        }
        pendingColorSelection = sel
        showColorPicker = true
    }

    /** 用打开弹窗时记住的选区套用颜色 —— 即使输入框已失焦也不会丢 */
    fun applyColor(hex: String) {
        val sel = pendingColorSelection ?: effectiveSelection()
        showColorPicker = false
        pendingColorSelection = null
        if (sel == null) {
            draftTip = "请先选中要变色的文字，再点「颜色」"
            return
        }
        contentField = contentField.wrapRange(sel.min, sel.max, "[color=$hex]", "[/color]")
        lastSelection = contentField.selection
    }

    /** 对齐：有选中就包住选区，没选中就包住光标所在整行（v0.4.0） */
    fun applyAlign(direction: String) {
        val label = when (direction) {
            "left" -> "左对齐"
            "center" -> "居中"
            "right" -> "右对齐"
            else -> "对齐"
        }
        val sel = effectiveSelection()
        val (start, end) = if (sel != null) {
            sel.min to sel.max
        } else {
            val text = contentField.text
            val cursor = contentField.selection.min.coerceIn(0, text.length)
            val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
            lineStart to lineEnd
        }
        val target = contentField.text.substring(start, end)
        if (target.isBlank()) {
            draftTip = "请先选中要${label}的内容"
            return
        }
        contentField = contentField.wrapRange(start, end, "[align=$direction]", "[/align]")
        lastSelection = contentField.selection
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
            ToolButton("B") { styleSelection("[b]", "[/b]", "加粗") }
            ToolButton("I") { styleSelection("[i]", "[/i]", "斜体") }
            ToolButton("U") { styleSelection("[u]", "[/u]", "下划线") }
            // v0.4.0：颜色只对选中文字生效（弹窗会记住选区），没选中只提示
            ToolButton("颜色") { openColorPicker() }
            // v0.4.0：对齐（有选中包选中，没选中包当前行）
            ToolButton("左") { applyAlign("left") }
            ToolButton("中") { applyAlign("center") }
            ToolButton("右") { applyAlign("right") }
            ToolButton("链接") { wrap("[url]", "[/url]", "https://") }
            ToolButton("图片") { pickImages.launch("image/*") }
            ToolButton("图链") { wrap("[img]", "[/img]", "图片链接") }
            // v0.4.0：插入视频（Discuz [media] BBCode）
            ToolButton("视频") { videoUrl = ""; showVideoDialog = true }
            ToolButton("引用") { wrap("[quote]", "[/quote]", "引用内容") }
            ToolButton("代码") { wrap("[code]", "[/code]", "代码") }
            ToolButton("隐藏") { wrap("[hide]", "[/hide]", "隐藏内容（回复可见）") }
            // v0.4.0：一键全选（配合颜色/加粗等只对选区生效的功能）
            ToolButton("全选") {
                val all = androidx.compose.ui.text.TextRange(0, contentField.text.length)
                contentField = contentField.copy(selection = all)
                lastSelection = all
            }
            ToolButton("@") { insertText("@") }
            ToolButton("\u8868\u60c5") { showSmiley = true }
        }

        OutlinedTextField(
            value = contentField,
            onValueChange = {
                contentField = it
                // 记录最近一次非空选区：弹窗/失焦把选区清掉时还能还原
                if (it.selection.min != it.selection.max) {
                    lastSelection = it.selection
                    lastSelectionTextLen = it.text.length
                }
            },
            label = { Text("内容（支持 BBCode；选中文字后可加粗/颜色/斜体/下划线）") },
            minLines = if (pollMode) 4 else 8,
            modifier = Modifier.fillMaxWidth(),
        )

        // v0.4.0：预览改为独立页面（点按钮才生成一次），编辑区不再实时预览
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = contentField.text.isNotBlank(),
                onClick = { showPreview = true },
            ) { Text("发帖预览") }
            Text(
                "点开后按发布后的样子查看排版/颜色/图片（不是实时预览）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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

        // v0.3.10：图片改为「选完即插入光标处」，不需要先上传再手动插入
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = !uploading,
                onClick = { pickImages.launch("image/*") },
            ) { Text(if (uploading) "插入中…" else "＋ 插入图片") }
            Text(
                "选图后自动插到光标处，可和文字混排",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uploaded.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                uploaded.forEach { item ->
                    Box {
                        AsyncImage(
                            model = item.first,
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
                                .clickable { removeUploaded(item) }
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
    if (showColorPicker) {
        ColorPickerDialog(
            onPick = { applyColor(it) },
            onDismiss = { showColorPicker = false; pendingColorSelection = null },
        )
    }

    if (showVideoDialog) {
        AlertDialog(
            onDismissRequest = { showVideoDialog = false },
            title = { Text("插入视频") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "填入视频页面地址（B 站 / 腾讯视频 / 优酷等），发布后由论坛播放器解析。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it },
                        label = { Text("视频地址") },
                        placeholder = { Text("https://www.bilibili.com/video/BV…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = videoUrl.trim()
                    if (url.isBlank()) {
                        draftTip = "视频地址不能为空"
                        return@TextButton
                    }
                    insertText("[media=x,500,375]$url[/media]")
                    showVideoDialog = false
                    draftTip = "已插入视频（发布后由论坛播放器展开）"
                }) { Text("插入") }
            },
            dismissButton = { TextButton(onClick = { showVideoDialog = false }) { Text("取消") } },
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("要存入草稿箱吗？") },
            text = { Text("退出后未发布的标题、正文和图片会丢失。可以先存到草稿箱，之后在「抽屉 → 草稿箱」里继续编辑。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    saveDraft()
                    onExit()
                }) { Text("存草稿并退出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitConfirm = false
                        onExit()
                    }) { Text("直接退出", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showExitConfirm = false }) { Text("取消") }
                }
            },
        )
    }

    if (showPreview) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPreview = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            DraftPreviewScreen(
                title = subject.trim(),
                content = contentField.text,
                images = uploaded.toList(),
                pollOptions = if (pollMode) pollChoices else emptyList(),
                onBack = { showPreview = false },
            )
        }
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

/** 移动版编辑器原有的 5 个预设色（`data-color`） */
private val SITE_COLORS = listOf("#222222", "#E64346", "#FF9501", "#AAC238", "#1A497D")

/**
 * 网页版论坛编辑器色板（v0.4.0 补充）：
 * 前 30 色按用户提供的网页编辑器色板截图逐像素取样，后 6 色为黑/灰/白与常用亮色。
 */
private val WEB_COLORS = listOf(
    "#70482E", "#505C36", "#18611A", "#3B345D", "#020356", "#3B065E",
    "#A96F26", "#767824", "#226D1B", "#227175", "#070487", "#5A616B",
    "#75481F", "#7D8D42", "#3B6D50", "#428985", "#233877", "#5E0D5C",
    "#AE802B", "#918F2B", "#54B331", "#35B5B4", "#2889A9", "#65307E",
    "#503D1C", "#3C3A13", "#30621D", "#255152", "#25434E", "#4F264E",
    "#000000", "#444444", "#888888", "#CCCCCC", "#FFFFFF", "#E64346",
)

/** 选颜色弹窗：选中文字后点「颜色」→ 直接点色块即可套用（36 色，来自网页版编辑器色板） */
@Composable
private fun ColorPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = (SITE_COLORS + WEB_COLORS).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文字颜色") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "先选中要变色的文字，再点色块（色板含网页版论坛编辑器的预设色）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                palette.chunked(6).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowColors.forEach { hex ->
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { onPick(hex) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
