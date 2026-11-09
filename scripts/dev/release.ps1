param([Parameter(Mandatory=$true)][string]$Version, [int]$Code)
$ErrorActionPreference = "Continue"
$repo = "D:\大D老师的地盘\mce-android-v034"
$diag = "D:\大D老师的地盘\.diagnostics"
if (-not $Code) { $Code = [int](($Version -split '\.')[-1]) + 350 }  # 粗略默认，正式请显式传 -Code
$gp = "$repo\gradle.properties"
$t = [IO.File]::ReadAllText($gp, [Text.Encoding]::UTF8)
$t = [regex]::Replace($t, 'MCE_VERSION_NAME=.*', "MCE_VERSION_NAME=$Version")
$t = [regex]::Replace($t, 'MCE_VERSION_CODE=.*', "MCE_VERSION_CODE=$Code")
[IO.File]::WriteAllText($gp, $t, (New-Object System.Text.UTF8Encoding($false)))
$vj = "$repo\version.json"
$json = [IO.File]::ReadAllText($vj, [Text.Encoding]::UTF8) | ConvertFrom-Json
$json.versionName = $Version
$json.versionCode = $Code
[IO.File]::WriteAllText($vj, ($json | ConvertTo-Json -Depth 5).Replace("`r`n","`n"), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "[release] version -> $Version / $Code"
& powershell -NoProfile -ExecutionPolicy Bypass -File "$diag\compile-test-build.ps1"
if ($LASTEXITCODE -ne 0) { Write-Host "[release] 构建失败，停止"; exit 1 }
& powershell -NoProfile -ExecutionPolicy Bypass -File "$diag\sync-release.ps1" -Version $Version
& powershell -NoProfile -ExecutionPolicy Bypass -File "$diag\run-tests.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File "$diag\repack-baseline.ps1"
$apk = "$repo\release\MCE-latest.apk"
if (Test-Path $apk) {
    $desk = [Environment]::GetFolderPath('Desktop')
    Copy-Item $apk (Join-Path $desk "MCE-starlight-v$Version-stable.apk") -Force
    Copy-Item "$repo\release\SHA256SUMS.txt" (Join-Path $desk "MCE-SHA256SUMS.txt") -Force -ErrorAction SilentlyContinue
}
Write-Host ("[release] 完成。SHA256=" + (Get-FileHash $apk -Algorithm SHA256).Hash)
Write-Host "[release] 推送：git push origin main（网络受限时用 .diagnostics/ssh-tunnel.ps1）"
