param(
    [string]$Output = '',
    [string]$Serial = '',
    [ValidateSet('Capture', 'On', 'Off', 'Mark')][string]$Mode = 'Capture',
    [string]$Mark = ''
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\AndroidDeploy.ps1')
$adb = Find-NotebookAdb
if (-not $adb) { throw 'adb not found. Install Android SDK platform-tools or set ANDROID_HOME.' }
$device = Get-NotebookAdbSerial -Adb $adb -Serial $Serial
$deviceArgs = @('-s', $device)
$action = 'dev.pocket.notebook.EDITOR_DIAGNOSTICS'

function Set-EditorDiagnostics([string]$Value) {
    & $adb @deviceArgs shell am broadcast -p dev.pocket.notebook -a $action --ez enabled $Value
    if ($LASTEXITCODE -ne 0) { throw 'Failed to send the diagnostic switch.' }
}

if ($Mode -eq 'On') { Set-EditorDiagnostics 'true'; return }
if ($Mode -eq 'Off') { Set-EditorDiagnostics 'false'; return }
if ($Mode -eq 'Mark') {
    if ($Mark -notmatch '^[A-Z0-9][A-Z0-9-]{0,60}$') { throw 'Use a short uppercase marker, for example CASE1-BEGIN or CASE8-FAIL.' }
    & $adb @deviceArgs shell log -p i -t NotebookPocket $Mark
    if ($LASTEXITCODE -ne 0) { throw 'Failed to write the case marker.' }
    return
}

if (-not $Output) {
    $version = (Get-Content -Raw (Join-Path $PSScriptRoot 'web\package.json') | ConvertFrom-Json).version
    $Output = 'editor-{0}-{1}.log' -f $version, (Get-Date -Format 'yyyyMMdd-HHmmss')
}
Write-Host 'Open the editor in the newly installed debug app before capturing.'
Write-Host ('Writing {0}; reproduce selection and several Cut attempts with the keyboard open, then press Ctrl+C.' -f [IO.Path]::GetFullPath($Output))
Set-EditorDiagnostics 'true'
& $adb @deviceArgs shell log -p i -t NotebookPocket DIAG-CAPTURE-BEGIN
try {
    & $adb @deviceArgs logcat -v threadtime 'NotebookPocket:V' 'AndroidRuntime:E' '*:S' | Tee-Object -FilePath $Output
    if ($LASTEXITCODE -ne 0) { throw 'adb logcat failed.' }
} finally {
    # Best effort on Ctrl+C. The explicit -Mode Off command is also available.
    try { Set-EditorDiagnostics 'false' | Out-Null } catch { }
}
