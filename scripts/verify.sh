#!/bin/sh
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root/web"
npm ci --no-audit --no-fund
npm test
npm run test:upstream
npm audit --audit-level=high
npm run build
cd "$project_root"
python3 scripts/check_repository.py
python3 -m unittest discover -s tests -p '*Test.py'
node tests/ResourceLocalizationTest.mjs
node tests/WorkspaceUXTest.mjs
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
