package cn.mce.starlight.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mce.starlight.R
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
    data object FriendQr : Route
    data class Pm(val touid: Int, val name: String) : Route
    data object Scan : Route
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
    // v0.4.1：抽屉内容首次打开前不参与组合（关闭状态下也能省掉一整套抽屉 UI 的首次合成）
    var drawerEverOpened by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // v0.3.7 液态玻璃：抽屉开合动画只驱动抽屉自身（右侧内容区不参与逐帧重组）。
    // 模糊只在「完全打开」时一次性开启，避免每帧改 RenderEffect 导致首页掉帧。
    // API < 31 没有 RenderEffect，Modifier.blur 自动降级为无模糊（仍有抽屉遮罩兜底）。
    val drawerAnimation = animateFloatAsState(
        targetValue = if (drawerState.isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "drawerAnimation",
    )
    val drawerOpened = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open

    // v0.3.8 液态玻璃流光：只在每次打开抽屉时**扫过一遍**（旧实现无限循环，用户觉得重复且偏快）。
    // 用 Animatable + LaunchedEffect(drawerOpened) 触发；值只在抽屉子树里读取，不影响右侧内容重组。
    val glassSheen = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(drawerOpened) {
        // v0.4.1：一旦有打开意图（点菜单或边缘滑动），才真正合成抽屉内容
        if (drawerOpened) drawerEverOpened = true
        if (drawerOpened) {
            glassSheen.snapTo(0f)
            glassSheen.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 900,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            )
        } else {
            // 关闭后复位，下次打开从头扫一遍
            glassSheen.snapTo(0f)
        }
    }

    var dest by remember { mutableStateOf(Dest.Feed) }
    // v0.4.1：液态玻璃「高亮框」支持上下拖动切换——拖动时高亮跟随手指，松手后执行对应菜单项
    var drawerDragIndex by remember { mutableStateOf(-1) }
    val drawerItemBounds = remember { mutableStateMapOf<Int, ClosedFloatingPointRange<Float>>() }
    var stack by remember { mutableStateOf(listOf<Route>()) }
    var dataVersion by remember { mutableIntStateOf(0) }
    // v0.4.1：从帖子返回首页时回到顶部
    var feedTopTick by remember { mutableIntStateOf(0) }
    // v0.4.1：首页各 tab 的滚动状态放在导航之上——从帖子返回时保持当前滚动位置
    val feedListStates = remember { mutableStateMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    // v0.4.1：液态玻璃「重点框」直接拖动（拖动时框跟随手指，松手进入对应菜单）
    var drawerDragActive by remember { mutableStateOf(false) }
    var drawerDragDelta by remember { mutableStateOf(0f) }
    var drawerDragStartTop by remember { mutableStateOf(0f) }
    var drawerContentTop by remember { mutableStateOf(0f) }
    var showLogin by remember { mutableStateOf(false) }
    // v0.3.7：板块分组默认全部折叠（用户要求「全国交流」保持折叠）
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }

    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var updateRelease by remember { mutableStateOf<UpdateClient.Release?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var showSigninReminder by remember { mutableStateOf(false) }
    // v0.3.9：应用内提示条（替代系统 Toast —— MuMu/部分 ROM 上系统 Toast 会带安卓机器人图标，
    // 与应用无关且样式不可控）。所有「更新相关 / 退出确认」提示统一走这里。
    var appToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(appToast) {
        if (appToast != null) {
            kotlinx.coroutines.delay(2600)
            appToast = null
        }
    }

    fun openDrawer() {
        drawerEverOpened = true
        scope.launch { drawerState.open() }
    }
    fun closeDrawer() { scope.launch { drawerState.close() } }
    fun push(route: Route) { stack = stack + route }
    fun pop() {
        val popped = stack.lastOrNull()
        stack = stack.dropLast(1)
        // v0.4.1：从帖子详情返回列表时回到顶部（其它页面保持原位）
    }

    /** 下载并拉起安装（普通更新与紧急更新共用；紧急更新只是不需要用户先点「立即更新」） */
    fun startUpdate(release: UpdateClient.Release) {
        val url = release.apkUrl
        if (url.isNullOrBlank()) {
            updateError = "缺少更新下载地址"
            updateRelease = release
            return
        }
        if (updateDownloading) return
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
                    appToast = "无法启动安装器，请在系统设置中允许安装未知应用"
                }
            } else {
                updateError = "下载失败，请检查网络后重试"
                updateRelease = release
            }
        }
    }

    fun checkUpdate(silent: Boolean) {
        scope.launch {
            val result = UpdateClient.checkDetailed(context)
            SettingsStore.markUpdateChecked()
            when (result) {
                is UpdateClient.CheckResult.Available -> {
                    val release = result.release
                    // v0.3.8 紧急推送：清单标了 emergency 就跳过「是否更新」确认，直接后台下载，
                    // 下载完自动拉起系统安装器（Android 仍需要用户点一次「安装」）。
                    // 同一版本只自动处理一次，避免每次启动反复下载/弹安装器。
                    if (release.emergency && SettingsStore.urgentHandledCode != release.versionCode) {
                        SettingsStore.markUrgentHandled(release.versionCode)
                        SettingsStore.clearSnooze()
                        appToast = "紧急更新 ${release.versionName}：正在自动下载…"
                        startUpdate(release)
                    } else if (release.emergency) {
                        // 该紧急版本已自动处理过：不再重复下载 / 弹安装器
                    } else if (SettingsStore.isUpdateSnoozed()) {
                        // 用户暂停了更新：普通更新在暂停期内不打扰（紧急推送已在上面优先处理）
                    } else {
                        updateRelease = release
                    }
                }
                is UpdateClient.CheckResult.Latest ->
                    if (!silent) appToast = "已是最新版本"
                is UpdateClient.CheckResult.Failed ->
                    appToast = result.reason
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
                // v0.3.8：暂停更新不再挡住「检查」本身（否则紧急推送永远送不到），普通更新在 checkUpdate 内按暂停态过滤
                checkUpdate(silent = true)
            }
            // v0.3.9：打开应用时的消息检查 —— 只弹一次（本次启动内 launchBannerShown 保证不重复）；
            // 之后的回前台/新消息检查走下面 DisposableEffect 的 refresh()，只有真正的新消息才弹
            launch {
                runCatching { cn.mce.starlight.core.MessageCenter.refresh(fromLaunch = true) }
            }
        }
    }

    // 回到前台时检查一次新消息（v0.3.7：加 30s 节流，避免频繁切前后台反复拉消息页）
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var lastMessageCheckAt = 0L
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val now = System.currentTimeMillis()
                if (now - lastMessageCheckAt > 30_000L) {
                    lastMessageCheckAt = now
                    scope.launch { runCatching { cn.mce.starlight.core.MessageCenter.refresh() } }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = drawer@{
            // v0.4.1：首次打开前不合成抽屉内容（省启动首帧的合成开销）
            if (!drawerEverOpened) return@drawer
            // 只在抽屉自己的作用域里读动画值：抽屉外观逐帧动画不会导致右侧首页列表重组
            val progress = drawerAnimation.value
            // 玻璃流光：0→1 的一次性扫过（由 MceApp 顶部的 Animatable 驱动，播完即停），
            // 映射到 -0.35→1.35 让高光从中偏左扫到右侧消失
            val sheen = -0.35f + glassSheen.value * 1.7f
            // v0.4.1：可拖动切换的菜单项（与索引一一对应）
            val dragActions = listOf<() -> Unit>(
                { dest = Dest.Feed; stack = emptyList(); closeDrawer() },
                { dest = Dest.Profile; stack = emptyList(); closeDrawer() },
                { dest = Dest.SignIn; stack = emptyList(); closeDrawer() },
                { dest = Dest.Feed; stack = listOf(Route.Compose(null)); closeDrawer() },
                { dest = Dest.Feed; stack = listOf(Route.DraftBox); closeDrawer() },
                { dest = Dest.Tasks; stack = emptyList(); closeDrawer() },
                { dest = Dest.Search; stack = emptyList(); closeDrawer() },
                { dest = Dest.Ranklist; stack = emptyList(); closeDrawer() },
                { dest = Dest.Notice; stack = emptyList(); closeDrawer() },
                { dest = Dest.Settings; stack = emptyList(); closeDrawer() },
            )
            fun dragModifierFor(index: Int, isSelected: Boolean): Modifier {
                if (!isSelected) return Modifier
                return Modifier.pointerInput(index) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            drawerDragStartTop = drawerItemBounds[index]?.start ?: 0f
                            drawerDragDelta = offset.y
                            drawerDragActive = true
                            drawerDragIndex = index
                        },
                        onVerticalDrag = { _, delta ->
                            drawerDragDelta += delta
                            val pointerY = drawerDragStartTop + drawerDragDelta
                            var best = -1
                            var bestDist = Float.MAX_VALUE
                            drawerItemBounds.forEach { (i, range) ->
                                val center = (range.start + range.endInclusive) / 2f
                                val dist = kotlin.math.abs(center - pointerY)
                                if (dist < bestDist) {
                                    bestDist = dist
                                    best = i
                                }
                            }
                            if (best >= 0) drawerDragIndex = best
                        },
                        onDragEnd = {
                            val target = drawerDragIndex
                            drawerDragActive = false
                            drawerDragDelta = 0f
                            drawerDragIndex = -1
                            if (target in dragActions.indices) dragActions[target]()
                        },
                        onDragCancel = {
                            drawerDragActive = false
                            drawerDragDelta = 0f
                            drawerDragIndex = -1
                        },
                    )
                }
            }
            @Composable
            fun DrawerEntry(index: Int, label: String, selected: Boolean) {
                DrawerItem(
                    label = label,
                    // 拖动时由浮层画高亮，条目只负责加粗
                    selected = (drawerDragIndex == index) || (selected && !drawerDragActive),
                    showHighlight = !drawerDragActive && selected,
                    sheen = sheen,
                    onClick = { dragActions[index]() },
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val bounds = coords.boundsInRoot()
                            drawerItemBounds[index] = bounds.top..bounds.bottom
                        }
                        .then(dragModifierFor(index, selected)),
                )
            }            // v0.3.7 液态玻璃抽屉：半透明玻璃底板 + 白色高光/主题色底光（随呼出淡入）+ 玻璃边缘
            Box(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 340.dp),
            ) {
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxSize(),
                    drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    drawerTonalElevation = 0.dp,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { drawerContentTop = it.boundsInRoot().top },
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.14f * progress),
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * progress),
                                        ),
                                    ),
                                ),
                        )
                        Column(
                            Modifier
                                .fillMaxSize()
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

                            DrawerEntry(0, "全部最新", dest == Dest.Feed && stack.isEmpty())
                            DrawerEntry(1, "我的", dest == Dest.Profile)
                            // v0.3.7：签到入口提到「我的」下方（用户要求调整菜单顺序）
                            DrawerEntry(2, "签到", dest == Dest.SignIn)

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
                                    sheen = sheen,
                                ) {
                                    expandedGroups = if (expanded) expandedGroups - groupName
                                    else expandedGroups + groupName
                                }
                                if (expanded) {
                                    groupBoards.forEach { board ->
                                        val selected = dest == Dest.Feed && stack.firstOrNull()
                                            ?.let { it is Route.Board && it.fid == board.fid } == true
                                        DrawerItem(label = "    " + board.name, selected = selected, sheen = sheen) {
                                            dest = Dest.Feed
                                            stack = listOf(Route.Board(board.fid, board.name))
                                            closeDrawer()
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 6.dp))

                            DrawerEntry(3, "发帖", false)
                            DrawerEntry(
                                4,
                                "草稿箱" + if (cn.mce.starlight.core.DraftStore.drafts.isEmpty()) "" else "（${cn.mce.starlight.core.DraftStore.drafts.size}）",
                                false,
                            )
                            DrawerEntry(5, "任务中心", dest == Dest.Tasks)
                            DrawerEntry(6, "搜索", dest == Dest.Search)
                            DrawerEntry(7, "排行榜", dest == Dest.Ranklist)
                            DrawerEntry(8, "消息提醒", dest == Dest.Notice)
                            DrawerEntry(9, "设置", dest == Dest.Settings)
                        }
                        // v0.4.1：拖动时由这个浮层绘制「重点框」，直接跟手移动（条目自身不再画高亮）
                        if (drawerDragActive && drawerDragIndex >= 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(56.dp)
                                    .offset {
                                        IntOffset(
                                            x = 0,
                                            y = (drawerDragStartTop + drawerDragDelta - drawerContentTop).toInt(),
                                        )
                                    }
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                                                Color.White.copy(alpha = 0.16f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                            ),
                                            start = Offset(1600f * sheen - 300f, 0f),
                                            end = Offset(1600f * sheen + 500f, 420f),
                                        ),
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.38f), RoundedCornerShape(28.dp)),
                            )
                        }
                    }
                }
                // 玻璃边缘高光（无指针输入，不影响点击）
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.20f * progress),
                            shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        ),
                )
            }
        },
        // API<31 没有 RenderEffect，模糊无效 → 用更深的遮罩保证「焦点转移」观感
        scrimColor = Color.Black.copy(
            alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.20f else 0.44f,
        ),
    ) {
        // v0.3.7：抽屉呼出时右侧内容高斯模糊。
        // 只在「打开/关闭」两态间切换半径，不逐帧改 RenderEffect（逐帧重建全屏离屏缓冲会明显掉帧）。
        val contentBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && drawerOpened) {
            Modifier.blur(radius = 16.dp)
        } else {
            Modifier
        }
        Box(Modifier.fillMaxSize().then(contentBlur)) {
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
                appToast = "再按一次退出"
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
                // v0.3.10：退出发帖页（存草稿/放弃）后出栈
                onExit = { pop() },
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
                onOpenDraft = { id -> push(Route.Compose(null, id)) },
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

            is Route.Pm -> PmChatScreen(
                touid = current.touid,
                name = current.name,
                onMenu = ::openDrawer,
                // v0.3.8：点对方头像进入其主页（类微信）
                onOpenUser = { uid, userName -> push(Route.User(uid, userName)) },
            )

            is Route.FriendQr -> FriendQrScreen(
                onMenu = ::openDrawer,
                onOpenScan = { push(Route.Scan) },
            )

            is Route.Scan -> ScanScreen(
                onMenu = ::openDrawer,
                onScanned = { uid, name -> stack = emptyList(); push(Route.User(uid, name)) },
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
                // v0.3.7：从「我的 → 消息提醒」进入时私信行也需要能点开
                onOpenPm = { uid, name -> push(Route.Pm(uid, name)) },
                onOpenPage = { url, title -> push(Route.Page(url, title)) },
                onOpenUser = { uid, name -> push(Route.User(uid, name)) },
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
                    scrollToTopKey = feedTopTick,
                    listStates = feedListStates,
                )

                Dest.Profile -> ProfileScreen(
                    onMenu = ::openDrawer,
                    reloadKey = dataVersion,
                    onOpenFriendQr = { push(Route.FriendQr) },
                    onOpenMyProfile = {
                        val uid = SessionStore.uid
                        val name = SessionStore.username ?: "我的资料"
                        if (uid != null) push(Route.User(uid, name)) else appToast = "先登录才能查看我的资料"
                    },
                    onOpenScan = { push(Route.Scan) },
                    onLogin = { showLogin = true },
                    onLogout = {
                        scope.launch {
                            AuthClient.logout()
                            SessionStore.clearLocal()
                            cn.mce.starlight.core.MessageCenter.reset()
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
                    onOpenUser = { uid, name -> push(Route.User(uid, name)) },
                    onOpenThread = { tid, title -> push(Route.Thread(tid, title)) },
                    onOpenPm = { uid, title -> push(Route.Pm(uid, title)) },
                    onOpenPage = { url, title -> push(Route.Page(url, title)) },
                )

                Dest.Settings -> SettingsScreen(
                    onMenu = ::openDrawer,
                    onCheckUpdate = { checkUpdate(silent = false) },
                    onLogin = { showLogin = true },
                    onLogout = {
                        scope.launch {
                            AuthClient.logout()
                            SessionStore.clearLocal()
                            cn.mce.starlight.core.MessageCenter.reset()
                            dataVersion += 1
                        }
                    },
                )
            }
        }
        } // 关闭「右侧内容模糊」容器
    }

        // v0.3.7：已经在消息页时不再盖一条「有新消息」提示
        if (dest != Dest.Notice) {
            cn.mce.starlight.core.MessageCenter.banner?.let { banner ->
                // v0.3.9：提示条是「弹一次」的瞬时提醒，8 秒后自动收起（也可以点 × 立刻关掉），
                // 不再像旧版那样一直挂到用户手动点掉/进消息页
                LaunchedEffect(banner) {
                    kotlinx.coroutines.delay(8000)
                    cn.mce.starlight.core.MessageCenter.dismissBanner()
                }
                cn.mce.starlight.ui.NewMessageBanner(
                    banner = banner,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                    onClick = {
                        // 点提示条进消息页 → 视为已看过，清角标并记下签名，避免回前台又弹
                        scope.launch { runCatching { cn.mce.starlight.core.MessageCenter.markAllSeen() } }
                        dest = Dest.Notice
                        stack = emptyList()
                    },
                    onDismiss = { cn.mce.starlight.core.MessageCenter.dismissBanner() },
                )
            }
        }

        // v0.3.9：应用内提示条（替代系统 Toast）——左侧放应用图标「小胖星」，右侧文案，
        // 圆角 Surface 与主题一致；2.6s 后由 appToast 的 LaunchedEffect 自动清空。
        appToast?.let { msg ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
                        RoundedCornerShape(20.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_brand_star),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
            emergency = release.emergency,
            downloading = updateDownloading,
            progress = updateProgress,
            downloadError = updateError,
            onUpdate = { startUpdate(release) },
            onSnooze = {
                SettingsStore.snoozeUpdates(7)
                updateRelease = null
            },
            onDismiss = { updateRelease = null },
        )
    }
}

/**
 * 抽屉菜单项（v0.3.7 液态玻璃）：
 * 选中态不再用纯色 pill，而是半透明主题色渐变 + 白色边缘高光，渐变随 [sheen] 缓慢流动形成「液态玻璃」质感。
 * [sheen] 只在抽屉打开时由 MceApp 的无限动画驱动（0f 表示不动画）。
 */
@Composable
private fun DrawerItem(
    label: String,
    selected: Boolean,
    sheen: Float = 0f,
    modifier: Modifier = Modifier,
    /** v0.4.1：拖动重点框时由浮层统一绘制高亮，条目自身不再画，避免重影 */
    showHighlight: Boolean = selected,
    onClick: () -> Unit,
) {
    // v0.3.7 修订：尺寸对齐上一版 NavigationDrawerItem（56dp 高、胶囊形、左右各约 12dp 留白），
    // 只把「选中高亮」换成液态玻璃渐变，不再额外收窄每一行的宽度
    val shape = RoundedCornerShape(28.dp)
    val selectedFraction by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "drawerItemSelected",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .height(56.dp)
                .clip(shape)
                .then(modifier)
                .clickable(onClick = onClick),
    ) {
        if (selectedFraction > 0.01f) {
            // 玻璃质感高亮层：随 sheen 横向流动的渐变 + 1dp 白色边缘
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f * selectedFraction),
                                Color.White.copy(alpha = 0.12f * selectedFraction),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * selectedFraction),
                            ),
                            start = Offset(1600f * sheen - 300f, 0f),
                            end = Offset(1600f * sheen + 500f, 420f),
                        ),
                        shape = shape,
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.30f * selectedFraction),
                        shape = shape,
                    ),
            )
        }
        Text(
            text = label,
            // 与旧版 NavigationDrawerItem 一致：labelLarge + 左侧 16dp 内边距 + 垂直居中
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp),
        )
    }
}
