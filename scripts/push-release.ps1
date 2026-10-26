param(
    [string]$GitPath = "git",
    [string]$Message = "release: aMCE 0.3.2 (36) - 6 批稳定性/速度优化 + R8 发行包",
    [switch]$NoPush
)
# aMCE 发布推送脚本（本机环境缺少 git 时由用户执行）
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts/push-release.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/push-release.ps1 -GitPath "C:\Program Files\Git\cmd\git.exe"
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $git = Get-Command $GitPath -ErrorAction SilentlyContinue
    if (-not $git) {
        if (Test-Path $GitPath) { $git = @{ Source = $GitPath } }
        else { throw "未找到 git：请安装 Git for Windows，或用 -GitPath 指定 git.exe 路径" }
    }
    $g = $git.Source

    Write-Host "== git: $g"
    & $g --version
    # 首次使用时补本地身份（沿用仓库历史作者信息，避免 commit 失败）
    if (-not (& $g config user.name)) { & $g config user.name "流云闲鹿" }
    if (-not (& $g config user.email)) { & $g config user.email "zp13683900708@163.com" }
    & $g status --short
    & $g add -A
    # APK 被 .gitignore 忽略，需强制加入（更新的发行包）
    & $g add -f MCE-starlight.apk release/MCE-latest.apk
    & $g status --short
    $staged = & $g diff --cached --name-only
    if (-not $staged) {
        Write-Host '== 没有需要提交的改动'
    } else {
        & $g commit -m $Message
    }
    if ($NoPush) {
        Write-Host '== 已提交（未推送，-NoPush）'
    } else {
        & $g push origin main
        Write-Host '== 推送完成'
    }
} finally {
    Pop-Location
}
