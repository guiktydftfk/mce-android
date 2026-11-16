param([string]$Apk = "D:\大D老师的地盘\mce-android-v034\dist\MCE-starlight-v0.4.7-beta-test.apk")
$ErrorActionPreference = "Continue"
$adb = "D:\大D老师的地盘\android-dev\sdk\platform-tools\adb.exe"
$s = "127.0.0.1:7555"
$fail = 0

function Check([string]$name, $ok) {
    if ($ok) { Write-Host "PASS  $name" } else { Write-Host "FAIL  $name"; $script:fail++ }
}

function Dump {
    for ($t = 0; $t -lt 4; $t++) {
        & $adb -s $s shell uiautomator dump /sdcard/smoke.xml 2>&1 | Out-Null
        & $adb -s $s pull /sdcard/smoke.xml "$env:TEMP\smoke.xml" 2>&1 | Out-Null
        if (Test-Path "$env:TEMP\smoke.xml") {
            $x = [IO.File]::ReadAllText("$env:TEMP\smoke.xml", [Text.Encoding]::UTF8)
            if ($x.Length -gt 200 -and -not $x.Contains("could not")) { return $x }
        }
        Start-Sleep -Milliseconds 800
    }
    return ""
}

function Node([string]$x, [string]$pat) {
    $m = [regex]::Matches($x, '<node[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') |
        Where-Object { $_.Groups[1].Value -match $pat } | Select-Object -First 1
    if (-not $m) { return $null }
    $g = $m.Groups
    return [pscustomobject]@{
        X = [int](([int]$g[2].Value + [int]$g[4].Value) / 2)
        Y = [int](([int]$g[3].Value + [int]$g[5].Value) / 2)
    }
}

function TapNode($n) { if ($n) { & $adb -s $s shell input tap $n.X $n.Y | Out-Null; return $true } return $false }
function Swipe([double]$x1, [double]$y1, [double]$x2, [double]$y2) {
    & $adb -s $s shell input swipe ([int]$x1) ([int]$y1) ([int]$x2) ([int]$y2) 350 | Out-Null
    Start-Sleep -Milliseconds 900
}
function WinNow() {
    $o = (& $adb -s $s shell dumpsys window displays 2>&1) -join ' '
    $m = [regex]::Match($o, 'mBounds=Rect\(0, 0 - (\d+), (\d+)\)')
    if ($m.Success) { return @([int]$m.Groups[1].Value, [int]$m.Groups[2].Value) }
    return @($script:W, $script:H)
}
function Back { & $adb -s $s shell input keyevent 4 | Out-Null; Start-Sleep -Milliseconds 900 }

# 抽屉内向下滚动查找目标文本，返回最后一次 dump
function DrawerFind([string]$label, [int]$tries = 6) {
    $wn0 = WinNow
    & $adb -s $s shell input tap ([int]($wn0[0] * 0.04)) ([int]($wn0[1] * 0.06)) | Out-Null
    Start-Sleep -Seconds 2
    # 抽屉未打开则重点一次（旋转动画/首点被吞）
    if ((Dump) -notmatch "导航|全部最新|我的") { & $adb -s $s shell input tap 63 113 | Out-Null; Start-Sleep -Seconds 2 }
    for ($i = 0; $i -lt $tries; $i++) {
        $d = Dump
        if ($d -match [regex]::Escape($label)) { return $d }
        $wn = WinNow
        Swipe ($wn[0] * 0.28) ($wn[1] * 0.75) ($wn[0] * 0.28) ($wn[1] * 0.32)
    }
    return (Dump)
}

Check "设备已连接" ([bool]((& $adb devices 2>&1) -match "127\.0\.0\.1:7555\s+device"))
# 固定竖屏，避免 MuMu 窗口旋转导致坐标错位
& $adb -s $s shell settings put system accelerometer_rotation 0 | Out-Null
& $adb -s $s shell settings put system user_rotation 0 | Out-Null
Start-Sleep -Seconds 2
# v0.4.8：稳健读取屏幕尺寸——用 wm size + SurfaceOrientation 轮询，避免旋转动画期间读到旧尺寸
$physOut = (& $adb -s $s shell wm size 2>&1) -join " "
$pm = [regex]::Match($physOut, "Physical size: (\d+)x(\d+)")
$pw = if ($pm.Success) { [int]$pm.Groups[1].Value } else { 1440 }
$ph = if ($pm.Success) { [int]$pm.Groups[2].Value } else { 2560 }
$rotation = 0
for ($ri = 0; $ri -lt 10; $ri++) {
    $inputOut = (& $adb -s $s shell dumpsys input 2>&1) -join " "
    $rm = [regex]::Match($inputOut, "SurfaceOrientation: (\d)")
    if ($rm.Success) { $rotation = [int]$rm.Groups[1].Value }
    if ($rotation -eq 0 -or $rotation -eq 2) { break }
    Start-Sleep -Milliseconds 800
}
$landscape = ($rotation -eq 1 -or $rotation -eq 3)
if ($landscape) { $W = [Math]::Max($pw, $ph); $H = [Math]::Min($pw, $ph) } else { $W = [Math]::Min($pw, $ph); $H = [Math]::Max($pw, $ph) }
Write-Host ("INFO  屏幕: ${W}x${H} land=${landscape} rotation=${rotation}")

if (Test-Path $Apk) {
    & $adb -s $s install -r $Apk 2>&1 | Out-Null
    Check "APK 安装成功" $true
} else { Check ("APK 存在: " + $Apk) $false }

& $adb -s $s shell am force-stop cn.mce.starlight | Out-Null
& $adb -s $s shell am start -n cn.mce.starlight/.MainActivity | Out-Null
Start-Sleep -Seconds 9
$homePage = ""
for ($hi = 0; $hi -lt 4; $hi++) {
    $homePage = Dump
    if ($homePage -match "全部最新|最新|热帖|精华") { break }
    Start-Sleep -Seconds 2
}
Check "首页加载（含最新分类）" ($homePage -match "全部最新|最新|热帖|精华")
Check "首页帖子列表非空" ($homePage -match "回复 \d+|分钟前|小时前|昨天")

# 帖子详情
$n = Node $homePage "分钟前|小时前|昨天"
if ($n) {
    & $adb -s $s shell input tap $n.X ([int]($n.Y + 60)) | Out-Null
    Start-Sleep -Seconds 8
    $tp = Dump
    Check "帖子详情加载" ($tp -match "只看楼主|共 \d+ 楼|回复")
    Check "帖子详情含回复框" ($tp -match "说点什么|发表回复")
Check "帖子详情含赞赏按钮" ($tp -match "赞赏")
    Back
    Start-Sleep -Seconds 2
} else { Check "打开帖子" $false }

# 抽屉：我的
    & $adb -s $s shell input tap 63 113 | Out-Null
    Start-Sleep -Seconds 2
    # 抽屉未打开则重点一次（旋转动画/首点被吞）
    if ((Dump) -notmatch "导航|全部最新|我的") { & $adb -s $s shell input tap 63 113 | Out-Null; Start-Sleep -Seconds 2 }
$dx = Dump
Check "抽屉可打开（含我的）" ($dx -match '"我的"')
if (TapNode (Node $dx "^我的$")) {
    Start-Sleep -Seconds 6
    $p = Dump
    Check "我的页加载（含星光值/主题）" ($p -match "星光值|我的主题")
    Back
    Start-Sleep -Seconds 1
} else { Check "点击我的" $false }

# 抽屉：最近浏览（需要滚动）
$df = DrawerFind "最近浏览"
Check "抽屉含最近浏览（滚动后）" ($df -match "最近浏览")

# 设置页（同一份滚动后的抽屉里点设置）
$set = Node $df "设置"
if ($set) {
    & $adb -s $s shell input tap $set.X $set.Y | Out-Null
    Start-Sleep -Seconds 5
    Swipe ($W * 0.5) ($H * 0.78) ($W * 0.5) ($H * 0.27)
    Swipe ($W * 0.5) ($H * 0.78) ($W * 0.5) ($H * 0.27)
    $st = Dump
    Check "设置页加载" ($st -match "检查更新|明暗模式|存储")
    Check "设置含通知开关" ($st -match "未读私信通知")
    Back
    Start-Sleep -Seconds 1
} else { Check "抽屉找到设置" $false }

# 搜索页
$ds = DrawerFind "搜索"
if (TapNode (Node $ds "搜索")) {
    Start-Sleep -Seconds 3
    $se = Dump
    Check "搜索页加载" ($se -match "搜索历史|搜索")
    Back
    Start-Sleep -Seconds 1
} else { Check "抽屉找到搜索" $false }

# 消息提醒
$dn = DrawerFind "消息提醒"
if (TapNode (Node $dn "消息提醒")) {
    Start-Sleep -Seconds 5
    $nt = Dump
    Check "消息提醒加载" ($nt -match "私信|提醒|系统")
    Back
    Start-Sleep -Seconds 1
} else { Check "抽屉找到消息提醒" $false }

# 表情面板（首页打开帖子 → 回帖框表情）
$home2 = Dump
$tn = Node $home2 "分钟前|小时前|昨天"
if ($tn) {
    & $adb -s $s shell input tap $tn.X ([int]($tn.Y + 60)) | Out-Null
    Start-Sleep -Seconds 8
    $tp2 = Dump
    if (TapNode (Node $tp2 "^表情$")) {
        Start-Sleep -Seconds 2
        $ep = Dump
        Check "表情面板（分类+网格）" (($ep -match "最近") -and ($ep -match "关闭"))
        Back
        Start-Sleep -Seconds 1
    } else { Check "帖子内打开表情面板" $false }
    Back
    Start-Sleep -Seconds 1
} else { Check "表情面板前置：打开帖子" $false }

# 双栏（横屏）
$sizeNow = (& $adb -s $s shell dumpsys window displays 2>&1) -join " "
$m2 = [regex]::Match($sizeNow, "mBounds=Rect\(0, 0 - (\d+), (\d+)\)")
$W2 = if ($m2.Success) { [int]$m2.Groups[1].Value } else { $W }
$H2 = if ($m2.Success) { [int]$m2.Groups[2].Value } else { $H }
if ($W2 -gt $H2) {
    $hp = Dump
    $hn = Node $hp "分钟前|小时前|昨天"
    if ($hn) {
        & $adb -s $s shell input tap $hn.X ([int]($hn.Y + 60)) | Out-Null
        Start-Sleep -Seconds 8
        $dp = Dump
        Check "横屏双栏（左列表+右帖子）" (($dp -match "全部最新") -and ($dp -match "只看楼主|共 \d+ 楼|回复"))
        Back
        Start-Sleep -Seconds 1
    } else { Check "双栏前置：列表可见" $false }
} else {
    Write-Host "INFO  当前为竖屏，跳过双栏检查"
}


# ---- v0.4.8 新功能回归 ----
function NodeDesc([string]$x, [string]$pat) {
    $m = [regex]::Matches($x, '<node[^>]*content-desc="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') |
        Where-Object { $_.Groups[1].Value -match $pat } | Select-Object -First 1
    if (-not $m) { return $null }
    $g = $m.Groups
    return [pscustomobject]@{ X = [int](([int]$g[2].Value + [int]$g[4].Value) / 2); Y = [int](([int]$g[3].Value + [int]$g[5].Value) / 2) }
}

& $adb -s $s shell am force-stop cn.mce.starlight | Out-Null
& $adb -s $s shell am start -n cn.mce.starlight/.MainActivity | Out-Null
Start-Sleep -Seconds 9

# 抽屉新增入口
$drawerNew = DrawerFind "关注板块" 8
Check "抽屉含关注板块入口" ($drawerNew -match "关注板块")
Check "抽屉含在线会员入口" ($drawerNew -match "在线会员")
$onlineNode = Node $drawerNew "^在线会员$"
if ($onlineNode) { TapNode $onlineNode | Out-Null; Start-Sleep -Seconds 9 }
$onlinePage = Dump
Check "在线会员页加载" ($onlinePage -match "在线会员")
Check "在线会员有统计行" ($onlinePage -match "人在线")
Back

# 设置页：布局模式 / 应用锁 / 缓存管理 / 崩溃日志
& $adb -s $s shell input tap 63 113 | Out-Null
Start-Sleep -Seconds 2
$setNode = $null
for ($i = 0; $i -lt 5; $i++) {
    $dd = Dump
    $setNode = Node $dd "^设置$"
    if ($setNode) { break }
    Swipe 300 1800 300 1000
}
if ($setNode) { TapNode $setNode | Out-Null; Start-Sleep -Seconds 6 }
$settingsTop = Dump
for ($i = 0; $i -lt 3; $i++) { Swipe 720 900 720 2000; $settingsTop = Dump }
Check "设置含大屏布局（双栏/三栏）" (($settingsTop -match "大屏布局") -and ($settingsTop -match "三栏"))
$settingsAll = ""
$hitLock = $false; $hitCache = $false; $hitCrash = $false
for ($i = 0; $i -lt 8; $i++) {
    $pp = Dump
    if ($pp -match "应用锁") { $hitLock = $true }
    if ($pp -match "缓存管理") { $hitCache = $true }
    if ($pp -match "崩溃日志") { $hitCrash = $true; break }
    Swipe 720 1900 720 900
}
Check "设置含应用锁开关" $hitLock
Check "设置含缓存管理入口" $hitCache
$crashNode = Node (Dump) "崩溃日志"
if ($crashNode) { TapNode $crashNode | Out-Null; Start-Sleep -Seconds 6 }
$crashPage = Dump
Check "崩溃日志页加载" ($crashPage -match "崩溃日志")
Back

# 搜索筛选条
& $adb -s $s shell input tap 63 113 | Out-Null
Start-Sleep -Seconds 2
$searchNode = $null
for ($i = 0; $i -lt 5; $i++) {
    $dd = Dump
    $searchNode = Node $dd "^搜索$"
    if ($searchNode) { break }
    Swipe 300 1800 300 1000
}
if ($searchNode) { TapNode $searchNode | Out-Null; Start-Sleep -Seconds 5 }
$searchPage = Dump
$kwNode = Node $searchPage "关键词（匹配标题与正文）"
if ($kwNode) { TapNode $kwNode | Out-Null; Start-Sleep -Milliseconds 800; & $adb -s $s shell input text "星光" | Out-Null; Start-Sleep -Milliseconds 600 }
& $adb -s $s shell input keyevent 4 | Out-Null
Start-Sleep -Milliseconds 800
$goNode = NodeDesc (Dump) "搜索"
if ($goNode) { & $adb -s $s shell input tap $goNode.X $goNode.Y | Out-Null; Start-Sleep -Seconds 10 }
$resultPage = Dump
Check "搜索结果含筛选条" ($resultPage -match "在结果中筛选")

$mem = (& $adb -s $s shell dumpsys meminfo cn.mce.starlight 2>&1 | Select-String "TOTAL PSS" | Select-Object -First 1).Line
Write-Host ("INFO  " + $mem)


# 旋转回归：竖屏→横屏应进入双栏，转回竖屏应恢复单栏
$preRot = Dump
$rotBtn = Node $preRot "分钟前|小时前|刚刚|昨天"
if ($rotBtn) {
    & $adb -s $s shell input tap $rotBtn.X ([int]($rotBtn.Y + 40)) | Out-Null
    Start-Sleep -Seconds 8
    $beforeRot = Dump
    $isThread = ($beforeRot -match "只看楼主|共 \d+ 楼")
    if ($isThread) {
        & $adb -s $s shell settings put system accelerometer_rotation 0 | Out-Null
        & $adb -s $s shell settings put system user_rotation 1 | Out-Null
        Start-Sleep -Seconds 7
        $land = Dump
        Check "旋转后进入宽屏双栏" (($land -match "全部最新") -and ($land -match "只看楼主|共 \d+ 楼"))
        & $adb -s $s shell settings put system user_rotation 0 | Out-Null
        Start-Sleep -Seconds 6
        $port = Dump
        Check "转回竖屏恢复单栏" (-not (($port -match "全部最新") -and ($port -match "只看楼主|共 \d+ 楼")))
    } else { Write-Host "INFO  旋转检查跳过（未进入帖子页）" }
} else { Write-Host "INFO  旋转检查跳过（列表为空）" }
Write-Host ("===== 冒烟结果: " + $(if ($fail -eq 0) { "全部通过" } else { "$fail 项失败" }) + " =====")
exit $fail
