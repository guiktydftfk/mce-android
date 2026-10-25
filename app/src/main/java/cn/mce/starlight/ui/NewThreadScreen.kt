package cn.mce.starlight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.PostActions
import kotlinx.coroutines.launch

/**
 * 发帖独立页面：
 * 板块选择 → 主题分类（从发帖页动态拉取）→ 标题 → 内容 → 发布
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewThreadScreen(
    onMenu: () -> Unit,
    initialFid: Int? = null,
    onPosted: () -> Unit,
) {
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
    var content by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 切换板块 → 重新拉取该板块的主题分类
    LaunchedEffect(boardIndex) {
        loadingForm = true
        formError = null
        categories = emptyList()
        selectedCategory = null
        val form = PostActions.loadComposeForm(boards[boardIndex].fid)
        loadingForm = false
        if (form == null) {
            formError = "无法加载发帖表单（可能未登录）"
        } else {
            categories = form.categories
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topInset + 64.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExposedDropdownMenuBox(expanded = boardExpanded, onExpandedChange = { boardExpanded = !boardExpanded }) {
            OutlinedTextField(
                value = boards[boardIndex].name,
                onValueChange = {},
                readOnly = true,
                label = { Text("板块") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = boardExpanded) },
                modifier = Modifier
                    .menuAnchor()
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

        if (categories.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded },
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "选择主题分类",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("主题分类") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
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

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("内容") },
            minLines = 10,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        (formError ?: error)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Button(
            enabled = !sending && subject.isNotBlank() && content.isNotBlank(),
            onClick = {
                sending = true
                error = null
                scope.launch {
                    val r = PostActions.newThread(
                        fid = boards[boardIndex].fid,
                        subject = subject.trim(),
                        message = content.trim(),
                        typeid = selectedCategory?.id,
                    )
                    sending = false
                    if (r.ok) onPosted() else error = r.message ?: "发布失败"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (sending) "发布中…" else "发布") }

        Spacer(Modifier.height(12.dp))
    }

    ImmersiveTopBar(title = "发帖", onMenu = onMenu)
}
