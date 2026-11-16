# One-command strict build + install + launch for Android user 0.
param(
    [switch]$SkipWeb,
    [string]$Serial,
    [switch]$NoLaunch
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $root 'build-apk.ps1') -SkipWeb:$SkipWeb -Install -Serial $Serial -NoLaunch:$NoLaunch
if (-not $?) { throw 'build-apk.ps1 failed.' }
