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
