param(
    [switch]$Bump,
    [switch]$NoCopy,
    [switch]$ToDesktop
)
# MCE Android one-click build.
#   -Bump : increment version. Rule: patch carries to minor at 10  (0.1.9 -> 0.2.0)
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path $PSScriptRoot -Parent
$workspace   = Split-Path $projectRoot -Parent
$devRoot     = Join-Path $workspace 'android-dev'
$propsFile   = Join-Path $projectRoot 'gradle.properties'

$jdk        = (Get-ChildItem (Join-Path $devRoot 'jdk')    -Directory | Select-Object -First 1).FullName
$gradle     = (Get-ChildItem (Join-Path $devRoot 'gradle') -Directory | Select-Object -First 1).FullName
$sdk        = Join-Path $devRoot 'sdk'
$gradleHome = Join-Path $devRoot 'gradle-home'

$lines = [System.IO.File]::ReadAllLines($propsFile)
function Get-Prop([string]$key) {
    $line = $lines | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
    if (-not $line) { return $null }
    return ($line -split '=', 2)[1].Trim()
}
$curName = Get-Prop 'MCE_VERSION_NAME'
$curCode = [int](Get-Prop 'MCE_VERSION_CODE')

if ($Bump) {
    $parts = $curName.Split('.')
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    $patch = [int]$parts[2] + 1
    # 每 10 进 1：补丁位满 10 进位到次版本，余数保留（0.1.9 -> 0.2.0，0.0.9 -> 0.1.0）
    if ($patch -ge 10) { $minor += [math]::Floor($patch / 10); $patch = $patch % 10 }
    if ($minor -ge 10) { $major += [math]::Floor($minor / 10); $minor = $minor % 10 }
    $newName = "$major.$minor.$patch"
    $newCode = $curCode + 1
    $lines = $lines | ForEach-Object {
        if ($_ -match '^MCE_VERSION_NAME=') { "MCE_VERSION_NAME=$newName" }
        elseif ($_ -match '^MCE_VERSION_CODE=') { "MCE_VERSION_CODE=$newCode" }
        else { $_ }
    }
    [System.IO.File]::WriteAllLines($propsFile, $lines, [System.Text.UTF8Encoding]::new($false))
    $curName = $newName
    $curCode = $newCode
}
Write-Host "[build] version = $curName ($curCode)"

$env:JAVA_HOME        = $jdk
$env:ANDROID_HOME     = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:GRADLE_USER_HOME = $gradleHome
$env:PATH             = "$jdk\bin;$env:PATH"

$log = Join-Path $projectRoot 'build.log'
cmd /c "`"$gradle\bin\gradle.bat`" --project-dir `"$projectRoot`" --gradle-user-home `"$gradleHome`" :app:assembleRelease --no-daemon --console=plain > `"$log`" 2>&1"
$code = $LASTEXITCODE
Write-Host "[build] gradle exit = $code"
Get-Content $log -Tail 12

$apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
if ($code -ne 0 -or -not (Test-Path $apk)) {
    Write-Host '[build] FAILED - see build.log'
    exit 1
}

$info = Get-Item $apk
Write-Host ("[build] OK {0:N2} MB" -f ($info.Length / 1MB))

if (-not $NoCopy) {
    $outDir = Join-Path $projectRoot 'dist'
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $name = "MCE-starlight-v$curName.apk"
    Copy-Item $apk (Join-Path $outDir $name) -Force
    Write-Host "[build] dist -> $(Join-Path $outDir $name)"

    if ($ToDesktop) {
        $desk = [Environment]::GetFolderPath('Desktop')
        $target = Join-Path $desk "MCE-starlight-v$curName.apk"
        Copy-Item $apk $target -Force
        Write-Host "[build] desktop -> $target"
    }
}
