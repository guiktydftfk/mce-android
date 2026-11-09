$ErrorActionPreference = "Continue"
$repo = "D:\大D老师的地盘\mce-android-v034"
$diag = "D:\大D老师的地盘\.diagnostics"
$sdk = "D:\大D老师的地盘\android-dev\sdk"
$bt = Join-Path $sdk "build-tools\35.0.0"
$zipalign = Join-Path $bt "zipalign.exe"
$apksigner = Join-Path $bt "apksigner.bat"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$s = "127.0.0.1:7555"
$artIn = "$repo\app\build\intermediates\binary_art_profile\release\compileReleaseArtProfile\baseline.prof"
$artMeta = "$repo\app\build\intermediates\binary_art_profile_metadata\release\compileReleaseArtProfile\baseline.profm"

# 1) 正常构建
& powershell -NoProfile -ExecutionPolicy Bypass -File "$diag\build-quick.ps1" 2>&1 | Select-Object -Last 2
$built = Get-ChildItem "$repo\app\build\outputs\apk\release" -Filter "*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host ("[repack] built=" + $built.Name + " " + $built.Length + "B")
$before = "$diag\apk-before-repack.apk"
Copy-Item $built.FullName $before -Force

# 2) 测量「打包前」冷启动
& $adb start-server 2>&1 | Out-Null; & $adb connect $s 2>&1 | Out-Null; Start-Sleep -Seconds 2
& $adb -s $s install -r $before 2>&1 | Select-Object -Last 1
$timesBefore = @()
for ($i = 0; $i -lt 5; $i++) {
    & $adb -s $s shell am force-stop cn.mce.starlight | Out-Null
    Start-Sleep -Milliseconds 800
    $r = & $adb -s $s shell am start -W -n cn.mce.starlight/.MainActivity 2>&1
    $t = ($r | Select-String "TotalTime" | Select-Object -First 1).Line -replace "[^0-9]", ""
    if ($t) { $timesBefore += [int]$t }
    Start-Sleep -Seconds 1
}
Write-Host ("[repack] before times: " + ($timesBefore -join ", "))

# 3) 替换 APK 内的 baseline profile 并重签
$work = "$diag\repack"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work | Out-Null
$workApk = "$work\app.apk"
Copy-Item $built.FullName $workApk -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($workApk, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($name in @("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm")) {
        $zip.Entries | Where-Object { $_.FullName -eq $name } | ForEach-Object { $_.Delete() }
    }
    function Add-Entry([string]$entryName, [string]$srcFile) {
        $e = $zip.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
        $out = $e.Open()
        $bytes = [IO.File]::ReadAllBytes($srcFile)
        $out.Write($bytes, 0, $bytes.Length)
        $out.Close()
    }
    Add-Entry "assets/dexopt/baseline.prof" $artIn
    if (Test-Path $artMeta) { Add-Entry "assets/dexopt/baseline.profm" $artMeta }
} finally { $zip.Dispose() }
Write-Host "[repack] entries replaced"

$aligned = "$work\app-aligned.apk"
$signed = "$work\app-signed.apk"
& $zipalign -f 4 $workApk $aligned 2>&1 | Select-Object -Last 1
& $apksigner sign --ks "$repo\keystore\debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out $signed $aligned 2>&1 | Select-Object -Last 2
& $apksigner verify --print-certs $signed 2>&1 | Select-String "DN:|SHA-256" | Select-Object -First 2

$dist = "$repo\dist\MCE-starlight-v0.4.4.apk"
Copy-Item $signed $dist -Force
Copy-Item $signed "$repo\release\MCE-latest.apk" -Force
Write-Host ("[repack] output=" + $dist + " " + (Get-Item $dist).Length + "B sha=" + (Get-FileHash $dist -Algorithm SHA256).Hash)

# 4) 安装并测「打包后」冷启动
& $adb -s $s install -r $dist 2>&1 | Select-Object -Last 1
$timesAfter = @()
for ($i = 0; $i -lt 5; $i++) {
    & $adb -s $s shell am force-stop cn.mce.starlight | Out-Null
    Start-Sleep -Milliseconds 800
    $r = & $adb -s $s shell am start -W -n cn.mce.starlight/.MainActivity 2>&1
    $t = ($r | Select-String "TotalTime" | Select-Object -First 1).Line -replace "[^0-9]", ""
    if ($t) { $timesAfter += [int]$t }
    Start-Sleep -Seconds 1
}
Write-Host ("[repack] after times: " + ($timesAfter -join ", "))
if ($timesBefore.Count -gt 0 -and $timesAfter.Count -gt 0) {
    $mb = ($timesBefore | Measure-Object -Average).Average
    $ma = ($timesAfter | Measure-Object -Average).Average
    Write-Host ("[repack] avg before=" + [math]::Round($mb, 0) + "ms after=" + [math]::Round($ma, 0) + "ms")
}
