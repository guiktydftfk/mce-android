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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.SearchClient
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** 搜索：帖子 / 用户 + 高级搜索（作者、版块、时间、排序） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMenu: () -> Unit,
    onOpenThread: (Int, String) -> Unit,
    onOpenUser: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("thread") }   // thread | user
    var keyword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // 空结果用中性提示（旧实现塞进 error → 红色报错色，把"没搜到"显示成"出错了"）
    var notice by remember { mutableStateOf<String?>(null) }
    var threads by remember { mutableStateOf(emptyList<SearchClient.Hit>()) }
    var users by remember { mutableStateOf(emptyList<SearchClient.UserHit>()) }

    // advanced
    var showAdvanced by remember { mutableStateOf(false) }
    var author by remember { mutableStateOf("") }
    var boardIndex by remember { mutableStateOf(-1) }
    var boardExpanded by remember { mutableStateOf(false) }
    var daysIndex by remember { mutableStateOf(0) }
    var daysExpanded by remember { mutableStateOf(false) }
    var orderIndex by remember { mutableStateOf(0) }
    var orderExpanded by remember { mutableStateOf(false) }

    val dayOptions = listOf(null to "不限时间", 7 to "最近 7 天", 30 to "最近 30 天", 365 to "最近一年")
    val orderOptions = listOf("lastpost" to "按回复时间", "dateline" to "按发表时间", "replies" to "按回复数")

    fun doSearch() {
        loading = true
        error = null
        notice = null
        threads = emptyList()
        users = emptyList()
        scope.launch {
            runCatching {
                if (mode == "user") {
                    users = SearchClient.searchUsers(keyword.trim())
                    if (users.isEmpty()) notice = "没有找到相关用户"
                } else {
                    val adv = !showAdvanced && author.isBlank() && boardIndex < 0 && daysIndex == 0
                    threads = if (adv) {
                        SearchClient.searchThreads(keyword.trim())
                    } else {
                        SearchClient.advancedSearch(
                            SearchClient.Advanced(
                                keyword = keyword.trim(),
                                author = author.trim(),
                                fid = if (boardIndex >= 0) Boards.all[boardIndex].fid else null,
                                days = dayOptions[daysIndex].first,
                                orderBy = orderOptions[orderIndex].first,
                            ),
                        )
                    }
                    if (threads.isEmpty()) notice = "没有找到相关帖子"
                }
            }.onFailure {
                val raw = it.message ?: "搜索失败"
                error = if (mode == "user" && (raw.contains("503") || raw.contains("模板"))) {
                    "论坛用户搜索页暂时不可用（服务端模板缺失），已尝试兼容模式仍失败，请稍后再试或先搜帖子"
                } else {
                    raw
                }
            }
            loading = false
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = topInset + 60.dp),
        ) {
            // mode tabs
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("thread" to "搜帖子", "user" to "搜用户").forEach { (value, label) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (mode == value) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (mode == value) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable {
                                mode = value
                                threads = emptyList()
                                users = emptyList()
                                error = null
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (mode == "thread") {
                    Text(
                        text = if (showAdvanced) "收起高级" else "高级搜索",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { showAdvanced = !showAdvanced }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            // query + button
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text(if (mode == "user") "用户名/UID" else "关键词（匹配标题与正文）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(enabled = !loading, onClick = { doSearch() }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            }

            // advanced panel
            if (showAdvanced && mode == "thread") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("作者（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ExposedDropdownMenuBox(
                        expanded = boardExpanded,
                        onExpandedChange = { boardExpanded = !boardExpanded },
                    ) {
                        OutlinedTextField(
                            value = if (boardIndex >= 0) Boards.all[boardIndex].name else "全部版块",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("版块") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = boardExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = boardExpanded,
                            onDismissRequest = { boardExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部版块") },
                                onClick = { boardIndex = -1; boardExpanded = false },
                            )
                            Boards.all.forEachIndexed { index, board ->
                                DropdownMenuItem(
                                    text = { Text(board.name) },
                                    onClick = { boardIndex = index; boardExpanded = false },
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = daysExpanded,
                            onExpandedChange = { daysExpanded = !daysExpanded },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = dayOptions[daysIndex].second,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("时间") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = daysExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = daysExpanded,
                                onDismissRequest = { daysExpanded = false },
                            ) {
                                dayOptions.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = { Text(option.second) },
                                        onClick = { daysIndex = index; daysExpanded = false },
                                    )
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = orderExpanded,
                            onExpandedChange = { orderExpanded = !orderExpanded },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = orderOptions[orderIndex].second,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("排序") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = orderExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = orderExpanded,
                                onDismissRequest = { orderExpanded = false },
                            ) {
                                orderOptions.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = { Text(option.second) },
                                        onClick = { orderIndex = index; orderExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                notice?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    if (mode == "thread") {
                        items(threads, key = { it.tid }) { hit ->
                            Text(
                                hit.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenThread(hit.tid, hit.title) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            androidx.compose.material3.HorizontalDivider()
                        }
                    } else {
                        items(users, key = { it.uid }) { user ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenUser(
                                            "https://mce.msi.cn/home.php?mod=space&uid=${user.uid}",
                                            user.name,
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    user.avatarUrl?.let {
                                        AsyncImage(
                                            model = it,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        user.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "UID ${user.uid}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
            }
        }

        ImmersiveTopBar(title = "搜索", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}
