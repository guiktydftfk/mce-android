package cn.mce.starlight.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.web.WebContentView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private const val BASE = "https://mce.msi.cn"
private const val LOGOUT_URL = "$BASE/member.php?mod=logging&action=logout&formhash="

/** 导航栈中的一页 */
sealed interface Route {
    data class Board(val fid: Int, val name: String) : Route
    data class Thread(val tid: Int, val title: String) : Route
    data class Web(val url: String, val title: String) : Route
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Forum("论坛", Icons.Filled.Home),
    Discover("发现", Icons.AutoMirrored.Filled.List),
    Profile("我的", Icons.Filled.Person),
}

@Composable
fun MceApp() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(Tab.Forum) }
    var forumStack by remember { mutableStateOf(listOf<Route>()) }
    var discoverStack by remember { mutableStateOf(listOf<Route>()) }
    var webPage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var dataVersion by remember { mutableIntStateOf(0) }

    fun openDrawer() { scope.launch { drawerState.open() } }
    fun closeDrawer() { scope.launch { drawerState.close() } }
    fun pushForum(route: Route) { forumStack = forumStack + route }
    fun popForum() { forumStack = forumStack.dropLast(1) }
    fun pushDiscover(route: Route) { discoverStack = discoverStack + route }
    fun popDiscover() { discoverStack = discoverStack.dropLast(1) }

    /** 登录态变化后：关闭 WebView、重新探测、让原生列表重新拉取 */
    fun afterAuthChanged() {
        webPage = null
        scope.launch {
            SessionStore.probeLoginState()
            dataVersion += 1
        }
    }

    fun openWeb(url: String, title: String) {
        webPage = url to title
        closeDrawer()
    }

    // 启动时探测一次登录态
    LaunchedEffect(Unit) { SessionStore.probeLoginState() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp)) {
                    Text("MCE星光班", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (SessionStore.isLoggedIn) {
                            "已登录 · ${SessionStore.username ?: "成员"}"
                        } else {
                            "未登录 · 点下方登录"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Tab.entries.forEach { tab ->
                    NavigationDrawerItem(
                        label = { Text(tab.label) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        selected = currentTab == tab,
                        onClick = {
                            currentTab = tab
                            forumStack = emptyList()
                            discoverStack = emptyList()
                            closeDrawer()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "账号与工具",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                )
                if (SessionStore.isLoggedIn) {
                    DrawerLink("退出登录") { openWeb(LOGOUT_URL, "退出登录") }
                } else {
                    DrawerLink("登录 / 注册") {
                        openWeb("$BASE/member.php?mod=logging&action=login", "登录")
                    }
                }
                DrawerLink("签到") { openWeb("$BASE/plugin.php?id=k_misign:sign", "签到") }
                DrawerLink("任务中心") { openWeb("$BASE/home.php?mod=task", "任务中心") }
                DrawerLink("常用表单") {
                    openWeb("$BASE/forum.php?mod=viewthread&tid=11219", "常用表单")
                }
                DrawerLink("搜索") { openWeb("$BASE/search.php?mod=forum", "搜索") }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            when (currentTab) {
                Tab.Forum -> RouteStack(
                    stack = forumStack,
                    push = ::pushForum,
                    pop = ::popForum,
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    root = {
                        ForumBoardScreen(
                            onMenu = ::openDrawer,
                            onOpenBoard = { pushForum(Route.Board(it.fid, it.name)) },
                            onOpenWeb = { pushForum(Route.Web("$BASE/forum.php", "社区论坛")) },
                        )
                    },
                )

                Tab.Discover -> RouteStack(
                    stack = discoverStack,
                    push = ::pushDiscover,
                    pop = ::popDiscover,
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    root = {
                        ThreadListScreen(
                            title = "看帖中心",
                            onMenu = ::openDrawer,
                            reloadKey = dataVersion,
                            loader = { HttpForumRepository().loadGuide(1).threads },
                            onOpenThread = { pushDiscover(Route.Thread(it.id, it.title)) },
                            onOpenWeb = {
                                pushDiscover(
                                    Route.Web("$BASE/forum.php?mod=guide&view=newthread", "看帖中心"),
                                )
                            },
                        )
                    },
                )

                Tab.Profile -> ProfileScreen(
                    onMenu = ::openDrawer,
                    onOpenWeb = ::openWeb,
                    onLogout = { openWeb(LOGOUT_URL, "退出登录") },
                )
            }

            // 全局 WebView（登录 / 签到 / 插件页）
            val web = webPage
            if (web != null) {
                BackHandler { afterAuthChanged() }
                WebContentView(
                    url = web.first,
                    onAuthStateChanged = { afterAuthChanged() },
                )
            }
        }
    }
}

@Composable
private fun DrawerLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun RouteStack(
    stack: List<Route>,
    push: (Route) -> Unit,
    pop: () -> Unit,
    onMenu: () -> Unit,
    reloadKey: Int,
    root: @Composable () -> Unit,
) {
    val current = stack.lastOrNull()
    BackHandler(enabled = current != null) { pop() }

    when (current) {
        null -> root()

        is Route.Board -> ThreadListScreen(
            title = current.name,
            onMenu = onMenu,
            reloadKey = reloadKey,
            loader = { HttpForumRepository().loadThreads(current.fid, 1).threads },
            onOpenThread = { push(Route.Thread(it.id, it.title)) },
            onOpenWeb = {
                push(Route.Web("$BASE/forum.php?mod=forumdisplay&fid=${current.fid}", current.name))
            },
        )

        is Route.Thread -> ThreadDetailScreen(
            tid = current.tid,
            fallbackTitle = current.title,
            onMenu = onMenu,
            reloadKey = reloadKey,
            onOpenWeb = {
                push(Route.Web("$BASE/forum.php?mod=viewthread&tid=${current.tid}", current.title))
            },
        )

        is Route.Web -> WebContentView(url = current.url)
    }
}

@Composable
private fun ProfileScreen(
    onMenu: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onLogout: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = 100.dp, start = 20.dp, end = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val avatar = SessionStore.avatarUrl
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        SessionStore.username ?: "未登录",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (SessionStore.isLoggedIn) "已登录" else "登录后可看私密版块、签到、发帖",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            if (SessionStore.isLoggedIn) {
                LinkRow("退出登录", onLogout)
            } else {
                LinkRow("登录 / 注册") {
                    onOpenWeb("$BASE/member.php?mod=logging&action=login", "登录")
                }
            }
            LinkRow("签到") { onOpenWeb("$BASE/plugin.php?id=k_misign:sign", "签到") }
            LinkRow("任务中心") { onOpenWeb("$BASE/home.php?mod=task", "任务中心") }
            LinkRow("搜索") { onOpenWeb("$BASE/search.php?mod=forum", "搜索") }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Text(
                "Phase 4 · 登录态与 Cookie 打通（原生列表可读私密版块）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ImmersiveTopBar(title = "我的", onMenu = onMenu)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
