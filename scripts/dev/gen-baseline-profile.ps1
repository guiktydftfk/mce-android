$ErrorActionPreference = "Continue"
$repo = "D:\大D老师的地盘\mce-android-v034"
$adb = "D:\大D老师的地盘\android-dev\sdk\platform-tools\adb.exe"
$s = "127.0.0.1:7555"
$diag = "D:\大D老师的地盘\.diagnostics"

# 1) Manifest 添加 profileable
$p = "$repo\app\src\main\AndroidManifest.xml"
$l = [IO.File]::ReadAllLines($p, [Text.Encoding]::UTF8)
if (-not (($l -join "`n").Contains("profileable"))) {
    $o = New-Object System.Collections.Generic.List[string]
    $done = $false
    foreach ($line in $l) {
        if (-not $done -and $line -match '^\s*<service') {
            $o.Add('        <!-- v0.4.4：允许 shell 对 release 包 profiling，用于生成 Baseline Profile -->')
            $o.Add('        <profileable android:shell="true" />')
            $o.Add('')
            $done = $true
        }
        $o.Add($line)
    }
    [IO.File]::WriteAllLines($p, $o.ToArray(), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "[manifest] profileable added"
} else { Write-Host "[manifest] already has profileable" }

# 2) 构建 release（profileable 生效）
& powershell -NoProfile -ExecutionPolicy Bypass -File "$diag\build-quick.ps1" 2>&1 | Select-Object -Last 2

# 3) 安装并采集 profile
& $adb start-server 2>&1 | Out-Null; Start-Sleep -Seconds 2; & $adb connect $s 2>&1 | Out-Null; Start-Sleep -Seconds 2
& $adb -s $s install -r "$repo\dist\MCE-starlight-v0.4.3.apk" 2>&1 | Select-Object -Last 1
Start-Sleep -Seconds 2
$apkPath = ((& $adb -s $s shell pm path cn.mce.starlight 2>&1) | Select-String "base.apk" | Select-Object -First 1).Line -replace "^package:", ""
Write-Host "[prof] apk=$apkPath"
& $adb -s $s shell "rm -f /data/local/tmp/mce.prof /data/local/tmp/baseline-prof.txt" | Out-Null
& $adb -s $s shell am force-stop cn.mce.starlight | Out-Null
& $adb -s $s shell am start -n cn.mce.starlight/.MainActivity | Out-Null
Start-Sleep -Seconds 3
$r = & $adb -s $s shell "am profile start --sampling 10 cn.mce.starlight /data/local/tmp/mce.prof" 2>&1
Write-Host ("[prof] start -> " + ($r -join " "))
Start-Sleep -Seconds 2
# 交互覆盖主要页面
& $adb -s $s shell input tap 63 113 | Out-Null; Start-Sleep -Seconds 2
& $adb -s $s shell input tap 95 475 | Out-Null; Start-Sleep -Seconds 3
& $adb -s $s shell input tap 720 617 | Out-Null; Start-Sleep -Seconds 4
& $adb -s $s shell input tap 270 923 | Out-Null; Start-Sleep -Seconds 7
& $adb -s $s shell input swipe 720 1800 720 900 300 | Out-Null; Start-Sleep -Seconds 1
& $adb -s $s shell input keyevent 4 | Out-Null; Start-Sleep -Seconds 2
& $adb -s $s shell input keyevent 4 | Out-Null; Start-Sleep -Seconds 2
& $adb -s $s shell input keyevent 4 | Out-Null; Start-Sleep -Seconds 2
& $adb -s $s shell "am profile stop cn.mce.starlight" 2>&1 | Select-Object -First 2
Start-Sleep -Seconds 2
& $adb -s $s shell "ls -l /data/local/tmp/mce.prof" 2>&1

# 4) profman 生成 baseline
& $adb -s $s shell "su -c 'profman --generate-baseline --apk=$apkPath --dex-location=$apkPath --profile-file=/data/local/tmp/mce.prof --output=/data/local/tmp/baseline-prof.txt'" 2>&1 | Select-Object -First 3
& $adb -s $s shell "ls -l /data/local/tmp/baseline-prof.txt" 2>&1
& $adb -s $s pull /data/local/tmp/baseline-prof.txt "$diag\baseline-prof.txt" 2>&1 | Select-Object -Last 1
if (Test-Path "$diag\baseline-prof.txt") {
    $f = Get-Item "$diag\baseline-prof.txt"
    Write-Host ("[baseline] size=" + $f.Length + " lines=" + (Get-Content $f.FullName | Measure-Object).Count)
    Get-Content $f.FullName | Select-Object -First 3
    if ($f.Length -gt 100) {
        New-Item -ItemType Directory -Force -Path "$repo\app\src\main" | Out-Null
        Copy-Item $f.FullName "$repo\app\src\main\baseline-prof.txt" -Force
        Write-Host "[baseline] copied to app/src/main/baseline-prof.txt"
        & powershell -NoProfile -ExecutionPolicy Bypass -File "$diag\build-quick.ps1" 2>&1 | Select-Object -Last 2
    }
}

# 5) 检查 APK 内是否包含 baseline profile
$apk = "$repo\dist\MCE-starlight-v0.4.3.apk"
if (Test-Path $apk) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $entries = $zip.Entries | Where-Object { $_.FullName -match "baseline|dexopt|profile" } | ForEach-Object { $_.FullName }
        Write-Host ("[apk] profile entries: " + (($entries | Select-Object -First 5) -join ", "))
    } finally { $zip.Dispose() }
    Write-Host ("[apk] sha256=" + (Get-FileHash $apk -Algorithm SHA256).Hash)
}
