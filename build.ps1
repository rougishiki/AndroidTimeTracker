<#
.SYNOPSIS
    构建 TimeTrack APK。

.PARAMETER Variant
    release（默认）：R8 压缩 + 资源压缩，约 1.2 MB，日常使用装这个。
    debug          ：不压缩、不混淆，约 9 MB；若 release 版出现异常，用它排查。

.EXAMPLE
    .\build.ps1
    .\build.ps1 -Variant debug
#>
param(
    [ValidateSet('debug', 'release')]
    [string]$Variant = 'release'
)

$ErrorActionPreference = 'Stop'

# TimeTrack 目录的上一级（即「安卓时间管理」）
$root = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $root '.toolchain'

$env:JAVA_HOME = 'D:\Dev\JDK\jdk-21'
$env:ANDROID_HOME = Join-Path $toolchain 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $toolchain 'gradle-home'

foreach ($p in @($env:JAVA_HOME, $env:ANDROID_HOME, $env:GRADLE_USER_HOME)) {
    if (-not (Test-Path $p)) { throw "缺少必要路径：$p" }
}

# 优先用本地已装好的 Gradle；找不到才退回工程自带的 wrapper
# （wrapper 首次运行会把发行版下载到 GRADLE_USER_HOME，需要联网）。
$gradle = Join-Path $toolchain 'gradle-8.9\bin\gradle.bat'
if (-not (Test-Path $gradle)) {
    $gradle = Join-Path $PSScriptRoot 'gradlew.bat'
    if (-not (Test-Path $gradle)) { throw '既找不到本地 Gradle，也找不到 gradlew.bat' }
}

$task = if ($Variant -eq 'release') { 'assembleRelease' } else { 'assembleDebug' }

Write-Host "==> $task" -ForegroundColor Cyan
& $gradle -p $PSScriptRoot $task --console=plain
if ($LASTEXITCODE -ne 0) { throw "构建失败，退出码 $LASTEXITCODE" }

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\$Variant\app-$Variant.apk"
if (-not (Test-Path $apk)) { throw "构建已结束但找不到 APK：$apk" }

$sizeMb = [math]::Round((Get-Item $apk).Length / 1MB, 2)
Write-Host ''
Write-Host "==> 构建成功（$Variant）" -ForegroundColor Green
Write-Host "    APK : $apk"
Write-Host "    大小: $sizeMb MB"
