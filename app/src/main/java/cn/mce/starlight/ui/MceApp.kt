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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
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
import cn.mce.starlight.core.SettingsStore
import cn.mce.starlight.data.AuthClient
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.UpdateClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 导航栈 */
sealed interface Route {
    data class Thread(val tid: Int, val title: String) : Route
    data class Page(val url: String, val title: String) : Route
    data class Compose(val fid: Int?, val draftId: Long? = null) : Route
    data class Board(val fid: Int, val name: String) : Route
    data object MyThreads : Route
    data object MyReplies : Route
    data object Favorites : Route
    data object Notices : Route
    data object ProfileEdit : Route
    data object Medals : Route
    data object Friends : Route
    data class User(val uid: Int?, val name: String) : Route
    data object DraftBox : Route
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
    var updateRelease by remember { mutableStateOf<UpdateClient.Release?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var showSigninReminder by remember { mutableStateOf(false) }

    fun openDrawer() { scope.launch { drawerState.open() } }
    fun closeDrawer() { scope.launch { drawerState.close() } }
    fun push(route: Route) { stack = stack + route }
    fun pop() { stack = stack.dropLast(1) }

    fun checkUpdate(silent: Boolean) {
        scope.launch {
            val result = UpdateClient.checkDetailed(context)
            SettingsStore.markUpdateChecked()
            when (result) {
                is UpdateClient.CheckResult.Available -> updateRelease = result.release
                is UpdateClient.CheckResult.Latest ->
                    if (!silent) Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                is UpdateClient.CheckResult.Failed ->
                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refreshSession() {
        scope.launch {
            SessionStore.probeLoginState()
            dataVersion += 1
        }
    }

    LaunchedEffect(Unit) {
        // 启动三项网络请求并行（旧实现串行：登录探测 → 签到提醒 → 检查更新，
        // 最坏要把三条请求排完才结束，启动初期 4~8 个请求排队抢带宽）。
        coroutineScope {
            // 只有「确定未登录」才弹登录框；网络异常(null)保持静默，避免误判踢人
            launch {
                val probe = SessionStore.probeLoginState()
                if (probe == false) showLogin = true
            }
            // 签到提醒：已过提醒时间且今日未签到 → 启动时提示（只查状态，不拉排行榜）
            launch {
                if (SettingsStore.signinReminderEnabled &&
                    java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >=
                    SettingsStore.signinReminderHour
                ) {
                    runCatching { cn.mce.starlight.data.SigninClient.loadStateOnly() }.getOrNull()?.let { s ->
                        if (!s.alreadySigned) showSigninReminder = true
                    }
                }
            }
            // 启动后自动检查更新（暂停更新期间跳过）
            launch {
                if (!SettingsStore.isUpdateSnoozed()) checkUpdate(silent = true)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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

                DrawerItem("发帖", false) {
                    dest = Dest.Feed
                    stack = listOf(Route.Compose(null))
                    closeDrawer()
                }
                DrawerItem("草稿箱" + if (cn.mce.starlight.core.DraftStore.drafts.isEmpty()) "" else "（${cn.mce.starlight.core.DraftStore.drafts.size}）", false) {
                    dest = Dest.Feed
                    stack = listOf(Route.DraftBox)
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
                onOpenUser = { uid, name -> push(Route.User(if (uid > 0) uid else null, name)) },
                onOpenPage = { url, title -> push(Route.Page(url, title)) },
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
                initialDraft = current.draftId?.let { cn.mce.starlight.core.DraftStore.get(it) },
                onPosted = {
                    // 发帖成功：回到主页并刷新列表
                    stack = emptyList()
                    dest = Dest.Feed
                    dataVersion += 1
                },
            )

            is Route.MyThreads -> MyThreadsScreen(
                onMenu = ::openDrawer,
                onOpenDraftBox = { push(Route.DraftBox) },
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

            is Route.DraftBox -> DraftBoxScreen(
                onMenu = ::openDrawer,
                onOpenDraft = { id -> push(Route.Compose(null, id)) },
                onComposeNew = { push(Route.Compose(null)) },
            )

            is Route.User -> UserProfileScreen(
                uid = current.uid,
                onMenu = ::openDrawer,
                onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
                onOpenPage = { url, title -> push(Route.Page(url, title)) },
            )

            is Route.Medals -> MedalsScreen(onMenu = ::openDrawer)

            is Route.Friends -> FriendsScreen(
                onMenu = ::openDrawer,
                onOpenUser = { uid, title -> push(Route.User(uid, title)) },
            )

            is Route.GenericList -> GenericListScreen(
                title = current.title,
                path = current.path,
                onMenu = ::openDrawer,
                onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
                onOpenUser = { uid, name -> push(Route.User(uid, name)) },
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

                Dest.Settings -> SettingsScreen(
                    onMenu = ::openDrawer,
                    onCheckUpdate = { checkUpdate(silent = false) },
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

    if (showSigninReminder) {
        SigninReminderDialog(
            onGoSignin = {
                showSigninReminder = false
                dest = Dest.SignIn
                stack = emptyList()
            },
            onDismiss = { showSigninReminder = false },
        )
    }

    updateRelease?.let { release ->
        UpdateDialog(
            release = release,
            downloading = updateDownloading,
            progress = updateProgress,
            downloadError = updateError,
            onUpdate = {
                val url = release.apkUrl
                if (url.isNullOrBlank()) {
                    updateError = "缺少更新下载地址"
                } else {
                    updateDownloading = true
                    updateError = null
                    updateProgress = 0
                    scope.launch {
                        var file = UpdateClient.download(context, url) { p -> updateProgress = p }
                        if (file == null) {
                            // 备用源：直连 raw.githubusercontent（部分网络可用）
                            val backup = url.replace("https://gh-proxy.com/", "")
                            file = UpdateClient.download(context, backup) { p -> updateProgress = p }
                        }
                        updateDownloading = false
                        if (file != null) {
                            updateRelease = null
                            val started = UpdateClient.install(context, file)
                            if (!started) {
                                Toast.makeText(context, "无法启动安装器，请在系统设置中允许安装未知应用", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            updateError = "下载失败，请检查网络后重试"
                        }
                    }
                }
            },
            onSnooze = {
                SettingsStore.snoozeUpdates(7)
                updateRelease = null
            },
            onDismiss = { updateRelease = null },
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
