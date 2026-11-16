#!/usr/bin/env python3
"""Reject generated/private material in a Git index; validate a source export without Git too."""
from pathlib import Path
import subprocess
import shutil
import sys

ROOT = Path(__file__).resolve().parent.parent
APPROVED_IMAGES = {
    'app/src/main/assets/demo/pancakes/images/pancakes.png',
    'app/src/main/assets/demo/pancakes/images/preparation.png',
    'docs/brand/notebookpocket-icon.png',
    'docs/brand/social-preview.png',
    'docs/architecture/system-overview.svg',  # Reviewed diagram generated from adjacent .dot source.
    *{f'app/src/main/res/mipmap-{density}/ic_launcher{suffix}.png'
      for density in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi')
      for suffix in ('', '_round')},
}
IGNORED_PARTS = {'.git', '.gradle', '.kotlin', '.idea', '.vscode', 'node_modules', 'build', 'dist', '.test-cache', '__pycache__', 'coverage', '.patch-backups'}
BAD_SUFFIXES = {'.apk', '.aab', '.apks', '.zip', '.7z', '.jks', '.keystore', '.p12', '.pem', '.key', '.hprof', '.log', '.pyc'}
IMAGE_SUFFIXES = {'.svg', '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.bmp', '.avif'}

def violations(paths):
    bad = []
    for name in paths:
        path = Path(name)
        reasons = []
        if any(part in IGNORED_PARTS for part in path.parts): reasons.append('cache/build/dependency output')
        if path.parts and path.parts[0].startswith('verification-'): reasons.append('historical verification report')
        if name.startswith('app/src/main/assets/web/') and path.name != '.gitkeep': reasons.append('generated web asset')
        if path.suffix.lower() in BAD_SUFFIXES: reasons.append('private or generated artifact')
        if path.suffix.lower() in IMAGE_SUFFIXES and name not in APPROVED_IMAGES: reasons.append('unreviewed image')
        if path.name == 'local.properties' or (path.name.startswith('.env') and path.name != '.env.example'): reasons.append('local configuration')
        if reasons: bad.append((name, ', '.join(reasons)))
    return bad

def main():
    tracked = subprocess.run(['git', '-C', str(ROOT), 'ls-files', '-z'], capture_output=True) if shutil.which('git') else None
    if tracked is not None and tracked.returncode == 0 and (ROOT / '.git').exists():
        paths = tracked.stdout.decode().split('\0')
        paths = [p for p in paths if p]
    else:
        paths = []
        # A working tree may contain regenerated, ignored output. Audit source candidates only.
        for path in ROOT.rglob('*'):
            if not path.is_file(): continue
            rel = path.relative_to(ROOT)
            if any(part in IGNORED_PARTS for part in rel.parts): continue
            if rel.parts[0].startswith('verification-') or str(rel).replace('\\', '/').startswith('app/src/main/assets/web/'): continue
            paths.append(rel.as_posix())
    bad = violations(paths)
    if bad:
        for path, reason in bad: print(f'{path}: {reason}', file=sys.stderr)
        return 1
    for path in APPROVED_IMAGES:
        if not (ROOT / path).is_file(): raise SystemExit(f'Missing approved runtime asset: {path}')
    print(f'Repository hygiene: {len(paths)} source files checked; only explicitly approved images allowed.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
