package cn.mce.starlight.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.data.AuthClient
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.HttpForumRepository
import kotlinx.coroutines.launch

private const val BASE = "https://mce.msi.cn"

/** 导航栈中的一页 */
sealed interface Route {
    data class Board(val fid: Int, val name: String) : Route
    data class Thread(val tid: Int, val title: String) : Route
    data class Page(val url: String, val title: String) : Route
}

/** 抽屉一级目的地 */
private enum class Dest(val label: String) {
    Board2("综合交流"),
    Board53("通知公告"),
    Board121("小红龙频道"),
    Guide("看帖中心"),
    SignIn("签到"),
    Tasks("任务中心"),
    Search("搜索"),
    Settings("设置"),
}

@Composable
fun MceApp() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var dest by remember { mutableStateOf(Dest.Board2) }
    var stack by remember { mutableStateOf(listOf<Route>()) }
    var dataVersion by remember { mutableIntStateOf(0) }
    var showLogin by remember { mutableStateOf(false) }

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

    // 启动：探测登录态；未登录则弹登录框
    LaunchedEffect(Unit) {
        SessionStore.probeLoginState()
        if (!SessionStore.isLoggedIn) showLogin = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
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

                DrawerItem("综合交流", dest == Dest.Board2) {
                    dest = Dest.Board2; stack = emptyList(); closeDrawer()
                }
                DrawerItem("通知公告", dest == Dest.Board53) {
                    dest = Dest.Board53; stack = emptyList(); closeDrawer()
                }
                DrawerItem("小红龙频道", dest == Dest.Board121) {
                    dest = Dest.Board121; stack = emptyList(); closeDrawer()
                }
                DrawerItem("看帖中心", dest == Dest.Guide) {
                    dest = Dest.Guide; stack = emptyList(); closeDrawer()
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                DrawerItem("签到", dest == Dest.SignIn) {
                    dest = Dest.SignIn; stack = emptyList(); closeDrawer()
                }
                DrawerItem("任务中心", dest == Dest.Tasks) {
                    dest = Dest.Tasks; stack = emptyList(); closeDrawer()
                }
                DrawerItem("搜索", dest == Dest.Search) {
                    dest = Dest.Search; stack = emptyList(); closeDrawer()
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                DrawerItem("设置", dest == Dest.Settings) {
                    dest = Dest.Settings; stack = emptyList(); closeDrawer()
                }
            }
        },
    ) {
        BackHandler(enabled = stack.isNotEmpty()) { pop() }

        val current = stack.lastOrNull()
        when (current) {
            null -> when (dest) {
                Dest.Board2 -> ThreadListScreen(
                    title = "综合交流",
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    loader = { HttpForumRepository().loadThreads(2, 1).threads },
                    onOpenThread = { push(Route.Thread(it.id, it.title)) },
                )

                Dest.Board53 -> ThreadListScreen(
                    title = "通知公告",
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    loader = { HttpForumRepository().loadThreads(53, 1).threads },
                    onOpenThread = { push(Route.Thread(it.id, it.title)) },
                )

                Dest.Board121 -> ThreadListScreen(
                    title = "小红龙频道",
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    loader = { HttpForumRepository().loadThreads(121, 1).threads },
                    onOpenThread = { push(Route.Thread(it.id, it.title)) },
                )

                Dest.Guide -> ThreadListScreen(
                    title = "看帖中心",
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    loader = { HttpForumRepository().loadGuide(1).threads },
                    onOpenThread = { push(Route.Thread(it.id, it.title)) },
                )

                Dest.SignIn -> SigninScreen(
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    onNeedLogin = { showLogin = true },
                )

                Dest.Tasks -> TaskScreen(
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                )

                Dest.Search -> SearchScreen(
                    onMenu = ::openDrawer,
                    onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
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

            is Route.Board -> ThreadListScreen(
                title = current.name,
                onMenu = ::openDrawer,
                reloadKey = dataVersion,
                loader = { HttpForumRepository().loadThreads(current.fid, 1).threads },
                onOpenThread = { push(Route.Thread(it.id, it.title)) },
            )
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
