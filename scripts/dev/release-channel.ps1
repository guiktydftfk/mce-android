<#
.SYNOPSIS
  MCE 星光班 · 发布通道脚本（v0.4.8 发布流程脚本化：正式 / 灰度 / 回滚）

.DESCRIPTION
  在已有 release.ps1（构建 → 测试 → 归档 → 同步）之后使用，负责「通道切换」：
    -Mode promote  把当前 APK 转为正式版（version.json 正式段 + release/MCE-latest.apk）
    -Mode canary   发布/更新灰度 beta（version.json canary 段 + release/canary/）
    -Mode rollback 把灰度名单清空（停止灰度），可指定 fallback 正式版号

  默认只改本地文件并归档；-Commit 才提交，-Push 才推送（需 ssh-tunnel 运行）。
  -DryRun 只打印将执行的动作，不写任何文件（用于核对参数）。

.EXAMPLE
  powershell -File scripts/dev/release-channel.ps1 -Mode promote -VersionName 0.4.8 -VersionCode 55 `
      -Apk dist/MCE-starlight-v0.4.8.apk -NotesFile notes-0.4.8.txt -Commit -Push

.EXAMPLE
  powershell -File scripts/dev/release-channel.ps1 -Mode canary -VersionName 0.4.9-beta -VersionCode 56 `
      -Apk dist/MCE-starlight-v0.4.9-beta.apk -Uids 2549,3303,4146,2782 -Notes "灰度测试" -Commit

.EXAMPLE
  powershell -File scripts/dev/release-channel.ps1 -Mode rollback -DryRun
#>
param(
    [Parameter(Mandatory = $true)][ValidateSet("promote", "canary", "rollback")][string]$Mode,
    [string]$VersionName = "",
    [int]$VersionCode = 0,
    [string]$Apk = "",
    [string]$Notes = "",
    [string]$NotesFile = "",
    [switch]$Emergency,
    [string]$Uids = "",
    [switch]$Commit,
    [switch]$Push,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
# 兼容两种位置：仓库内 scripts/dev/ 或 .diagnostics/（同级多一个 mce-android-v034 目录）
$parent = Split-Path $here -Parent
# 兼容两种位置：仓库内 scripts/dev/ 或 .diagnostics/（旁边是 mce-android-v034 目录）
$candidates = @(
    (Split-Path $parent -Parent),                 # scripts/dev -> 仓库根
    (Join-Path $parent "mce-android-v034"),       # .diagnostics -> ../mce-android-v034
    $parent
)
$repo = $candidates | Where-Object { (Test-Path (Join-Path $_ "app")) -and (Test-Path (Join-Path $_ "version.json")) } | Select-Object -First 1
if (-not $repo) { Write-Host "[release-channel] 失败: 找不到仓库根目录"; exit 1 }
$versionJson = Join-Path $repo "version.json"
$releaseDir = Join-Path $repo "release"

function Log([string]$msg) { Write-Host "[release-channel] $msg" }
function Fail([string]$msg) { Write-Host "[release-channel] 失败: $msg" -ForegroundColor Red; exit 1 }

if (-not (Test-Path $versionJson)) { Fail "version.json 不存在: $versionJson" }
if ($NotesFile) {
    if (-not (Test-Path $NotesFile)) { Fail "NotesFile 不存在: $NotesFile" }
    $Notes = Get-Content $NotesFile -Raw -Encoding UTF8
}

if ($Mode -ne "rollback") {
    if ([string]::IsNullOrWhiteSpace($VersionName)) { Fail "请提供 -VersionName" }
    if ($VersionCode -le 0) { Fail "请提供 -VersionCode" }
    if ([string]::IsNullOrWhiteSpace($Notes)) { Fail "请提供 -Notes 或 -NotesFile" }
    if (-not (Test-Path $Apk)) { Fail "APK 不存在: $Apk" }
}

$json = Get-Content $versionJson -Raw -Encoding UTF8 | ConvertFrom-Json
$sha = if ($Apk -and (Test-Path $Apk)) { (Get-FileHash $Apk -Algorithm SHA256).Hash } else { "" }

Log "模式=$Mode 版本=$VersionName/$VersionCode APK=$Apk SHA256=$sha"
if ($DryRun) {
    Log "DryRun：将更新 version.json / 归档 APK / 可选提交与推送；不写文件。"
    Log ("当前正式版=" + $json.versionName + "/" + $json.versionCode + " 灰度=" + $json.canary.versionName + "/" + $json.canary.versionCode)
    exit 0
}

switch ($Mode) {
    "promote" {
        $json.versionName = $VersionName
        $json.versionCode = $VersionCode
        $json.notes = $Notes
        $json.emergency = [bool]$Emergency
        $versionedName = "MCE-starlight-v$VersionName-stable.apk"
        Copy-Item $Apk (Join-Path $releaseDir "MCE-latest.apk") -Force
        Copy-Item $Apk (Join-Path $releaseDir $versionedName) -Force
        Copy-Item $Apk (Join-Path $repo "MCE-starlight.apk") -Force
        if (-not (Test-Path (Join-Path $repo $versionedName))) {
            Copy-Item $Apk (Join-Path $repo $versionedName) -Force
        }
        $sums = @"
MCE 星光班 Android 客户端 · 稳定版归档
=====================================
版本: $VersionName (versionCode $VersionCode)$(if ($Emergency) { " —— 紧急更新" } else { "" })
包名: cn.mce.starlight
文件: $versionedName（与 release/MCE-latest.apk 同内容）
SHA256: $sha
最小系统: Android 8.0 (API 26)

安装说明
--------
- 覆盖安装即可，数据保留（包名与签名不变）
- 校验: Get-FileHash .\$versionedName -Algorithm SHA256
- 自动更新清单: release/MCE-latest.apk（versionCode 只增不减）
"@
        [IO.File]::WriteAllText((Join-Path $releaseDir "SHA256SUMS.txt"), $sums, (New-Object System.Text.UTF8Encoding($false)))
        Log "正式版归档完成（release/MCE-latest.apk + $versionedName）"
    }
    "canary" {
        if (-not $json.canary) { Fail "version.json 缺少 canary 段" }
        $json.canary.versionName = $VersionName
        $json.canary.versionCode = $VersionCode
        $json.canary.notes = $Notes
        if ($Uids) { $json.canary.uids = @($Uids.Split(",") | ForEach-Object { [int]$_.Trim() }) }
        if ($Emergency) { $json.canary.emergency = $true }
        $canaryDir = Join-Path $releaseDir "canary"
        New-Item -ItemType Directory -Force -Path $canaryDir | Out-Null
        Copy-Item $Apk (Join-Path $canaryDir ("MCE-starlight-$VersionName.apk")) -Force
        Log "灰度包归档完成（release/canary/）"
    }
    "rollback" {
        $json.canary.uids = @()
        $json.canary.notes = "灰度已暂停（$([DateTime]::Now.ToString('yyyy-MM-dd HH:mm'))）"
        Log "灰度已暂停：canary.uids 清空（已装 beta 的用户会按正式版通道更新）"
    }
}

[IO.File]::WriteAllText($versionJson, ($json | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))
Log "version.json 已更新"

if ($Commit -or $Push) {
    Push-Location $repo
    try {
        git add -A
        $msg = switch ($Mode) {
            "promote" { "release($VersionName): 正式版 $VersionName/$VersionCode$(if ($Emergency) { '（紧急更新）' } else { '' })" }
            "canary" { "release(canary): 灰度 $VersionName/$VersionCode" }
            "rollback" { "release(canary): 暂停灰度（清空名单）" }
        }
        git commit -q -m $msg
        Log "已本地提交: $msg"
    } finally { Pop-Location }
}
if ($Push) {
    Push-Location $repo
    try {
        $env:GIT_SSH_COMMAND = "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=NUL -o ConnectTimeout=25"
        git push "ssh://git@127.0.0.1:2222/guiktydftfk/mce-android.git" main
        Log "已推送 main（确认 ssh-tunnel 正在运行）"
    } finally { Pop-Location }
}
Log "完成 ✅"
