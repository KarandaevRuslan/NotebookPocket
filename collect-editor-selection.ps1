param([string]$Output = '', [string]$Serial = '')
# Compatibility alias; capture all four current editor cases through one tag.
& (Join-Path $PSScriptRoot 'collect-editor-diagnostics.ps1') -Output $Output -Serial $Serial
