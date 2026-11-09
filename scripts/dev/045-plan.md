# 0.4.5 执行计划（定时任务 kickoff 用）

> 目标版本：0.4.5（versionCode 49），发布流程见文末。
> 仓库：D:\大D老师的地盘\mce-android-v034（branch main）
> 构建脚本：D:\大D老师的地盘\.diagnostics\
>   - quickcheck.ps1（编译）-WithTests 可带单测
>   - build-quick.ps1（assembleRelease，产出 dist/MCE-starlight-v<版本>.apk）
>   - compile-test-build.ps1（全量：lint+tests+release）
>   - run-tests.ps1（JUnit，当前 104 个测试）
>   - sync-release.ps1 -Version X（归档 + SHA256SUMS）
> 设备验证：adb 串口 127.0.0.1:7555（MuMu），无设备时用 `D:\大D老师的地盘\.diagnostics\device-lib.ps1`
> 网络注意：GitHub 直连不通时，本地代理 127.0.0.1:7897 可用；
>   `D:\大D老师的地盘\.diagnostics\ssh-tunnel.ps1` 起本地隧道 127.0.0.1:2222，
>   push 用 `git push ssh://git@127.0.0.1:2222/guiktydftfk/mce-android.git main`
>   （需 danger-full-access 权限，GIT_SSH_COMMAND 见 .diagnostics/ssh-tunnel.ps1 注释流程）

## P0 收尾（先做）
- [ ] 0.1 自研 Baseline Profile：新增 macrobenchmark 模块 → 真机采集启动/滚动 → 生成 `app/src/main/baseline-prof.txt` → release 重打包；验证 APK 内 `assets/dexopt/baseline.prof` 含本项目类；对比冷启动耗时
- [ ] 0.2 人工回归 4 项：侧栏长按★收藏板块 / 楼层长按菜单（复制链接·举报·编辑）/ 图片长按保存相册 / 未读私信系统通知弹出
- [ ] 0.3 构造未读私信 → 15 分钟内收到通知 → 点击打开 APP

## P1 功能
- [ ] 1.1 通知点击直达对应会话；通知开关/免打扰时段设置；启动器角标数字
- [ ] 1.2 编辑/删除自己的帖子与回复（原生提交并回传刷新）
- [ ] 1.3 举报补全：主题举报、用户举报（现有仅楼层）
- [ ] 1.4 帖子内搜索关键词；只看楼主时显示「只看楼主（N 楼）」与快速退出
- [ ] 1.5 板块排序持久化（记住最新/热门/精华）；收藏板块管理页（排序/取消）
- [ ] 1.6 最近浏览增强：单条删除、按板块分组、点进去回上次阅读位置

## P2 体验 / 质量
- [ ] 2.1 深色定时支持自定义时间段
- [ ] 2.2 图片保存原图画质；分享面板增加分享图片/复制链接
- [ ] 2.3 帖子详情缓存落盘（重启秒开）+ 收藏帖离线阅读
- [ ] 2.4 大文件拆分收尾：MceApp / NewThreadScreen / ThreadDetailScreen（每个 <30KB）
- [ ] 2.5 测试覆盖扩展（通知调度、SettingsStore、收藏/历史），目标 ≥120 单测；UI 冒烟脚本
- [ ] 2.6 发布流程脚本化（一条命令：版本号+version.json+README+SHA256SUMS+归档清理+push）

## 可选
- [ ] 3.2 帖子收藏分组/备注
- [ ] 3.3 桌面小部件（签到 / 未读）

## 发布检查单（0.4.5）
- [ ] 全部单测通过（≥120）
- [ ] lint + release 构建通过
- [ ] 真机冒烟：启动→首页→板块→帖子（图片/评分/只看楼主/楼层跳转）→搜索→私信→通知→设置
- [ ] README / version.json / SHA256SUMS 内容与版本一致（0.4.4 曾出现 README 文案错配，需逐项核对）
- [ ] 远端 main 推送成功并核对 APK 文件列表（仅当前版本）
- [ ] 本地保留当前 + 前三个版本 APK

## 恢复方式
1. 打开 DSH Desktop，选择/恢复本仓库会话；
2. 发送：「按 .diagnostics/045-plan.md 从 P0 开始继续，完成后发布 0.4.5」；
3. 或直接回复本会话「继续」。
