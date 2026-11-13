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
        Start-Sleep -Milliseconds 700
    }
    return ""
}

function Node([string]$x, [string]$pat) {
    $m = [regex]::Matches($x, '<node[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') |
        Where-Object { $_.Groups[1].Value -match $pat } | Select-Object -First 1
    if (-not $m) { return $null }
    $g = $m.Groups
    $cx = ([int]$g[2].Value + [int]$g[4].Value) / 2
    $cy = ([int]$g[3].Value + [int]$g[5].Value) / 2
    return [pscustomobject]@{ X = [int]$cx; Y = [int]$cy }
}

function Swipe([double]$x1, [double]$y1, [double]$x2, [double]$y2) {
    & $adb -s $s shell input swipe ([int]$x1) ([int]$y1) ([int]$x2) ([int]$y2) 300 | Out-Null
    Start-Sleep -Milliseconds 800
}

Check "设备已连接" ([bool]((& $adb devices 2>&1) -match "127\.0\.0\.1:7555\s+device"))
$sizeOut = (& $adb -s $s shell dumpsys window displays 2>&1) -join " "
$m = [regex]::Match($sizeOut, "mBounds=Rect\(0, 0 - (\d+), (\d+)\)")
$W = if ($m.Success) { [int]$m.Groups[1].Value } else { 1440 }
$H = if ($m.Success) { [int]$m.Groups[2].Value } else { 2560 }
Write-Host ("INFO  屏幕: ${W}x${H}")

if (Test-Path $Apk) {
    & $adb -s $s install -r $Apk 2>&1 | Out-Null
    Check "APK 安装成功" $true
} else { Check ("APK 存在: " + $Apk) $false }

& $adb -s $s shell am force-stop cn.mce.starlight | Out-Null
& $adb -s $s shell am start -n cn.mce.starlight/.MainActivity | Out-Null
Start-Sleep -Seconds 9
$homePage = Dump
Check "首页加载（含最新分类）" ($homePage -match "全部最新")
Check "首页帖子列表非空" ($homePage -match "回复 \d+|分钟前|小时前|昨天")

# 抽屉顶部：我的
& $adb -s $s shell input tap 63 113 | Out-Null
Start-Sleep -Seconds 2
$x = Dump
Check "抽屉可打开（含我的）" ($x -match '"我的"')
$n = Node $x "^我的$"
if ($n) {
    & $adb -s $s shell input tap $n.X $n.Y | Out-Null
    Start-Sleep -Seconds 6
    $p = Dump
    Check "我的页加载（含星光值/主题）" ($p -match "星光值|我的主题")
    & $adb -s $s shell input keyevent 4 | Out-Null
    Start-Sleep -Seconds 1
} else { Check "点击我的" $false }

# 抽屉滚到底：最近浏览 + 设置
& $adb -s $s shell input tap 63 113 | Out-Null
Start-Sleep -Seconds 2
Swipe ($W/2) ($H*0.80) ($W/2) ($H*0.30)
Swipe ($W/2) ($H*0.80) ($W/2) ($H*0.30)
$xd = Dump
Check "抽屉含最近浏览（滚动后）" ($xd -match "最近浏览")
$set = Node $xd "设置"
if ($set) {
    & $adb -s $s shell input tap $set.X $set.Y | Out-Null
    Start-Sleep -Seconds 5
    Swipe ($W*0.5) ($H*0.78) ($W*0.5) ($H*0.27)
    Swipe ($W*0.5) ($H*0.78) ($W*0.5) ($H*0.27)
    $st = Dump
    Check "设置页加载" ($st -match "检查更新|明暗模式|存储")
    Check "设置含通知开关" ($st -match "未读私信通知")
} else { Check "抽屉找到设置" $false }

$mem = (& $adb -s $s shell dumpsys meminfo cn.mce.starlight 2>&1 | Select-String "TOTAL PSS" | Select-Object -First 1).Line
Write-Host ("INFO  " + $mem)

Write-Host ("===== 冒烟结果: " + $(if ($fail -eq 0) { "全部通过" } else { "$fail 项失败" }) + " =====")
exit $fail
