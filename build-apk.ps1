<#
.SYNOPSIS
  Build Notebook Pocket and install/run on Android user 0 when a device is connected.
.EXAMPLE
  .\build-apk.ps1 -SkipWeb
.EXAMPLE
  .\build-apk.ps1 -SkipWeb -Install -Serial R58...
.EXAMPLE
  .\build-apk.ps1 -NoInstall
#>
param(
    [switch]$SkipWeb,
    [switch]$Install,
    [switch]$NoInstall,
    [switch]$NoLaunch,
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
if ($Install -and $NoInstall) { throw '-Install and -NoInstall are mutually exclusive.' }
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$webRoot = Join-Path $projectRoot 'web'
$apkSource = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$artifactDir = Join-Path $projectRoot 'artifacts'
$artifactPath = Join-Path $artifactDir 'NotebookPocket-debug.apk'

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$File,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed (exit code $LASTEXITCODE): $File $($Arguments -join ' ')"
    }
}

if (-not (Test-Path -Path (Join-Path $projectRoot 'app\build.gradle.kts'))) {
    throw 'Place build-apk.ps1 in the NotebookPocket project root.'
}

# Resolve the bundled Wrapper before spending time rebuilding web assets.
$wrapper = Join-Path $projectRoot 'gradlew.bat'
if (Test-Path -LiteralPath $wrapper -PathType Leaf) {
    foreach ($relativePath in @('gradle\wrapper\gradle-wrapper.jar', 'gradle\wrapper\gradle-wrapper.properties')) {
        if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $relativePath) -PathType Leaf)) {
            throw "Gradle Wrapper is incomplete: missing $relativePath. Extract all files from the source archive into the project root."
        }
    }
    $gradle = $wrapper
} else {
    $gradleCommand = Get-Command 'gradle.bat', 'gradle' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $gradleCommand) {
        throw 'Gradle not found. Add gradlew.bat (Wrapper) to the project or install Gradle and add it to PATH.'
    }
    $gradle = $gradleCommand.Source
}

if (-not $SkipWeb) {
    $npm = Get-Command 'npm.cmd' -ErrorAction SilentlyContinue
    if (-not $npm) { throw 'npm.cmd not found. Install Node.js and reopen PowerShell.' }

    Push-Location $webRoot
    try {
        Invoke-Checked -File $npm.Source -Arguments @('ci', '--no-audit', '--no-fund')
        Invoke-Checked -File $npm.Source -Arguments @('run', 'build')
    } finally {
        Pop-Location
    }
}

Push-Location $projectRoot
try {
    Invoke-Checked -File $gradle -Arguments @(':app:assembleDebug')
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $apkSource -PathType Leaf)) {
    throw "Gradle finished, but the expected APK was not found: $apkSource"
}
New-Item -Path $artifactDir -ItemType Directory -Force | Out-Null
Copy-Item -LiteralPath $apkSource -Destination $artifactPath -Force
Write-Host "`nAPK ready: $artifactPath" -ForegroundColor Green

if ($NoInstall) {
    Write-Host 'Installation skipped (-NoInstall).'
    return
}

. (Join-Path $projectRoot 'scripts\AndroidDeploy.ps1')
$adbPath = Find-NotebookAdb
if (-not $adbPath) {
    if ($Install) { throw 'adb not found. Add Android SDK platform-tools to PATH or set ANDROID_HOME.' }
    Write-Warning 'No adb found; APK is built. Run .\install-apk.ps1 after connecting the device.'
    return
}

if ($Install) {
    $device = Get-NotebookAdbSerial -Adb $adbPath -Serial $Serial
} else {
    $device = Get-NotebookAdbSerial -Adb $adbPath -Serial $Serial -AllowNone
}
if (-not $device) {
    Write-Warning 'No authorized Android device; APK is built. Connect a phone and run .\install-apk.ps1.'
    return
}
Install-NotebookApkForUser0 -Adb $adbPath -Serial $device -Apk $artifactPath -NoLaunch:$NoLaunch
