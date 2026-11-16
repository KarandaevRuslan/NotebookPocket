# Shared adb helpers for NotebookPocket PowerShell wrappers (Windows PowerShell 5.1+).
# Installation is deliberately scoped to Android user 0. Never uninstall/clear data.

function Find-NotebookAdb {
    $command = Get-Command 'adb.exe', 'adb' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }

    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe')
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe')
    }
    if ($env:LOCALAPPDATA) {
        $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
    }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

function Get-NotebookAdbSerial {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [string]$Serial,
        [switch]$AllowNone
    )

    $listing = @(& $Adb devices)
    if ($LASTEXITCODE -ne 0) { throw 'adb devices failed. Check Android platform-tools and USB debugging.' }
    $online = @()
    $unavailable = @()
    foreach ($line in $listing) {
        if ($line -match '^([^\s]+)\s+device(?:\s|$)') {
            $online += $Matches[1]
        } elseif ($line -match '^([^\s]+)\s+(unauthorized|offline)(?:\s|$)') {
            $unavailable += "${Matches[1]} ($($Matches[2]))"
        }
    }

    $requested = $Serial
    if (-not $requested -and $env:ANDROID_SERIAL) { $requested = $env:ANDROID_SERIAL }
    if ($requested) {
        if ($online -contains $requested) { return $requested }
        throw "ADB device '$requested' is not ready. Connected: $($online -join ', '). Unavailable: $($unavailable -join ', '). Run adb devices."
    }
    if ($online.Count -eq 1) { return $online[0] }
    if ($online.Count -gt 1) {
        throw "Multiple Android devices connected: $($online -join ', '). Choose one with -Serial <device-id>."
    }
    if ($AllowNone) { return $null }
    throw "No authorized Android device found. Run adb devices, connect a phone and approve USB debugging. Unavailable: $($unavailable -join ', ')."
}

function Install-NotebookApkForUser0 {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$Apk,
        [switch]$NoLaunch
    )
    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) {
        throw "APK not found: $Apk"
    }
    $fullApk = (Resolve-Path -LiteralPath $Apk).Path
    Write-Host "`nDeploying to device '$Serial', Android user 0:" -ForegroundColor Cyan
    Write-Host "  $fullApk"

    $users = @(& $Adb -s $Serial shell pm list users)
    if ($LASTEXITCODE -ne 0 -or -not ($users -match 'UserInfo\{0:')) {
        throw 'Could not confirm Android user 0 via adb shell pm list users.'
    }

    # -r preserves app data. -t allows debug/test packages. No uninstall, no pm clear.
    $output = @(& $Adb -s $Serial install -r -t --user 0 $fullApk)
    $exitCode = $LASTEXITCODE
    foreach ($line in $output) { Write-Host $line }
    if ($exitCode -ne 0 -or -not ($output -match '^Success')) {
        throw "APK install for Android user 0 failed (exit code $exitCode). Check adb output above. Existing app data was not cleared."
    }

    $packages = @(& $Adb -s $Serial shell pm list packages --user 0 dev.pocket.notebook)
    if ($LASTEXITCODE -ne 0 -or -not ($packages -match '^package:dev\.pocket\.notebook\s*$')) {
        throw 'adb install returned Success, but dev.pocket.notebook was not found for Android user 0.'
    }
    Write-Host 'Verified: dev.pocket.notebook is installed for user 0.' -ForegroundColor Green
    if (-not $NoLaunch) {
        Start-NotebookAppForUser0 -Adb $Adb -Serial $Serial
    }
}

function Start-NotebookAppForUser0 {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string]$Serial
    )
    $foregroundUser = @(& $Adb -s $Serial shell am get-current-user)
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'Cannot determine the foreground Android user. The app was installed for user 0; launch skipped.'
        return
    }
    if ((($foregroundUser -join '').Trim()) -ne '0') {
        Write-Warning "Android user 0 is not the foreground user (current: $($foregroundUser -join '')). Installed for user 0; launch skipped."
        return
    }
    $packages = @(& $Adb -s $Serial shell pm list packages --user 0 dev.pocket.notebook)
    if ($LASTEXITCODE -ne 0 -or -not ($packages -match '^package:dev\.pocket\.notebook\s*$')) {
        throw 'Notebook Pocket is not installed for Android user 0. Run install-apk.ps1 first.'
    }
    $started = @(& $Adb -s $Serial shell am start --user 0 -n dev.pocket.notebook/.MainActivity)
    $exitCode = $LASTEXITCODE
    foreach ($line in $started) { Write-Host $line }
    if ($exitCode -ne 0 -or ($started -match '(?i)^(Error:|Exception|SecurityException)')) {
        throw "Failed to launch Notebook Pocket for user 0 (exit code $exitCode)."
    }
    Write-Host 'Notebook Pocket launch requested for user 0.' -ForegroundColor Green
}
