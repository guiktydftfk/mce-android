param(
    [string]$Message = "",
    [switch]$NoPush
)
# aMCE release push script (ASCII only - Windows PowerShell 5.1 mis-parses UTF-8 .ps1 without BOM).
#
# Environment notes (confirmed during 0.3.2 release):
#  - git is NOT in PATH; GitHub Desktop bundles one at app-*\resources\app\git\cmd\git.exe
#  - that git spawns cygwin sh which cannot create a signal pipe in the sandbox
#    (Win32 error 5), so use the native Windows OpenSSH binary + GIT_SSH_VARIANT=ssh
#    (otherwise: "ssh variant 'simple' does not support setting port")
#  - capturing native output via PowerShell pipes may fail, so run through cmd + file redirect
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$logFile = Join-Path $projectRoot 'push.log'

function Find-Git {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $desktop = Join-Path $env:LOCALAPPDATA 'GitHubDesktop'
    if (Test-Path $desktop) {
        $hit = Get-ChildItem $desktop -Directory -Filter 'app-*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'resources\app\git\cmd\git.exe' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($hit) { return $hit }
    }
    foreach ($p in @(
        'C:\Program Files\Git\cmd\git.exe',
        "${env:ProgramFiles(x86)}\Git\cmd\git.exe",
        "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe"
    )) { if (Test-Path $p) { return $p } }
    throw 'git not found: install Git for Windows or put git.exe on PATH'
}

function Run([string]$cmdLine) {
    Push-Location $projectRoot
    try {
        cmd /c ($cmdLine + ' > "' + $logFile + '" 2>&1')
        $code = $LASTEXITCODE
        if (Test-Path $logFile) {
            Get-Content $logFile | ForEach-Object { Write-Host "  $_" }
        }
        return $code
    } finally { Pop-Location }
}

$git = Find-Git
$ssh = 'C:\Windows\System32\OpenSSH\ssh.exe'
if (Test-Path $ssh) {
    $env:GIT_SSH_COMMAND = $null
    $env:GIT_SSH = $ssh
    $env:GIT_SSH_VARIANT = 'ssh'
}
$g = '"' + $git + '" -c safe.directory=*'

Write-Host "== git: $git"
Run "$g --version" | Out-Null
# local identity only (never touch global config); author name kept as the repo owner's
$ownerName = -join ([char[]]@(0x6D41, 0x4E91, 0x95F2, 0x9E7F))
Run "$g config user.name `"$ownerName`"" | Out-Null
Run "$g config user.email `"zp13683900708@163.com`"" | Out-Null

Write-Host '== changes:'
Run "$g status --short" | Out-Null
Run "$g add -A" | Out-Null
# APK files are ignored by .gitignore; force-add the release artifacts
Run "$g add -f MCE-starlight.apk release/MCE-latest.apk" | Out-Null

Run "$g diff --cached --quiet" | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host '== nothing to commit'
} else {
    if (-not $Message) { $Message = 'chore: update release artifacts' }
    $safeMsg = $Message.Replace('"', '""')
    if ((Run "$g commit -m `"$safeMsg`"") -ne 0) { throw 'git commit failed' }
    Write-Host '== committed'
}

if ($NoPush) {
    Write-Host '== committed (-NoPush, not pushed)'
    return
}

if ((Run "$g push origin main") -ne 0) { throw 'git push failed (check SSH key / network)' }
Run "$g log --oneline -1" | Out-Null
Write-Host '== push done'
Remove-Item $logFile -Force -ErrorAction SilentlyContinue
