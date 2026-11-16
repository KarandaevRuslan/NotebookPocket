# Installs the previously built APK ONLY for Android user 0, preserving app data.
param(
    [string]$Apk,
    [string]$Serial,
    [switch]$NoLaunch
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $root 'scripts\AndroidDeploy.ps1')
if (-not $Apk) {
    $Apk = Join-Path $root 'artifacts\NotebookPocket-debug.apk'
} elseif (-not [IO.Path]::IsPathRooted($Apk)) {
    $Apk = Join-Path (Get-Location).Path $Apk
}
$adbPath = Find-NotebookAdb
if (-not $adbPath) { throw 'adb.exe not found. Install Android SDK platform-tools or set ANDROID_HOME.' }
$device = Get-NotebookAdbSerial -Adb $adbPath -Serial $Serial
Install-NotebookApkForUser0 -Adb $adbPath -Serial $device -Apk $Apk -NoLaunch:$NoLaunch
