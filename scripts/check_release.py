#!/usr/bin/env python3
"""Validate a release tag without shell-evaluating tag content or publishing anything."""
from pathlib import Path
import json
import re
import sys
root = Path(__file__).resolve().parent.parent
if len(sys.argv) != 2 or not re.fullmatch(r'v\d+\.\d+\.\d+', sys.argv[1]):
    raise SystemExit('Expected a version tag such as v0.9.3')
package = json.loads((root/'web/package.json').read_text())
lock = json.loads((root/'web/package-lock.json').read_text())
version = sys.argv[1][1:]
if package['version'] != version or lock['version'] != version or lock['packages']['']['version'] != version:
    raise SystemExit('Tag, package.json and lockfile versions must agree')
if version not in (root/'CHANGELOG.md').read_text():
    raise SystemExit('Add release notes before tagging')
print('Release version validated:', version)
