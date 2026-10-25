package cn.mce.starlight.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.data.AuthClient
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.HttpForumRepository
import kotlinx.coroutines.launch

private const val BASE = "https://mce.msi.cn"

/** 导航栈 */
sealed interface Route {
    data class Thread(val tid: Int, val title: String) : Route
    data class Page(val url: String, val title: String) : Route
    data class Compose(val fid: Int?) : Route
    data class Board(val fid: Int, val name: String) : Route
    data object MyThreads : Route
    data object MyReplies : Route
    data object Favorites : Route
    data object Notices : Route
    data object ProfileEdit : Route
    data object Medals : Route
    data object Friends : Route
    data class GenericList(val title: String, val path: String) : Route
}

/** 抽屉一级目的地 */
private enum class Dest(val label: String) {
    Feed("全部最新"),
    Profile("我的"),
    SignIn("签到"),
    Tasks("任务中心"),
    Search("搜索"),
    Ranklist("排行榜"),
    Notice("消息提醒"),
    Compose("发帖"),
    Settings("设置"),
}

@Composable
fun MceApp() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var dest by remember { mutableStateOf(Dest.Feed) }
    var stack by remember { mutableStateOf(listOf<Route>()) }
    var dataVersion by remember { mutableIntStateOf(0) }
    var showLogin by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(setOf("全国交流")) }

    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }

    fun openDrawer() { scope.launch { drawerState.open() } }
    fun closeDrawer() { scope.launch { drawerState.close() } }
    fun push(route: Route) { stack = stack + route }
    fun pop() { stack = stack.dropLast(1) }

    fun refreshSession() {
        scope.launch {
            SessionStore.probeLoginState()
            dataVersion += 1
        }
    }

    LaunchedEffect(Unit) {
        SessionStore.probeLoginState()
        if (!SessionStore.isLoggedIn) showLogin = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)) {
                    Text("MCE星光班", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (SessionStore.isLoggedIn) {
                            "已登录 · ${SessionStore.username ?: "成员"}"
                        } else {
                            "未登录 · 打开即提示登录"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                DrawerItem("全部最新", dest == Dest.Feed && stack.isEmpty()) {
                    dest = Dest.Feed; stack = emptyList(); closeDrawer()
                }
                DrawerItem("我的", dest == Dest.Profile) {
                    dest = Dest.Profile; stack = emptyList(); closeDrawer()
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(
                    "板块",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp),
                )
                Boards.groups.forEach { (groupName, groupBoards) ->
                    val expanded = groupName in expandedGroups
                    DrawerItem(
                        label = (if (expanded) "\u25be " else "\u25b8 ") + groupName,
                        selected = false,
                    ) {
                        expandedGroups = if (expanded) expandedGroups - groupName
                        else expandedGroups + groupName
                    }
                    if (expanded) {
                        groupBoards.forEach { board ->
                            val selected = dest == Dest.Feed && stack.firstOrNull()
                                ?.let { it is Route.Board && it.fid == board.fid } == true
                            DrawerItem(label = "    " + board.name, selected = selected) {
                                dest = Dest.Feed
                                stack = listOf(Route.Board(board.fid, board.name))
                                closeDrawer()
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                DrawerItem("发帖", dest == Dest.Compose) {
                    dest = Dest.Feed
                    stack = listOf(Route.Compose(null))
                    closeDrawer()
                }
                DrawerItem("签到", dest == Dest.SignIn) {
                    dest = Dest.SignIn; stack = emptyList(); closeDrawer()
                }
                DrawerItem("任务中心", dest == Dest.Tasks) {
                    dest = Dest.Tasks; stack = emptyList(); closeDrawer()
                }
                DrawerItem("搜索", dest == Dest.Search) {
                    dest = Dest.Search; stack = emptyList(); closeDrawer()
                }
                DrawerItem("排行榜", dest == Dest.Ranklist) {
                    dest = Dest.Ranklist; stack = emptyList(); closeDrawer()
                }
                DrawerItem("消息提醒", dest == Dest.Notice) {
                    dest = Dest.Notice; stack = emptyList(); closeDrawer()
                }
                DrawerItem("设置", dest == Dest.Settings) {
                    dest = Dest.Settings; stack = emptyList(); closeDrawer()
                }
            }
        },
    ) {
        BackHandler(enabled = stack.isNotEmpty() || dest != Dest.Feed) {
            if (stack.isNotEmpty()) pop() else dest = Dest.Feed
        }

        // 根页面：双击返回退出应用
        BackHandler(enabled = stack.isEmpty() && dest == Dest.Feed) {
            val now = System.currentTimeMillis()
            if (now - lastBackAt < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackAt = now
                Toast.makeText(context, "\u518d\u6309\u4e00\u6b21\u9000\u51fa", Toast.LENGTH_SHORT).show()
            }
        }

        val current = stack.lastOrNull()
        when (current) {
            is Route.Thread -> ThreadDetailScreen(
                tid = current.tid,
                fallbackTitle = current.title,
                onMenu = ::openDrawer,
                reloadKey = dataVersion,
            )

            is Route.Page -> NativePageScreen(
                initialUrl = current.url,
                fallbackTitle = current.title,
                onMenu = ::openDrawer,
                onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
            )

            is Route.Compose -> NewThreadScreen(
                onMenu = ::openDrawer,
                initialFid = current.fid,
                onPosted = {
                    pop()
                    dataVersion += 1
                },
            )

            is Route.MyThreads -> MyThreadsScreen(
                onMenu = ::openDrawer,
                onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
            )

            is Route.MyReplies -> MyRepliesScreen(
                onMenu = ::openDrawer,
                onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
            )

            is Route.Favorites -> FavoritesScreen(
                onMenu = ::openDrawer,
                onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
            )

            is Route.ProfileEdit -> ProfileEditScreen(
                onMenu = ::openDrawer,
                onSaved = { dataVersion += 1; pop() },
            )

            is Route.Medals -> MedalsScreen(onMenu = ::openDrawer)

            is Route.Friends -> FriendsScreen(
                onMenu = ::openDrawer,
                onOpenUser = { uid, title ->
                    push(Route.Page("$BASE/home.php?mod=space&uid=$uid", title))
                },
            )

            is Route.GenericList -> GenericListScreen(
                title = current.title,
                path = current.path,
                onMenu = ::openDrawer,
                onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
                onOpenUser = { uid, title ->
                    push(Route.Page("$BASE/home.php?mod=space&uid=$uid", title))
                },
            )

            is Route.Notices -> NoticesScreen(
                onMenu = ::openDrawer,
                onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
            )

            is Route.Board -> ThreadListScreen(
                title = current.name,
                onMenu = ::openDrawer,
                reloadKey = dataVersion,
                enableFilters = true,
                loader = { page, filter -> HttpForumRepository().loadThreads(current.fid, page, filter) },
                onOpenThread = { push(Route.Thread(it.id, it.title)) },
                onCompose = { push(Route.Compose(current.fid)) },
            )

            null -> when (dest) {
                // 首页动态流（卡片式，借鉴鸿蒙版布局）
                Dest.Feed -> HomeFeedScreen(
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    onOpenThread = { push(Route.Thread(it.id, it.title)) },
                    onCompose = { push(Route.Compose(null)) },
                )

                Dest.Profile -> ProfileScreen(
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    onLogin = { showLogin = true },
                    onLogout = {
                        scope.launch {
                            AuthClient.logout()
                            SessionStore.clearLocal()
                            dataVersion += 1
                        }
                    },
                    onOpenPage = { url, _ ->
                        when {
                            url.contains("do=thread") && url.contains("type=reply") -> push(Route.MyReplies)
                            url.contains("do=thread") -> push(Route.MyThreads)
                            url.contains("do=favorite") -> push(Route.Favorites)
                            url.contains("do=notice") -> push(Route.Notices)
                            url.contains("spacecp") && url.contains("ac=profile") -> push(Route.ProfileEdit)
                            url.contains("do=medal") -> push(Route.Medals)
                            url.contains("do=friend") -> push(Route.Friends)
                            url.contains("mod=magic") -> push(Route.GenericList("道具中心", "home.php?mod=magic"))
                            else -> push(Route.Page(url, url))
                        }
                    },
                )

                Dest.SignIn -> SigninScreen(
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    onNeedLogin = { showLogin = true },
                )

                Dest.Tasks -> TaskScreen(onMenu = ::openDrawer, reloadKey = dataVersion)

                Dest.Search -> SearchScreen(
                    onMenu = ::openDrawer,
                    onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
                    onOpenUser = { url, title -> push(Route.Page(url, title)) },
                )

                Dest.Ranklist -> RanklistScreen(
                    onMenu = ::openDrawer,
                    onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
                )

                Dest.Notice -> NoticesScreen(
                    onMenu = ::openDrawer,
                    onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
                )

                Dest.Compose -> NewThreadScreen(
                    onMenu = ::openDrawer,
                    onPosted = { dest = Dest.Feed; dataVersion += 1 },
                )

                Dest.Settings -> SettingsScreen(
                    onMenu = ::openDrawer,
                    onLogin = { showLogin = true },
                    onLogout = {
                        scope.launch {
                            AuthClient.logout()
                            SessionStore.clearLocal()
                            dataVersion += 1
                        }
                    },
                )
            }
        }
    }

    if (showLogin) {
        LoginDialog(
            onDismiss = { showLogin = false },
            onSuccess = {
                showLogin = false
                refreshSession()
            },
        )
    }
}

@Composable
private fun DrawerItem(label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
