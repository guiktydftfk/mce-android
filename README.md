# MCE星光班 · Android 客户端

mce.msi.cn（Discuz! X3.5 论坛）的原生 Android 客户端：Kotlin + Jetpack Compose 外壳，
内容页逐步从 WebView 替换为原生实现（绞杀者模式）。

## 快速开始

```powershell
# 一键构建（JDK / Android SDK / Gradle 都在 <工作区>\android-dev）
powershell -ExecutionPolicy Bypass -File .\scripts\build-apk.ps1 -Bump -ToDesktop
```

产物：`dist/MCE-starlight-v<版本>.apk`，`-ToDesktop` 会同时复制到桌面。

## 结构

```
app/src/main/java/cn/mce/starlight/
├── MainActivity.kt          Compose 入口（edge-to-edge）
├── core/
│   ├── SiteScope.kt         站内域名判定
│   ├── WebCookies.kt        WebView Cookie ↔ 原生请求桥接
│   └── SessionStore.kt      登录态与当前用户
├── data/
│   ├── ForumRepository.kt   模型 + 协议
│   ├── Boards.kt            版块种子数据
│   ├── MobileForumParser.kt 列表页解析（forumdisplay / guide）
│   ├── ThreadDetailParser.kt 详情页解析（楼层/作者/图片）
│   └── HttpForumRepository.kt 抓取 + 解析实现
├── ui/
│   ├── MceApp.kt            抽屉 + 导航栈
│   ├── ForumBoardScreen.kt  版块列表
│   ├── ThreadListScreen.kt  帖子列表（沉浸式 + 拖动刷新）
│   ├── ThreadDetailScreen.kt 帖子详情
│   └── Chrome.kt            悬浮顶栏 + 可拖动刷新按钮
└── web/
    └── WebContentView.kt    仅用于登录/签到/插件页
```

## 测试

`app/src/test/resources` 里是真实抓取的页面样本（forumdisplay / guide / viewthread），
`ParserTest` 用 Jsoup 在 JVM 上验证解析器选择器。

```powershell
# 在 CI 或本机（需可运行的 Gradle 测试进程）
gradle :app:testDebugUnitTest
```

## 说明

- 站点未启用「掌上论坛」JSON API（实测 `module_not_exists`），因此数据层基于移动版 HTML 解析
- 登录、发帖、签到等含 formhash 动态表单的页面保留 WebView

---

## 更新记录

## 0.1.5 · 板块补全 / 分组抽屉 / 自定义背景

- 新增：**补全全部板块**（4 个分组共 35 个板块：全国交流 3 个、项目和活动 13 个、城市板块 17 个、星光大厅 4 个）
- 新增：侧边栏按分组收纳，**点击分组展开/收起**（城市板块统一归入「城市板块」大类）
- 新增：设置 → **自定义背景**：上传本地图片作为全局背景，支持**透明度**与**模糊度**滑块调节，可一键清除
- 修复：**刷新不再跳回顶部**（刷新前后保持滚动位置）
- 修复：附件图片加载（图片请求补上 Cookie + Referer + 移动 UA）


## 0.1.4 · 签到统计/图片/BBCode 修复

- 修复：签到页「今日排名 / 连续签到 / 累计签到」数值不显示 → 改为从整页文本按标签提取数字
- 修复：BBCode 工具栏逻辑错误（原先只是把标签追加到末尾）→ 改为**按光标位置插入**，有选区则包裹选区，光标自动落在标签中间
- 修复：部分帖子图片不显示 → 附件「图标 + 链接」形式现在按图标识别为图片附件
- 变更：版本号规则：**每 10 进 1**（0.0.9 → 0.1.0，0.1.9 → 0.2.0；0.0.14 即 0.1.4）
- 变更：更新记录并入 README（原 CHANGELOG.md 内容迁移至此，文件删除）

## 0.0.8 · 刷新修复 + 图片修复 + 发帖/回帖

- 修复：下拉刷新失效（沉浸式改版时丢失 PullToRefreshBox）→ 恢复下拉刷新
- 修复：右下角刷新按钮失效（覆盖层 fillMaxSize 挡住触摸）→ 按钮改为只占自身尺寸并重新实现拖动 + 点击
- 修复：帖子图片完全不显示（附件是链接 `mod=attachment`、图片是懒加载属性而非 `src`）→ 解析 `file/zoomfile/data-src` 与附件链接
- 新增：Coil 图片加载注入 Cookie，附件图片需要登录态也能加载
- 新增：**回帖**（详情页底部输入框，原生 POST replysubmit）
- 新增：**发帖**（列表页右上角「发帖」，原生 POST topicsubmit）
- 改进：签到数值解析兼容「今日排名 第 3 / 今日排名：3 / 3 今日排名」等形态

## 0.0.7 · 修复主题 / 签到 / 任务 / 列表配图

- 修复：深色模式不生效（页面直接画在白色窗口背景上）→ 用 Surface 铺满主题背景，状态栏图标同步反色
- 修复：签到页只剩一张图 → 改为**专用签到解析**（今日排名 / 连续签到 / 累计签到 / 签到按钮，原生提交）
- 修复：任务中心把页面拍平成链接列表 → 改为**专用任务解析**（标题 + 描述 + 状态 + 立即申请按钮成组）
- 新增：帖子列表显示**帖子配图**（列表页 HTML 无缩略图，按需抓取首图并内存缓存）

## 0.0.6 · 去网页化 + 设置与明暗主题

- 变更：**打开应用直接进入「综合交流」板块**（fid=2，之前误标为私密版块）
- 新增：未登录时**弹出原生登录框**（直接 POST Discuz 登录表单，无 WebView）
- 新增：侧边栏「设置」页：深色模式开关（持久化保存）、账号登录/退出、关于信息
- 新增：全局明暗主题（浅色/深色配色 + 状态栏图标自动反色）
- 新增：原生**搜索页**（输入关键词，POST 搜索并解析结果列表）
- 变更：**签到 / 任务中心改为原生页面**（解析页面文本、链接、表单并原生提交），不再跳手机版网页
- 移除：**所有网页端入口**——WebView 渲染层、每个页面的「网页版」按钮全部删除
- 移除：不再使用的 SiteScope / FeatureFlags

## 0.0.5 · 登录态打通

- 新增：WebView 与原生请求共享 Cookie（登录后原生列表可读私密版块）
- 新增：登录成功后自动关闭网页层回到原生界面（轮询「退出链接」判断，避免访客 Cookie 误判）
- 新增：「我的」页展示头像、昵称、登录状态
- 修复：用户名/头像误取「列表中第一个帖子的作者」→ 改为从个人资料页解析当前用户


## 0.0.4 · 体验改造

- 新增：左侧抽屉导航（论坛 / 发现 / 我的 + 登录、签到、任务、常用表单、搜索），左上角 ☰ 展开
- 新增：首页沉浸式浏览（全屏内容、悬浮渐变顶栏、内容从顶栏下滑过）
- 新增：进入列表自动加载一次
- 新增：右下角可拖动刷新按钮（可全屏拖动，点击刷新）
- 新增：帖子列表显示作者头像；帖子详情显示正文图片（最多 4 张）
- 修复：帖子详情解析器（作者、时间、楼层；多选择器回退，避免「没有解析到楼层内容」）

## 0.0.3 · 全原生内容页

- 新增：原生版块列表（全国交流 / 星光大厅）
- 新增：原生帖子列表（版块内 + 看帖中心共用）
- 新增：原生帖子详情（楼层、作者、时间、正文）
- 说明：登录、签到、任务等含动态表单的页面保留 WebView

## 0.0.2 · 原生帖子列表

- 新增：原生 Compose 帖子列表（通知公告）
- 新增：Jsoup 解析 Discuz 移动版模板 HTML
- 新增：下拉刷新、加载失败重试、解析为空自动提示回退网页
- 保留：帖子详情仍用 WebView

## 0.0.1 · 原生外壳

- 新增：Kotlin + Jetpack Compose 外壳，底部导航（论坛 / 发现 / 我的）
- 新增：WebView 加载论坛，标题跟随网页标题
- 新增：站内域名策略（站外链接交给系统浏览器）
- 新增：返回键处理（先网页后退，再退出页面）
- 新增：SessionStore / ForumRepository / FeatureFlags 架构骨架

---

## 架构说明

- 绞杀者模式：内容页逐屏从 WebView 替换为原生 Compose
- 数据层：`HttpForumRepository` 携带移动 UA 抓取 Discuz 触屏模板，Jsoup 解析
- 会话层：WebView 的 Cookie 由 `CookieManager` 保存，原生请求带同一个 Cookie 头
- 兜底：任何原生页解析失败都能一键切「网页版」
