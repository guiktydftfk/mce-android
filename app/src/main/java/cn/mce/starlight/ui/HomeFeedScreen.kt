package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import cn.mce.starlight.core.SettingsStore
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.ThreadSummary

/** 导读分类（对应 forum.php?mod=guide&view=xxx） */
private val GUIDE_TABS = listOf(
    "newthread" to "最新",
    "hot" to "热帖",
    "digest" to "精华",
    "sofa" to "沙发",
)

/**
 * 首页动态流（借鉴鸿蒙版设计）：
 * 卡片式布局 —— 头像/作者 + 版块标签 + 时间、标题、摘要、大图、分类与回复数。
 *
 * v0.3.3 批 5：列表状态/刷新/加载更多/空态统一由 [PagedThreadList] 承担；
 * 换导读分类时列表与滚动位置一起重置；去掉重复的顶部 LinearProgressIndicator。
 */
@Composable
fun HomeFeedScreen(
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    onOpenThread: (ThreadSummary) -> Unit,
    /** v0.4.1：从帖子返回时回到顶部 */
    scrollToTopKey: Int = 0,
    /** v0.4.1：首页各导读 tab 的滚动状态（由 MceApp 持有，从帖子返回时保持位置） */
    listStates: MutableMap<String, androidx.compose.foundation.lazy.LazyListState>? = null,
) {
    val repo = remember { HttpForumRepository() }
    var view by remember { mutableStateOf("newthread") }
    // v0.4.1：每个导读 tab 独立滚动状态；从帖子返回时同一 tab 复用原状态（不再回顶）
    val listState: androidx.compose.foundation.lazy.LazyListState? =
        if (listStates == null) null
        else remember(view) { listStates.getOrPut(view) { androidx.compose.foundation.lazy.LazyListState() } }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // v0.3.10：首页列表缓存 —— 首次打开/从帖子返回时先用缓存秒开，再后台刷新
    val filterFn: (ThreadSummary) -> Boolean = { thread ->
        val min = SettingsStore.feedMinChars
        min <= 0 || threadCharCount(thread) >= min
    }
    val cacheKey = "guide_$view"
    val cached = remember(view, reloadKey) { cn.mce.starlight.core.FeedCache.get(cacheKey) }

    Box(Modifier.fillMaxSize()) {
        PagedThreadList(
            // 过滤阈值变化时也重建列表（resetKey 变化 → 重新加载第 1 页）
            resetKey = Triple(view, reloadKey, SettingsStore.feedMinChars),
            scrollToTopKey = scrollToTopKey,
            externalListState = listState,
            topPadding = topInset + 104.dp,
            horizontalPadding = 12.dp,
            initialItems = cached?.threads.orEmpty(),
            initialHasNext = cached?.hasNext ?: true,
            onFirstPage = { page -> cn.mce.starlight.core.FeedCache.put(cacheKey, page) },
            emptyText = if (SettingsStore.feedFilterEnabled) {
                "当前设置过滤了少于 ${SettingsStore.feedMinChars} 字的帖子\n可在「设置 → 内容」里调整或关闭"
            } else {
                "这个分类还没有帖子，下拉刷新试试"
            },
            loader = { page ->
                val base = repo.loadGuide(view, page)
                // v0.3.9：论坛「导读」偶尔不收录自己刚发的帖子（版块列表能看到、首页看不到，
                // 但仍会收到该帖的新消息提醒）。首页第一页额外合并「我的最近主题」。
                // v0.4.0 修正：**不再顶到最前面**（旧实现把缺失的自己的帖子无条件 prepend，
                // 导致它们永远钉在首页顶端）。现在按时间归位，且只补仍在当前页时间范围内的帖子。
                if (page == 1 && view == "newthread") {
                    // v0.4.1：导读「最新」会延迟收录（甚至不收）刚发的新主题 ——
                    // 刷新时**并行**拉取所有板块的最新一页 + 我的最近主题，按时间归位合并。
                    val (mine, allBoards) = coroutineScope {
                        val mineAsync = async {
                            runCatching { repo.loadGuide("my", 1) }.getOrNull()
                        }
                        val allAsync = async {
                            runCatching { repo.loadLatestAllBoards() }.getOrDefault(emptyList())
                        }
                        mineAsync.await() to allAsync.await()
                    }
                    val known = base.threads.mapTo(HashSet()) { it.id }
                    val extras = (allBoards + mine?.threads.orEmpty())
                        .filter { it.id !in known }
                        .distinctBy { it.id }
                        .sortedByDescending {
                            cn.mce.starlight.core.FeedTime.parseApproxMs(it.metaText) ?: 0L
                        }
                        .take(20)
                    if (extras.isEmpty()) base else base.copy(
                        threads = cn.mce.starlight.core.FeedTime.mergeExtras(base.threads, extras),
                    )
                } else {
                    base
                }
            },
            // v0.3.8：按字数过滤（只看首页信息流，不影响板块/搜索）
            filter = filterFn,
            onOpenThread = onOpenThread,
        )

        ImmersiveTopBar(
            title = "全部最新",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )

        // 导读分类
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset + 52.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GUIDE_TABS.forEach { (value, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (view == value) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (view == value) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { if (view != value) view = value }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 首页过滤用的「字数」：优先用摘要（正文节选），没有摘要时退回标题长度 */
private fun threadCharCount(thread: ThreadSummary): Int {
    val s = thread.summary?.trim().orEmpty()
    return if (s.isNotEmpty()) s.length else thread.title.trim().length
}

