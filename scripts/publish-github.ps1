# From the unpacked source checkout: .\scripts\publish-github.ps1 -Publish
# Does not run without -Publish and an interactive confirmation.
param(
    [switch]$Publish,
    [string]$Repository = 'KarandaevRuslan/NotebookPocket'
)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))

foreach ($command in @('git', 'gh', 'python')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $command"
    }
}

if (-not (Test-Path .git)) {
    git init -b main
    if ($LASTEXITCODE -ne 0) { throw 'git init failed' }
}

git add -A
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
python scripts/check_repository.py
if ($LASTEXITCODE -ne 0) { throw 'Repository hygiene check failed: review staged files.' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Whitespace check failed.' }
Write-Host 'Changes to be published:' -ForegroundColor Cyan
git diff --cached --stat
git status --short

if (-not $Publish) {
    Write-Host 'Dry run only. Review the above list, then rerun with -Publish.' -ForegroundColor Yellow
    exit 0
}

gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Sign in first with gh auth login.' }
$confirmation = Read-Host "Type PUBLISH to create the PUBLIC GitHub repository $Repository and push the staged source"
if ($confirmation -cne 'PUBLISH') { Write-Host 'Cancelled.'; exit 0 }

git diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
    git commit -m 'Initial public NotebookPocket source'
    if ($LASTEXITCODE -ne 0) { throw 'git commit failed. Configure git user.name and user.email.' }
}

# Refuse to overwrite or force-push existing repositories.
gh repo view $Repository --json name --jq .name *> $null
if ($LASTEXITCODE -eq 0) { throw "Repository already exists: $Repository. Follow docs/PUBLISHING.md instead." }
if ((git remote) -contains 'origin') { throw 'An origin remote already exists. Review its URL before publishing.' }

gh repo create $Repository --public --source=. --remote=origin --push
if ($LASTEXITCODE -ne 0) { throw 'GitHub creation/push failed; check gh auth and git remote status.' }
Write-Host "Published https://github.com/$Repository" -ForegroundColor Green
