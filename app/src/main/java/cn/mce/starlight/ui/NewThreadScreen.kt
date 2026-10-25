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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.PostActions
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Compose page (native), covering the web compose essentials:
 *  board + topic category + title + BBCode content
 *  image upload (multi) via misc.php?mod=swfupload
 *  extra options (hidden replies / order / notify / signature / disable smiley / no-parse url)
 *  reply credit, draft saving
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewThreadScreen(
    onMenu: () -> Unit,
    initialFid: Int? = null,
    onPosted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val boards = Boards.all

    var boardIndex by remember {
        mutableStateOf(boards.indexOfFirst { it.fid == initialFid }.coerceAtLeast(0))
    }
    var boardExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf(emptyList<PostActions.Category>()) }
    var selectedCategory by remember { mutableStateOf<PostActions.Category?>(null) }
    var loadingForm by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    var subject by remember { mutableStateOf("") }
    var contentField by remember { mutableStateOf(TextFieldValue("")) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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

    // uploaded attachments (local uri + remote aid)
    val uploaded = remember { mutableStateListOf<Pair<Uri, String>>() }
    var uploading by remember { mutableStateOf(false) }

    val fid = boards[boardIndex].fid

    LaunchedEffect(boardIndex) {
        loadingForm = true
        formError = null
        categories = emptyList()
        selectedCategory = null
        val form = PostActions.loadComposeForm(fid)
        loadingForm = false
        if (form == null) formError = "无法加载发帖表单（可能未登录）" else categories = form.categories
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

    // 在光标处插入 BBCode（有选区则包裹选区，否则插入占位文本并把光标放到标签中间）
    fun wrap(open: String, close: String, placeholder: String) {
        val tv = contentField
        val start = tv.selection.min
        val end = tv.selection.max
        val selected = tv.text.substring(start, end).ifBlank { placeholder }
        val newText = tv.text.replaceRange(start, end, open + selected + close)
        val cursor = start + open.length + selected.length
        contentField = TextFieldValue(newText, TextRange(cursor))
    }

    fun insertText(text: String) {
        val tv = contentField
        val start = tv.selection.min
        val newText = tv.text.replaceRange(start, tv.selection.max, text)
        contentField = TextFieldValue(newText, TextRange(start + text.length))
    }

    suspend fun submit(draft: Boolean) {
        sending = true
        error = null
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
        )
        sending = false
        if (r.ok) onPosted() else error = r.message ?: "发布失败"
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topInset + 64.dp, start = 14.dp, end = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // board selector
        ExposedDropdownMenuBox(expanded = boardExpanded, onExpandedChange = { boardExpanded = !boardExpanded }) {
            OutlinedTextField(
                value = boards[boardIndex].name,
                onValueChange = {},
                readOnly = true,
                label = { Text("板块") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = boardExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = boardExpanded, onDismissRequest = { boardExpanded = false }) {
                boards.forEachIndexed { index, board ->
                    DropdownMenuItem(
                        text = { Text("${board.name}（${board.group}）") },
                        onClick = { boardIndex = index; boardExpanded = false },
                    )
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
                    value = selectedCategory?.name ?: "选择主题分类（可留空）",
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
            Text("正在加载发帖表单…", style = MaterialTheme.typography.bodySmall)
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
        }

        OutlinedTextField(
            value = contentField,
            onValueChange = { contentField = it },
            label = { Text("内容（支持 BBCode）") },
            minLines = 8,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

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
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !sending && subject.isNotBlank() && contentField.text.isNotBlank(),
                onClick = { scope.launch { submit(false) } },
                modifier = Modifier.weight(1f),
            ) { Text(if (sending) "提交中…" else "发布") }

            TextButton(
                enabled = !sending && contentField.text.isNotBlank(),
                onClick = { scope.launch { submit(true) } },
            ) { Text("存草稿") }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }

    ImmersiveTopBar(title = "发帖", onMenu = onMenu)
}

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
