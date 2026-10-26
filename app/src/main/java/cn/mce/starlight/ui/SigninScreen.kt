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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.SigninClient
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 签到页：统计 + 签到按钮 + 排行榜（今日/本月/总）。
 *
 * v0.3.2 修复「排行榜显示不完整」：
 *  - 旧实现只取第一页并 take(30)，今日榜 146 人、本月/总榜 200+ 人时大量缺失；
 *  - 现在整页只有**一个 LazyColumn**（统计卡 / 签到 / 榜单同一滚动容器），
 *    排行榜按论坛真实分页（每页 200 条）触底自动加载，直到没有下一页。
 */
@Composable
fun SigninScreen(
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    onNeedLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<SigninClient.State?>(null) }
    var ranks by remember { mutableStateOf(emptyList<SigninClient.RankEntry>()) }
    var hasMore by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var rankType by remember { mutableStateOf("today") }

    // 换榜单时给列表一个全新的 state，天然回到顶部
    // （不要在列表还没布局时调用 scrollToItem —— 那会永久挂起，表现为一直转圈）
    val listState = remember(rankType) { LazyListState() }

    suspend fun load() {
        loading = true
        message = null
        if (data == null) error = null
        ranks = emptyList()
        hasMore = false
        runCatching { SigninClient.load(rankType) }
            .onSuccess { s ->
                data = s
                ranks = s.ranks
                hasMore = s.hasMoreRanks
                page = 1
                error = null
            }
            .onFailure {
                if (data == null) error = it.message ?: "加载失败"
                else message = "刷新失败：" + (it.message ?: "网络错误")
            }
        loading = false
    }

    suspend fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        runCatching { SigninClient.loadRankPage(rankType, page + 1) }
            .onSuccess { p ->
                if (p.entries.isEmpty()) {
                    hasMore = false
                } else {
                    // 跨页去重，避免同一用户重复出现
                    val known = ranks.mapTo(HashSet()) { it.uid ?: it.username.hashCode() }
                    val fresh = p.entries.filter { known.add(it.uid ?: it.username.hashCode()) }
                    ranks = ranks + fresh
                    page += 1
                    hasMore = p.hasMore
                }
            }
            .onFailure { message = "加载更多失败：" + (it.message ?: "网络错误") }
        loadingMore = false
    }

    LaunchedEffect(reloadKey, rankType) {
        load()
    }

    // 触底自动加载下一页（"加载更多"按钮也在，作为手动兜底）
    LaunchedEffect(listState, rankType, hasMore) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.distinctUntilChanged().collect { (last, total) ->
            if (hasMore && total > 0 && last >= total - 4) loadMore()
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val state = data

    Box(Modifier.fillMaxSize()) {
        when {
            state == null && loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            state == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "加载失败", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { scope.launch { load() } }) { Text("重试") }
                    if ((error ?: "").contains("登录")) {
                        TextButton(onClick = onNeedLogin) { Text("去登录") }
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = topInset + 64.dp, bottom = 96.dp),
            ) {
                // 1. 统计卡
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem("今日排名", state.todayRank ?: "-")
                        StatItem("连续签到", state.continuous ?: "-")
                        StatItem("累计签到", state.total ?: "-")
                    }
                }

                // 2. 签到状态与按钮
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            if (state.alreadySigned) "今天已签到 ✔" else "今天还没有签到",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.alreadySigned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        state.level?.let {
                            Text(
                                "签到等级 Lv $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        message?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = !busy && !state.alreadySigned && state.formhash != null,
                            onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    val (ok, msg) = SigninClient.signIn(state)
                                    busy = false
                                    message = msg
                                    if (ok) load()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "签到中…" else "立即签到") }
                    }
                }

                // 3. 榜单切换 + 人数
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            RankTab("今日", rankType == "today") { rankType = "today" }
                            RankTab("本月", rankType == "month") { rankType = "month" }
                            RankTab("总排行", rankType == "total") { rankType = "total" }
                        }
                        if (ranks.isNotEmpty()) {
                            Text(
                                if (hasMore) "已加载 ${ranks.size} 人" else "共 ${ranks.size} 人",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                }

                // 4. 榜单
                if (loading) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            Alignment.Center,
                        ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    }
                } else if (ranks.isEmpty()) {
                    item {
                        Text(
                            "暂无排行榜数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    itemsIndexed(ranks, key = { i, e -> "${e.uid ?: e.username}-$i" }) { index, entry ->
                        RankRow(index + 1, entry, rankType)
                        HorizontalDivider(
                            Modifier.padding(start = 58.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    // 5. 底部：加载更多 / 全部加载完
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            Alignment.Center,
                        ) {
                            when {
                                loadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在加载…", style = MaterialTheme.typography.bodySmall)
                                }

                                hasMore -> TextButton(onClick = { scope.launch { loadMore() } }) {
                                    Text("加载更多（已显示 ${ranks.size} 人）")
                                }

                                else -> Text(
                                    "已显示全部 ${ranks.size} 人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        ImmersiveTopBar(title = "签到", modifier = Modifier.align(Alignment.TopStart), onMenu = onMenu)
    }
}

@Composable
private fun RankRow(rank: Int, entry: SigninClient.RankEntry, rankType: String) {
    val value = when (rankType) {
        "month" -> entry.monthDays
        else -> entry.totalDays
    }
    val subtitle = listOfNotNull(
        entry.lastSignText,
        if (rankType == "month") entry.totalDays?.let { "总 $it 天" } else entry.monthDays?.let { "月 $it 天" },
        entry.reward?.let { "奖励 $it 社区值" },
    ).joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = when (rank) {
                1 -> Color(0xFFE0A020)
                2 -> Color(0xFF8C99AB)
                3 -> Color(0xFFBE7B4A)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(34.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.username,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value?.let { "$it 天" } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RankTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
