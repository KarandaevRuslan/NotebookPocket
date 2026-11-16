#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# The complete official Wrapper is bundled; no system Gradle is required.
if [[ ! -f gradle/wrapper/gradle-wrapper.jar || ! -f gradle/wrapper/gradle-wrapper.properties ]]; then
  echo "Gradle Wrapper is incomplete. Extract the complete source archive." >&2
  exit 1
fi
if [[ $# -eq 0 ]]; then set -- --version; fi
exec bash ./gradlew "$@"
