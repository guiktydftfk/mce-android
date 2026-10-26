# aMCE v0.3.2 代码审查 · 问题清单与修改顺序（待确认后再动手）

> **执行进度（用户已批准 6 批方案）**
> - 用户决策：缩略图方案 A；删除 WebView CookieManager 兼容层；**详情页本轮不动**（批 1 的「合并详情双请求」顺延）；release 开 R8；6 批做完后统一推送；验证可在测试帖 `tid=38058/38059/38060` 内进行。**需要发新帖的测试必须先经用户许可**（回复测试帖内的回复不需要）。
> - 批 1 请求与流量 ✅ 已构建 `0.3.3 (36)` 并装模拟器
>   - 偏差说明：① 详情页按要求未改（A6 双请求合并顺延）；② `UpdateClient` 仍保留 `?t=` 时间戳（去掉会回归"CDN 旧清单→永远已是最新"），只去掉重复预探测 + 镜像不新时继续下一个。
> - 批 4 逻辑正确性 ✅ **已按用户要求提前执行**（0.3.3/36，已装模拟器）
>   - 完成：A1/A2 成功判定统一（新 `data/DiscuzResponse.kt`）、A3 formhash 失效+Mutex、A4 刷新挂起、A5 排行榜重试、A14 拦截页判定收窄
>   - 顺延：A6/A8（详情页本轮不动）、A10 空态/错误态分离（并入批 5 列表状态机重构）
>   - 实机验证：见下方批 4 详情
> - 批 2 启动与主线程 ✅ 已构建 0.3.3/36 并装模拟器
>   - 完成：删 `MainActivity` 重复 `SettingsStore.init`、`ThumbCache` 懒加载、`DebugDump` 生产关停 + 后台清理旧文件、Coil 单拦截器 + host 精确匹配、Cookie header 内存缓存、**删除 WebView CookieManager 兼容层**、设置页背景图 IO 协程/固定文件名/滑杆落盘防抖
>   - 实测：冷启动 **2.16/2.17/2.27s**（批 1 为 3.17–3.34s，基线 3.15–3.82s，约 -30%）；feed/头像/缩略图（需 Cookie+Referer 的附件图）正常；进勋章页后 `debug_medals.html` mtime 不变、debug 文件数不增（生产不再落盘）
>   - 未实测：设置页"选背景图 + 滑杆持久化"——模拟器图库为空、`run-as` 写私有目录被 SELinux 拒绝，无法构造测试条件（仅代码审查+编译通过）
> - 批 3 解析 CPU ✅ 已构建 0.3.3/36 并装模拟器
>   - 完成：全库循环内 `Regex(...)` 提升为常量（Signin/Personal/Search/Social/GenericList/Ranklist/Task/Profile/UserProfile/ThreadDetail/PostActions/Session/Auth/Rate/ImageDiag/NativePage/UpdateClient）；搜索页单次 DOM 解析（`MobileForumParser.parse(doc, page)` 重载）；删掉 `li.html()` 全量序列化（收藏/提醒/勋章改用 `:has()` 选择器）；`hasNext` 去掉全页锚点扫描与恒真兜底
>   - 实测：24 个单测全绿；签到（今日排名 25/共 176 人）、搜帖子、我的收藏、消息提醒、我的勋章、我的好友、排行榜、道具中心、我的主题 全部正常；冷启动 1.57/1.59s
>   - 已知站点问题（非客户端）：**用户搜索** POST 后被 302 到 `home.php?mod=spacecp&ac=search`，该页服务端报 503「模版文件未找到: menubottom.htm」（已抓包确认）
> - 批 5 列表状态机 + 空态 + 用户搜索回退 ✅ 已构建 0.3.3/36 并装模拟器
>   - 完成：新增 `ui/PagedThreadList.kt`（请求代际号防旧结果覆盖、刷新/加载更多互斥、换 tab 同时重置列表与滚动位置、空态中性提示+下拉刷新、单一刷新指示器、可见窗口缩略图），`HomeFeedScreen`/`ThreadListScreen`/`MyThreadsScreen` 三处重复逻辑收敛；新增 `ui/StateBlocks.kt`（ErrorBlock/EmptyHint）；空列表不再当红色错误（我的回复/收藏/消息/任务/搜索统一中性提示）；11 处列表补稳定 key；**用户搜索电脑版模板回退**（`&mobile=no` + 桌面 UA，实测 503→200，解析 `ul.buddy > li` 得到 Brian/uid 2499；站点缺 `menubottom.htm` 也照样可用）
>   - **过程中发现并修复一个严重回归**：电脑版回退请求会把 `l2ND_2132_mobile=no` 写进 CookieJar，之后**所有页面都被服务端按电脑版模板渲染** → 移动版选择器全空（首页/版块/我的主题全部空列表）。修复：回退请求用 `isolateCookies=true`（响应 Set-Cookie 不落盘）+ 启动时 `CookieJar.removeMobileCookies()` 自愈。已验证搜索后首页/版块正常、Cookie 无残留
>   - 实测：首页/版块/我的主题正常；换导读 tab 列表+滚动一起重置；加载更多翻到 2024 年旧帖；用户搜索 Brian → UID 2499；26 个单测全绿（新增用户搜索桌面解析 2 个）
> - 批 6 冗余收敛 + R8 + 发布文档 ✅ 已构建 **0.3.3/36 release（R8）** 并装模拟器>   - 完成：新增 `data/Net.kt`（BASE/MOBILE_UA/DESKTOP_UA/absolutize/formhashOf/isBlockedPage/isLoginPage 统一，16 处 BASE、10 处 absolutize、5 处 formhash 提取全部收敛）；`SessionStore` 探活改走 `GenericPageClient`（旧实现自写 HTTP + 自动跟随会丢 Set-Cookie）；新增 `ProfileCommon` 供 `ProfileClient`/`UserProfileClient` 复用资料解析；删死代码（`ForumBoardScreen.kt`、`Dest.Compose`、`loadProfileInfo`、`lastProbe`、`collectImages(root)`、`document()`、`smilieidRegex`、`rewardUrl`、`Spacer15`）；**release 开启 R8**（`isMinifyEnabled`+`isShrinkResources`+`proguard-rules.pro`），构建脚本改为 `assembleRelease`，release 用仓库内 `keystore/debug.keystore`（与历史 debug 包同签名可覆盖升级，且不依赖沙箱无写权限的 `~/.android`）
>   - **R8 真机回归抓到并修复 2 个问题**：① 批 5 给通用列表/排行榜加的 `key` 在 URL 重复时会抛 `IllegalArgumentException: Key ... was already used`（道具中心直接崩）→ 改为 `itemsIndexed` + 带下标 key；② R8 后需确认 jsoup/Coil/Compose 反射链正常 → 首页/帖子/表情/搜索/个人页/排行榜/道具/设置/更新检查逐项实测通过，无崩溃
>   - 实测：APK **9.94MB → 1.71MB（-83%）**；冷启动 **1.15/1.21/1.33s**（批 5 为 1.57–1.59s，基线 3.15–3.82s）；26 个单测全绿；`versionName=0.3.3 / versionCode=36` 已安装
>   - 发布物料已就绪：README 更新至 0.3.3、`version.json` → 0.3.3/36、APK 已复制 `MCE-starlight.apk` / `release/MCE-latest.apk` / 桌面（**尚未 git push，等用户确认**）
> - 六批全部完成。**最终发布版按用户要求定名为 `0.3.2` / versionCode 36**（0.3.3 开发内容并入 0.3.2 发布说明），已重新构建、安装验证并更新 README/version.json/APK 副本。**已推送成功**：commit `ff78d80` → `origin/main`（推送方式：GitHub Desktop 自带 git + 原生 OpenSSH，见 `scripts/push-release.ps1`）。

> 审查时间：0.3.2（versionCode 35）构建通过后
> 方式：三路只读审查（data/ 层、ui/ 层、core+图片缓存链路）+ Lead 逐条核实，**未改动任何业务代码**
> 行号以当前工作区代码为准；标 ✅ 的是 Lead 已亲自读代码核实的，标 ❓ 的是需要一次真实抓页才能定性的
> 基线实测（MuMu 模拟器，0.3.2）：冷启动 `am start -W` 3.15–3.82s；启动时网络请求串行 4~8 个；首屏缩略图 = 8 次整页 GET

---

## A. 逻辑错误（用户可感知，按严重度）

| # | 问题 | 位置 | 结论 |
|---|---|---|---|
| A1 ✅ | **「回复成功」判定几乎恒为真**：`ok = !resp.contains("登录后") && !resp.contains("请输入内容")`，Discuz 真实失败文案（来路不正确/间隔过短/内容过短/需审核）都不含这两个词 → 失败也提示成功并清空输入框 | `data/PostActions.kt:221`、`ui/ThreadDetailScreen.kt:238-256` | 高 |
| A2 ✅ | **同族「假成功」**：`GenericPageClient.submit()` 只要 HTTP 200 就 `true`（签到/任务表单，`ui/NativePageScreen.kt:152`）；`TaskClient` 申请、`PostActions.favorite`（`:237-240` 连响应都不看）、`ProfileEditClient.kt:131`（`!resp.contains("超时")&&!resp.contains("错误")` 恒真 → 资料保存必"成功"）、`RateClient.kt:92`（只认"抱歉"，"还可评分 0 次/间隔过短"被判成功） | `GenericPageClient.kt:121-141`、`TaskClient.kt:60`、`PostActions.kt:237-240`、`ProfileEditClient.kt:131`、`RateClient.kt:92` | 高 |
| A3 ✅ | **formhash 登录/退出后不失效**：`FormhashStore.invalidate()` 全工程无调用点；`AuthClient.login/logout` 不碰它；缓存 10 分钟 TTL → 游客期缓存 → 登录 → 立即发帖/回帖/搜索用游客 hash → "请求来路不正确"（且被 A1/A2 掩盖）。另 `cached/cachedAt` 无 Mutex，并发 `get()` 各抓一次 `forum.php` | `data/FormhashStore.kt:8-23`、`data/AuthClient.kt:25-90` | 高 |
| A4 ✅ | **刷新协程会永久挂起**：`refresh()` 先 `listState.scrollToItem(0)` 再 `loadFirst()`；Loading/Error 分支里 LazyColumn 根本没组合，`scrollToItem` 不返回 → `loadFirst()` 永不执行、`refreshing` 卡 true、下拉/自动翻页永久禁用；错误态 FAB 仍可点 → 点了没反应。项目自己在 `SigninScreen.kt:76-78` 记过这个坑并用 `remember(rankType){LazyListState()}` 规避，这两处没规避 | `ui/HomeFeedScreen.kt:119-128`、`ui/ThreadListScreen.kt:123-132` | 高 |
| A5 ✅ | **排行榜页重试是空的**：`TextButton(onClick = { /* retry */ })`，且该页无下拉刷新/FAB → 出错后只能退出重进；`Spacer(Modifier.padding(4.dp))` 是零尺寸，间距也没生效 | `ui/RanklistScreen.kt:76-77` | 高 |
| A6 ✅ | **打开帖子固定请求 2 次**：`LaunchedEffect(tid)`（:83-93）和 `LaunchedEffect(tid, reloadKey)`（:104-107）首次组合同时跑，各发一次相同 GET + Jsoup 解析，慢的覆盖快的（loading 闪两次） | `ui/ThreadDetailScreen.kt:83-107` | 高 |
| A7 ✅ | **缩略图第 9 条起永远没图**：`allowThumbnail = index < 8` 是列表下标不是可见性；且并发去重 `if (!started) return positive[tid]` 在首个请求在飞时对第二个调用者必然返回 null 且不再重试 → 该帖永久无图 | `ui/HomeFeedScreen.kt:181`、`ui/ThreadListScreen.kt:183`、`data/ThreadImageCache.kt:37-40` | 高 |
| A8 ✅ | **详情「下一页」会跳页/替换整页**：`page += 1` 同步自增再异步 `load(page)`，双击跳页并并发两次请求；`state = Content(it)` 整体替换 → 已读楼层丢失、无法回上一页；失败整页变 Error（与 `:253` 注释"保留当前内容"矛盾） | `ui/ThreadDetailScreen.kt:172-183, 95-102` | 中高 |
| A9 ✅ | **loadMore 与 refresh 竞态**：捕获旧 `current` 后 `state = Content(current + next)`，刷新并发时用过期列表覆盖 + `page += 1` → 列表回退/重复/分页错位 | `ui/HomeFeedScreen.kt:101-117`、`ui/ThreadListScreen.kt:104-121`、`ui/PersonalScreens.kt:82-95` | 中 |
| A10 ✅ | **空列表被当成错误**：9 处（列表空 → 红色错误 + 重试，且 Error 分支没有下拉刷新，只能点重试，还会踩 A4） | `HomeFeedScreen.kt:95-96`、`ThreadListScreen.kt:98`、`PersonalScreens.kt:77/138/192/238`、`SearchScreen.kt:95/111`、`TaskScreen.kt:60`、`RanklistScreen.kt:58` | 中 |
| A11 ✅ | **切导读/筛选 tab 不重置滚动位置**：只 `state = Loading`，`listState` 沿用旧 index（新分类直接落到底部） | `HomeFeedScreen.kt:130-133`、`ThreadListScreen.kt:134-137` | 中 |
| A12 ✅ | **发帖页切「发帖/投票」会清空已填内容**：表单 `LaunchedEffect` key 含 `pollMode` 与 `SessionStore.isLoggedIn` → 切页签或冷启动探针把 isLoggedIn 由 false→true 都会重发 `loadComposeForm` 并清空 categories/selectedCategory（主题分类是必选项，用户要重选）；`pollChoices` 每次重组重算 | `ui/NewThreadScreen.kt:136-149, 126` | 中 |
| A13 ✅ | **pid 伪造会撞 key**：解析不到 pid 时用 `index + 1`，`blocks.mapNotNull{ parsePost(it, blocks.indexOf(it)) }`；桌面模板 `div.plc` id 是 `post_123` → 整页 pid 变成 1,2,3…，混有真实 pid 时 LazyColumn `key={pid}` 重复；楼层回复用伪 pid 拼 `reppost` 会引用错楼层 | `data/ThreadDetailParser.kt:43,65` | 中 |
| A14 ✅ | **拦截页判定偏宽**：`isBlockedPage` 用子串 `"频繁"/"请稍后"/"seccodeverify"`；真实抓到的发帖页 fixture（`app/src/test/resources/.../newthread_fid_2.html`）本身就含"请稍后" → 若该判定用到普通页会误抛"访问过于频繁"。目前只在 `HttpForumRepository.fetch` 用，风险中低 | `data/GenericPageClient.kt:234-236`、`HttpForumRepository.kt:51` | 中低 |
| A15 ✅ | **登录态探活绕开统一网络栈**：`SessionStore` 自带 `HttpURLConnection`（`instanceFollowRedirects=true`），探活响应里的 Set-Cookie 全丢；且用 `profileHtml.contains("spacecp")` 当登录证据偏弱 → 与历史 bug（登录了却是游客）同类风险；`loadProfileInfo()` 是死代码 | `core/SessionStore.kt:41-54, 89-101, 120-135` | 中 |
| A16 ✅ | **Coil 图片请求会带 Cookie 发给站外域**：`request.url.host.contains("mce.msi.cn")` → `mce.msi.cn.evil.com` 也命中；同一拦截器 `addInterceptor` + `addNetworkInterceptor` 注册两遍，每个图片请求执行两次 | `MceApplication.kt:31, 50-55` | 中 |
| A17 ✅ | **ThumbCache 自称 LRU 实为 FIFO**：`LinkedHashMap(loaded)` 未开 accessOrder；`get()` 里 3 次无意义查找、`put()` 里 `m[tid]` no-op（注释说的 touch 无效）；每次 put 把 ≤200 条全量拼串写 SharedPreferences；map 无锁、多个 IO 协程并发读写 | `data/ThumbCache.kt:26-47` | 中 |
| A18 ✅ | **CookieJar 无 domain/过期/TTL**：Expires/Max-Age/Domain/Path 全丢，`header(url)` 的 url 参数完全没用（任何 host 都返回全部 Cookie）；`hasAuth()` 只看名字含 auth/saltkey（空值也算） | `core/CookieJar.kt:26-58` | 中低 |
| A19 ✅ | **死代码/失效逻辑**：`MceApp` 的 `Dest.Compose` 从无赋值 → `when(dest)` 里的分支不可达；`ForumBoardScreen.kt` 整文件无调用方；`AuthClient.kt:68` 同一条件写两遍 `contains("action=logout") \|\| contains("action=logout")`；`MceApp.kt:41` BASE 未使用；`SmileyClient.kt:86`、`ThreadDetailParser.collectImages()`、`GenericPageClient.document()`、`ForumRepository.rewardUrl`、`WebCookies.hasAuthCookie()`+`CookieJar.hasAuth()`+`SessionStore.lastProbe` 只写不读 | 多处 | 低（但误导性强） |

## B. 性能问题（软件层速度）

| # | 问题 | 位置 | 收益 |
|---|---|---|---|
| B1 ✅ | **冷启动首帧前主线程同步 IO**：`MceApplication.onCreate` 里 `SettingsStore.init`+`CookieJar.init`+`ThumbCache.init`（两份 SharedPreferences XML 全量解析，ThumbCache 还要 `split('\n')` 建 200 条 map）；`MainActivity.kt:15` 又重复 `SettingsStore.init(this)`（每次 Activity 创建/旋转都重读） | `MceApplication.kt:20-26`、`MainActivity.kt:15`、`ThumbCache.kt:14-32` | 高 |
| B2 ✅ | **启动网络串行 4~8 个请求**：同一个协程里 `probeLoginState()` → `SigninClient.load()`（为判断"今天签没签到"却额外抓了排行榜整页！）→ `checkUpdate()`；`UpdateClient.checkDetailed()` 先对 4 个镜像逐个 `httpGet` 预探测，再调 `check()` 把同样 URL 再抓一遍（最坏 8 次串行），且每个请求都加 `?t=时间戳` 让 CDN 永久失效；`code <= current` 在第一个镜像就返回"最新"，gh-proxy 缓存旧文件时后面镜像的新版永远看不到 | `ui/MceApp.kt:119-133`、`data/UpdateClient.kt:76-101` | 高 |
| B3 ✅ | **缩略图 = 每张卡片抓一次完整帖子页并全楼层解析**：只为拿一个图片 URL，`Semaphore(2)` 串行分批 → 首屏 8 次整页 GET（约 1–2.4MB）+ 8 次全楼层 Jsoup 解析；这也是论坛频率限制的触发源 | `data/ThreadImageCache.kt:43-56`、`ui/FeedCard.kt:51-53` | 高 |
| B4 ✅ | **打开一个帖子累计 3~5 次整页下载**：缩略图 1 次 + 详情 2 次（A6）+ 回帖后重新 GET 1~2 次（:243-256，失败还 delay 1200ms 再试） | 同上 + `ThreadDetailScreen.kt:243-256` | 高（修 A6/B3 后自然下降） |
| B5 ✅ | **Cookie 固定开销**：`CookieJar.all()` 每次 `getAll()` 复制 + filter + associate 三次拷贝；`GenericPageClient` 每个 3xx 跳再 `WebCookies.cookieHeader()` 一次；每个响应 `CookieManager.getInstance().setCookie(...)+flush()`（同步落盘） | `CookieJar.kt:32-37`、`GenericPageClient.kt:153-154,199,219-226`、`WebCookies.kt:28-34` | 中高 |
| B6 ✅ | **循环内编译正则**：`SigninClient.parseRankEntries` 每行 4~5 个 `Regex()`（一页 200 行 ≈ 1000 次编译，最多 20 页）；`PersonalClient`(37,40,53,71,76-77)、`SearchClient`(89,100)、`SocialClient`(86,88)、`GenericListClient`(63,64)、`RanklistClient`(39)、`TaskClient`(35,42)、`UserProfileClient`(65)、`ThreadDetailParser`(45,59,79,124-125)、`MobileForumParser`(45,72)、`PostActions`(72-73,133-134,246)、`SessionStore`(77,83,93,99)、`AuthClient`(82,95-98) | 多文件 | 中高（改常量即可，零风险） |
| B7 ✅ | **搜索页固定解析两遍**：`Jsoup.parse(html)` 后又 `MobileForumParser.parse(html,...)`（内部再 parse），`doc` 只在兜底分支用；用户搜索还对每个 `<a>` 做 `parents()` 祖先回溯 + 子树选择 | `data/SearchClient.kt:84-85,104` | 中 |
| B8 ✅ | **整页 li 遍历 + 每个 li 序列化 HTML**：`doc.select("li").mapNotNull { val html = li.html() ... }`、`doc.select("li, div").forEach`（并集重复命中）、勋章页二次扫描 | `PersonalClient.kt:49-51,74`、`GenericListClient.kt:33-39`、`SocialClient.kt:25-36` | 中 |
| B9 ✅ | **DebugDump 生产路径同步落盘**：每次进勋章/好友/他人主页/图片自检都 `writeText` 一份 200–500KB 原始 HTML 到 `filesDir`，永不清理（`DebugDump.kt:19`） | `SocialClient.kt:21,55,59`、`UserProfileClient.kt:36`、`ImageDiagClient.kt:42,71` | 中 |
| B10 ✅ | **设置页**：背景图 `input.copyTo` 在 ActivityResult 主线程回调里拷整张原图（ANR 风险）；文件名带时间戳、旧文件从不删除（`background_*.jpg` 只增不减，清除背景也只置空路径）；透明度/模糊滑杆 `onValueChange` 每帧写 SharedPreferences | `SettingsScreen.kt:57-69,147-161`、`SettingsStore.kt:82-90` | 中 |
| B11 ✅ | **列表整表替换 + 缺稳定 key**：3 处 `state = Content(merged)` 每次 loadMore 全量替换 → 整列表重组；`items(count)` 等 11 处缺 key（`PersonalScreens.kt:113,151,204,250`、`RanklistScreen.kt:124`、`SocialScreens.kt:73,135`、`NativePageScreen.kt:102`、`ProfileEditScreen.kt:209`、`UserProfileScreen.kt:236`、`ProfileScreen.kt:255`） | 多文件 | 中 |
| B12 ✅ | **资料编辑页每敲一个字重建全表单**：所有字段共用一个 `mutableStateMapOf`，每个 `OutlinedTextField` 直接读 `values[field.name]` → 任意写使所有读方失效 | `ProfileEditScreen.kt:67,76,111,...` | 中 |
| B13 ✅ | **hasNext 死计算**：`MobileForumParser.hasNext` 每次整页扫锚点多行多列 + `:76` 兜底"有分页容器就 true"，而两个列表页只用 `threads.isNotEmpty()`，`ForumPage.hasNextPage` 从未被读取 | `MobileForumParser.kt:68-77`、`ThreadListScreen.kt:97`、`HomeFeedScreen.kt:94` | 中低 |
| B14 ✅ | **release 未开 R8**（`isMinifyEnabled=false`，无 proguard 规则）→ 无裁剪/内联，APK 体积与类加载都吃亏 | `app/build.gradle.kts:37` | 中（需真机回归） |
| B15 ✅ | **缩略图导致卡片高度跳动**：`FeedCard` 里 `remember(thread.id)` 随 item 滑出销毁 → 滑回时先 null 再出图，高度差 170dp；`allowThumbnail=false` 的卡片也永远没有图，导致列表高度参差 | `FeedCard.kt:49-53,113-123` | 中（体验） |

## C. 冗余代码

| # | 冗余 | 位置 |
|---|---|---|
| C1 ✅ | `BASE="https://mce.msi.cn"` 硬编码 **14 份**；`MOBILE_UA` 逐字 **4~5 份**；`absolutize()` **10 份**；formhash 提取 **4 套**；`looksBlocked` 与 `isBlockedPage` 逐字重复 | data/ 多文件 |
| C2 ✅ | 死代码：`ForumBoardScreen.kt` 整文件；`MceApp` `Dest.Compose` 分支；`SessionStore.loadProfileInfo()`；`ThreadDetailParser.collectImages()`；`GenericPageClient.document()`；`ForumRepository.rewardUrl`；`SmileyClient.smilieidRegex`；`WebCookies.hasAuthCookie()`+`CookieJar.hasAuth()`+`SessionStore.lastProbe`；`AuthClient.kt:68` 重复条件；`SocialScreens.kt:186 Spacer15`；`MceApp.kt:41 BASE`；`Diagnostics.kt:34` `SDK_INT>=23` 恒真；`HomeFeedScreen`/`ThreadListScreen` 各约 10 个未用 import |
| C3 ✅ | 5 套手写 HTTP 栈：`GenericPageClient`（唯一正确实现，逐跳存 Cookie）、`SessionStore.fetch`、`UpdateClient.httpGet/download`、`PostActions.uploadImage`、`ImageDiagClient` |
| C4 ✅ | 列表页骨架三份复制（`HomeFeedScreen`/`ThreadListScreen`/`PersonalScreens`）；loading/error/retry 骨架 ≥5 份；`topInset + 魔法数` 13 处（46/52/56/60/64/70/104/120.dp）；双刷新指示器（PullToRefresh + 顶部 LinearProgressIndicator，且 `topInset+46dp` 与分类条 `+52dp` 只差 6dp 会盖字） |
| C5 ✅ | 功能重叠：`SocialClient` vs `GenericListClient`；`ProfileClient:35-50` 与 `UserProfileClient:45-62` 几乎逐行相同；`HttpForumRepository.baseHost` 从无调用点传值；`PostActions` 用 `HttpForumRepository.MOBILE_UA`（语义错位） |

---

## D. 修改顺序清单（建议 6 批，按「收益/风险」排序）

> 原则：先干掉「重复请求 + 假成功」这类用户直接感知的问题，再动启动/主线程，再做 CPU 与结构收敛；每批都要「构建 → 单测 → 模拟器实测 → 报告」，仍不 push。

### 批 1 · 请求数与流量（收益最大，风险低，约 6 个文件）—— ✅ 已完成（0.3.3/36）
1. ~~**A6** 合并详情页两个 `LaunchedEffect`~~ → **按要求顺延**（本轮不动详情页）。
2. ✅ **A7 + B3/B15** 缩略图方案 A：新增 `ui/ThumbnailWindow.kt`（`rememberThumbnailWindow`：可见 + 后 3 条，`derivedStateOf` 只在窗口变化时重组），`HomeFeedScreen`/`ThreadListScreen`/`MyThreadsScreen` 的 `index < 8` 全部替换；首图提取新增 `ThreadDetailParser.firstImageUrl()`（只解析一次 DOM 取第一张正文图，不再跑全楼层解析）。
3. ✅ **A7** `ThreadImageCache` 重写：并发单飞改 `CompletableDeferred`（第二个调用者等待结果而不是拿 null）、负缓存写时清过期 + 限容、正缓存改访问序 LRU（512）、频率限制判定复用 `GenericPageClient.isBlockedPage`、登录/退出时 `clear()`（`AuthClient` 登录成功与 logout 调用）。
4. ✅ **B2** `UpdateClient`：删掉"先逐个镜像预探测、再 `check()` 重抓一遍"的重复请求（启动最坏 8→4 次串行），某个镜像清单不新时继续试下一个镜像（修 gh-proxy 缓存旧文件导致"永远已是最新"）；`check()` 保留为兼容入口。**保留 `?t=` 时间戳**（防 CDN 旧清单回归）。
5. ✅ **B2** `MceApp` 启动三条请求改并行（`coroutineScope { launch ×3 }`），签到提醒改用新增 `SigninClient.loadStateOnly()`（只查状态，不拉 200 条排行榜，2 请求→1）。
- 实测：模拟器已装 0.3.3(36)；首屏缩略图正常、滚动到第 9 条之后**也有图**（旧实现永远没图，截图 `probe/b1-scroll.png`）；签到页 `共 162 人` 正常；手动检查更新无异常；打开帖子正常；单元测试 14 个全绿（新增 2 个 `firstImageUrl` 用例：与全量解析首图一致、忽略头像/占位图）；冷启动 3.17–3.34s（基线 3.15–3.82s，首帧主因在批 2）。
- 预期：首屏缩略图请求 8→1~2；启动请求 6~10→3~4；弱网下不再触发"频率限制→全部缩略图空 10 分钟"。

### 批 2 · 启动与主线程（首帧时间，约 5 个文件）—— ✅ 已完成（0.3.3/36）
6. ✅ **B1** 删 `MainActivity` 重复 `SettingsStore.init`；`ThumbCache.init` 改懒加载（只存 prefs 引用，200 条记录解析推迟到首个 get/put）；`DebugDump.init` 的旧文件清理挪到后台 daemon 线程。
7. ✅ **B9** `DebugDump` 加显式开关 `enabled = false`（本项目一直发 debug 包，用 `BuildConfig.DEBUG` 判断会一直在生产落盘）；`dump()` 关停后直接 return；启动后台清理 7 天前的 `debug_*.html`。已验证进勋章页不再写盘。
8. ✅ **A16 + B5** Coil：host 改为精确匹配（`== "mce.msi.cn"` 或 endswith `.mce.msi.cn`，避免 Cookie 发给 `mce.msi.cn.evil.com`）；只保留 `addNetworkInterceptor`（原来 application+network 注册两遍）；`CookieJar.header()` 增加内存缓存（save/clear 失效 + 5 分钟兜底 TTL），每请求不再 `getAll()+filter+associate`。
9. ✅ **B5** 删除 WebView `CookieManager` 兼容层（`saveFromHeaders` 镜像 + `flush` + `legacyCookie` 回退全部移除），只保留 `CookieJar`；`GenericPageClient` 移除未用 import。副作用：极老版本升级用户可能需要重登一次（已按用户决策接受）。
10. ✅ **B10** 设置页：背景图固定文件名 `background.jpg` 覆盖写 + 清理历史 `background_<时间戳>.jpg` + `copyTo` 挪到 IO 协程；透明度/模糊滑杆 `onValueChange` 只改内存 state、`onValueChangeFinished` 才落盘（旧实现一次拖动 60+ 次整份 prefs 重写）。
- 实测：冷启动 **2.16 / 2.17 / 2.27s**（批 1 为 3.17–3.34s，基线 3.15–3.82s）；feed/头像/缩略图（附件图需 Cookie+Referer）正常；勋章页打开后 debug 文件 mtime 不变、数量不增。
- 未实测：设置页选背景图 + 滑杆持久化（模拟器图库为空、`run-as` 写私有目录被 SELinux 拒绝，无法构造测试条件）。

### 批 3 · 解析 CPU（改常量为主，风险低，约 12 个文件）—— ✅ 已完成（0.3.3/36）
11. ✅ **B6** 全库循环内 `Regex(...)` 提升为文件级常量：`SigninClient`（排行榜每行 4~5 个 → 0）、`PersonalClient`、`SearchClient`、`SocialClient`、`GenericListClient`、`RanklistClient`、`TaskClient`、`ProfileClient`/`UserProfileClient`（动态 label 正则做 Map 缓存）、`ThreadDetailParser`、`PostActions`、`SessionStore`、`AuthClient`、`RateClient`、`ImageDiagClient`、`NativePageScreen`、`UpdateClient`。
12. ✅ **B7** `SearchClient` 单次解析：`MobileForumParser` 新增 `parse(doc, page)` 重载（对外保留 `parse(html, baseUrl, page)`）；用户搜索改为 `li:has(img[src*=avatar.php])` 等容器选择器一次筛出，不再对每个锚点 `parents()` 回溯全部祖先（兜底路径也限制到最近 4 层）。
13. ✅ **B8** 删除所有 `li.html()` 全量序列化：`PersonalClient` 收藏改 `li:has(a[href*=favid=])`、提醒改 `a[href*=ptid]` 兜底；`SocialClient` 勋章改 `:has(img[src*=medal])` 单次选择；`GenericListClient` 改 `li:has(a[href])`；顺带删掉 `items.map { it }` 等空转。
14. ✅ **B13** `MobileForumParser.hasNext` 只扫 `a[href*=page=]` 并判断是否 > 当前页，去掉"遍历全页锚点 + 每锚点编译正则 + 有 .pg 容器就恒真"两条浪费。
- 实测：24 个单测全绿；签到（今日排名 25 / 共 176 人）、搜帖子、我的收藏、消息提醒、我的勋章、我的好友、排行榜、道具中心、我的主题 全部解析正常；冷启动 1.57 / 1.59s。
- 已知站点问题（非客户端引入）：**用户搜索**被 302 到 `home.php?mod=spacecp&ac=search`，该页服务端 503「模版文件未找到: menubottom.htm」（抓包确认）。

### 批 4 · 逻辑正确性（需要真机写入回归，约 8 个文件）
15. **A1/A2** 统一"成功判定"：`PostActions.reply`、`favorite`、`RateClient`、`ProfileEditClient`、`TaskClient`、`GenericPageClient.submit` 改为「显式失败关键词表 + 关键操作二次 GET 校验」；`submit` 返回响应体而不是 Boolean。
16. **A3** `FormhashStore`：`AuthClient.login/logout` 调 `invalidate()`；`get()` 加 `Mutex` 单飞。
17. **A4** 刷新协程：改成"先 `loadFirst()` 再回顶"（`requestScrollToItem`，不挂起）；Error/Loading 态 FAB 不再卡死。
18. **A5** 排行榜重试接上 `load()`；`Spacer(Modifier.padding(4.dp))` 改 `height(4.dp)`；底部加 `DraggableRefreshFab` 兜底。
19. **A10** 空列表 ↔ 错误的区分：空列表给"暂无内容"空态（带下拉刷新），只有真异常才进 Error。→ **并入批 5**（列表状态机重构时统一处理）
20. **A14** `isBlockedPage` 收窄：仅在"确实是 Discuz 提示/错误页"（messagetext/alert_error/showError）时判定，正常页面里的"请稍后"不再误报。
- ✅ 实机验证（0.3.3/36，测试帖 `tid=38060`）：
  - 回复成功：真实回复 → toast「回复成功」+ 新楼层出现 + 输入框清空（`probe/b4-reply-ok.png`）
  - 回复失败：连发两条触发频控 → toast 显示服务端原文「抱歉，您两次发表间隔少于 15 秒，请稍候再发表」，**输入内容保留、不再假成功**（`probe/b4-reply-fail.png`）
  - 收藏：真实收藏 → 「已收藏」
  - 资料保存成功：抓包确认请求体 + 响应 `parent.show_success('')` → 判定成功（之前的"保存失败"是旧提示残留 + 按钮被提示文案顶下去导致点空，已确认）
  - 资料保存失败：QQ 填 `abc` → 「保存失败：字段「yahoo」校验未通过」（服务端返回 `show_error("yahoo","")`，已兼容双引号/多参数写法）
  - 排行榜页正常渲染 + FAB 兜底；单元测试 **24 个全绿**（新增 `DiscuzResponseTest` 10 个用例，含真实抓包样本）
- 预期：不再「假成功」、错误可恢复、换账号后发帖不再报"来路不正确"。

### 批 5 · 列表状态机收敛（中风险，收益稳，约 6 个文件）—— ✅ 已完成（0.3.3/36）
21. ✅ **A9 + A11 + C4** 新增 `ui/PagedThreadList.kt`：统一 首页/版块/我的主题 的列表状态（请求代际号作废旧请求、刷新与加载更多互斥、换 `resetKey` 同时重置列表与 `LazyListState`、空态中性提示 + 下拉刷新、只保留 PullToRefresh 单一指示器、缩略图可见窗口内置）；三个 Screen 从 ~500 行重复逻辑降为薄壳。
22. ✅ **B11** 全量补稳定 key：我的回复/收藏/消息、任务、排行榜、勋章、好友、通用列表、用户主页最近主题、个人资料入口等。
23. ✅（可选）详情页翻页改追加——按要求**未改**（详情页本轮不动）。
24. ✅ **A10** 空列表 ↔ 错误分离：新增 `ui/StateBlocks.kt`（`ErrorBlock` / `EmptyHint`），我的回复/收藏/消息/任务/搜索统一"中性空态"，搜索空结果不再用红色错误色。
25. ✅（用户追加）**用户搜索电脑版模板回退**：手机模板 503 时自动用 `mobile=no` + 桌面 UA 重取并解析 `ul.buddy > li`（`SearchClient.searchUsersDesktop/parseDesktopUsers`）；为此给 `GenericPageClient.get` 增加 `userAgent` 与 `isolateCookies` 参数。
   - **踩坑修复**：`mobile=no` 会被服务端写入 `l2ND_2132_mobile` Cookie → 后续所有页面按电脑版渲染、移动版解析全空。已用 `isolateCookies` + 启动自愈 `removeMobileCookies()` 修掉，并实测搜索后列表仍正常。
- 实测：首页/版块/我的主题、换 tab 重置、自动加载更多、用户搜索、空态提示均正常；26 个单测全绿。

### 批 6 · 冗余收敛与工程化（低风险，收尾，约 15 个文件）—— ✅ 已完成（0.3.3/36 R8）
24. ✅ **C1/C3/A15** 新增 `data/Net.kt`：`BASE`、`MOBILE_UA`、`DESKTOP_UA`、`absolutize()`、`formhashOf()`、`isBlockedPage/isLoginPage`；16 个 data 文件的 `BASE`、10 处 `absolutize()`、5 处 formhash 提取全部改为引用 `Net`；`SessionStore` 探活改走 `GenericPageClient`（旧实现自写 HTTP + 自动跟随会丢 Set-Cookie）。
25. ✅ **C2/A19** 删除死代码：`ui/ForumBoardScreen.kt`（整文件）、`Dest.Compose` 枚举与不可达分支、`SessionStore.loadProfileInfo/lastProbe`、`GenericPageClient.document()`、`ThreadDetailParser.collectImages(root)`、`SmileyClient.smilieidRegex`、`ForumRepository.rewardUrl`、`SocialScreens.Spacer15`。
26. ✅ **C5** 新增 `data/ProfileCommon.kt`，`ProfileClient`/`UserProfileClient` 共用昵称/用户组/统计数据/资料字段解析。
27. ✅ **B14** release 开 `isMinifyEnabled=true` + `isShrinkResources=true` + `app/proguard-rules.pro`（jsoup keep、行号保留）；构建脚本改 `assembleRelease`，签名用仓库内 `keystore/debug.keystore`（同签名可覆盖升级）。APK **9.94MB → 1.71MB**，冷启动 **1.15–1.33s**。
28. ✅ 回归：26 个单测全绿；R8 包逐项实测（首页/帖子/表情/搜索/收藏/消息/勋章/好友/排行榜/道具/设置/更新检查/登录态）。**过程中修复批 5 引入的重复 key 崩溃**（道具中心 URL 重复 → `itemsIndexed` + 下标 key）。

24. **C1/C3/A15** 新增 `data/Net.kt`：`BASE`、`MOBILE_UA`、`absolutize()`、`FORMHASH_RE`、`isBlocked/isLoginPage`；`SessionStore` 探活改走 `GenericPageClient`；5 套手写 HTTP 收敛成 1 套（上传/下载/图片自检各自保留必要特化）。
25. **C2/A19** 删除死代码清单（ForumBoardScreen、Dest.Compose、loadProfileInfo、collectImages、document、hasAuth*、lastProbe、未用 import 等）。
26. **C5** `UserProfileClient` 复用 `ProfileClient` 的字段解析；`SocialClient`/`GenericListClient` 二次评估合并。
27. **B14** release 开 `isMinifyEnabled = true` + 必要 proguard（jsoup/Coil/OkHttp consumer 规则已有，保留 jsoup 反射相关），真机回归一次。
28. 每批增加回归用例：成功判定（关键词表）、分页列表状态机、`parseRankEntries`/`SmileyClient` 已有用例保留；把 `:app:testDebugUnitTest` 的环境问题（`GradleWorkerMain`）记录在文档里继续用 JUnitCore 手动跑。

---

## E. 需要你先拍板的 6 个决策

1. **缩略图方案**：A（可见窗口 + 每卡一次轻量首图请求，改动小、稳）还是 B（列表页 HTML 里直接取首图 = 0 额外请求，但要先抓一页验证列表 HTML 是否含配图；历史上 0.0.7 记录"列表页 HTML 无缩略图"）→ 建议先 A，B 作为后续实验。
2. **WebView CookieManager 兼容层**：删掉（可能让极老版本升级用户首次需要重登一次）还是保留（继续每响应 flush 的开销）。
3. **详情页分页**：是否改成「追加楼层」交互（体验更好但改动大、需回归翻页/回帖定位）；还是先只修双请求与竞态。
4. **release 是否开 R8**：收益是体积/启动，风险是需要真机回归一次（Compose/Coil/OkHttp 有 consumer 规则，jsoup 需确认）。
5. **发版节奏**：建议拆两个版本 —— `0.3.3` = 批 1~3（纯速度优化，风险低），`0.3.4` = 批 4~6（逻辑修复 + 结构收敛，需更多回归）；每版仍然"构建不 push"。
6. **验证方式**：A1/A2 的"成功判定"修复需要真实写入验证（回复一条/给测试帖评分/保存一次资料）。可接受在 `tid=38058/38059/38060` 这些已有测试帖上验证吗？

> 确认后我按批执行：每批先给 diff 说明 → 构建 → 单测 → 模拟器实测 → 报告「已实测/未完成」，不会 push。
