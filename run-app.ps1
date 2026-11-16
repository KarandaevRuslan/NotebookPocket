# Launch an already installed Notebook Pocket for foreground Android user 0.
param([string]$Serial)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $root 'scripts\AndroidDeploy.ps1')
$adbPath = Find-NotebookAdb
if (-not $adbPath) { throw 'adb.exe not found. Install Android SDK platform-tools or set ANDROID_HOME.' }
$device = Get-NotebookAdbSerial -Adb $adbPath -Serial $Serial
Start-NotebookAppForUser0 -Adb $adbPath -Serial $device
