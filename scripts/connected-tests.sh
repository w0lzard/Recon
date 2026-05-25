#!/usr/bin/env bash
# Runs connectedDebugAndroidTest and fails unless the fresh XML report ran exactly the expected
# number of tests, all passing. Gradle can say BUILD SUCCESSFUL after an install failure with zero
# tests run, and an old report would hide that, so old results are deleted first.
# Usage: scripts/connected-tests.sh [expected]   (default: @Test count in app/src/androidTest)
set -euo pipefail
cd "$(dirname "$0")/.."
dir=app/build/outputs/androidTest-results/connected/debug
expected=${1:-$(grep -rhoE '@Test\b' app/src/androidTest | wc -l)}
rm -rf "$dir"
# A different build already on the device (gate/release, other signature) made the engine skip silently; start clean.
adb=${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb
for p in dev.wolzard.recon dev.wolzard.recon.test; do "$adb" uninstall "$p" >/dev/null 2>&1 || true; done
./gradlew :app:connectedDebugAndroidTest --console=plain || true   # verdict comes from the report below
shopt -s nullglob
xmls=("$dir"/TEST-*.xml)
(( ${#xmls[@]} )) || { echo "FAIL: no XML report in $dir"; exit 1; }
read -r tests failures errors skipped < <(grep -ho '<testsuite [^>]*>' "${xmls[@]}" | awk '
  { for (i = 1; i <= NF; i++) if (split($i, kv, "=") == 2) { gsub(/"/, "", kv[2]); s[kv[1]] += kv[2] } }
  END { print s["tests"]+0, s["failures"]+0, s["errors"]+0, s["skipped"]+0 }')
echo "connected tests: ran=$tests expected=$expected failures=$failures errors=$errors skipped=$skipped"
[[ $tests -eq $expected && $failures -eq 0 && $errors -eq 0 && $skipped -eq 0 ]] || { echo "FAIL"; exit 1; }
echo OK
