#!/bin/sh
set -eu
runner=dev.pocket.notebook.test/androidx.test.runner.AndroidJUnitRunner
class=dev.pocket.notebook.data.DraftProcessDeathTest
run_test() {
    result=$(adb shell am instrument -w -e class "$class#$1" "$runner")
    printf '%s\n' "$result"
    printf '%s\n' "$result" | tr -d '\r' | grep -q 'OK (1 test)'
    if printf '%s\n' "$result" | grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'; then return 1; fi
}
run_test a_checkpointBeforeKill
adb shell am force-stop dev.pocket.notebook
run_test b_restoreAfterKill
