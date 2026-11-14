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
function Back { & $adb -s $s shell input keyevent 4 | Out-Null; Start-Sleep -Milliseconds 900 }

# 抽屉内向下滚动查找目标文本，返回最后一次 dump
function DrawerFind([string]$label, [int]$tries = 6) {
    & $adb -s $s shell input tap 63 113 | Out-Null
    Start-Sleep -Seconds 2
    for ($i = 0; $i -lt $tries; $i++) {
        $d = Dump
        if ($d -match [regex]::Escape($label)) { return $d }
        Swipe ($W * 0.5) ($H * 0.82) ($W * 0.5) ($H * 0.30)
    }
    return (Dump)
}

Check "设备已连接" ([bool]((& $adb devices 2>&1) -match "127\.0\.0\.1:7555\s+device"))
$sizeOut = (& $adb -s $s shell dumpsys window displays 2>&1) -join " "
$m = [regex]::Match($sizeOut, "mBounds=Rect\(0, 0 - (\d+), (\d+)\)")
$W = if ($m.Success) { [int]$m.Groups[1].Value } else { 1440 }
$H = if ($m.Success) { [int]$m.Groups[2].Value } else { 2560 }
$landscape = $W -gt $H
Write-Host ("INFO  屏幕: ${W}x${H} land=${landscape}")

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
    Back
    Start-Sleep -Seconds 2
} else { Check "打开帖子" $false }

# 抽屉：我的
& $adb -s $s shell input tap 63 113 | Out-Null
Start-Sleep -Seconds 2
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

$mem = (& $adb -s $s shell dumpsys meminfo cn.mce.starlight 2>&1 | Select-String "TOTAL PSS" | Select-Object -First 1).Line
Write-Host ("INFO  " + $mem)

Write-Host ("===== 冒烟结果: " + $(if ($fail -eq 0) { "全部通过" } else { "$fail 项失败" }) + " =====")
exit $fail
