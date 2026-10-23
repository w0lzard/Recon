#!/usr/bin/env bash
# Runs connectedDebugAndroidTest and fails unless the fresh XML report ran exactly the expected
# number of tests, all passing. Gradle can say BUILD SUCCESSFUL after an install failure with zero
# tests run, and an old report would hide that, so old results are deleted first.
# Usage: scripts/connected-tests.sh [expected] [extra gradlew args...]
set -euo pipefail
cd "$(dirname "$0")/.."
dir=app/build/outputs/androidTest-results/connected/debug
expected=${1:-$(grep -rhoE '@Test\b' app/src/androidTest | wc -l)}
rm -rf "$dir"

adb=${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb

# Refuse to run if ANDROID_SERIAL is a physical device (emulators only)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  if [[ ! "$ANDROID_SERIAL" =~ ^emulator- ]]; then
    qemu=$("$adb" -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu 2>/dev/null || true)
    if [[ "$qemu" != "1" ]]; then
      echo "ERROR: Refusing to run tests on physical device ($ANDROID_SERIAL). Emulators only." >&2
      exit 1
    fi
  fi
fi

# A different build already on the device (gate/release, other signature) made the engine skip silently; start clean.
for p in dev.wolzard.recon dev.wolzard.recon.test; do "$adb" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} uninstall "$p" >/dev/null 2>&1 || true; done
./gradlew :app:connectedDebugAndroidTest --console=plain "${@:2}" || true   # verdict comes from the report below
shopt -s nullglob
xmls=("$dir"/TEST-*.xml)
(( ${#xmls[@]} )) || { echo "FAIL: no XML report in $dir"; exit 1; }
read -r tests failures errors skipped < <(grep -ho '<testsuite [^>]*>' "${xmls[@]}" | awk '
  { for (i = 1; i <= NF; i++) if (split($i, kv, "=") == 2) { gsub(/"/, "", kv[2]); s[kv[1]] += kv[2] } }
  END { print s["tests"]+0, s["failures"]+0, s["errors"]+0, s["skipped"]+0 }')
# Under the test orchestrator an assumption failure (live opt-in tests) is written as <failure>; count it as skipped.
assumed=$(grep -ho '<failure[^>]*AssumptionViolatedException' "${xmls[@]}" | wc -l)
failures=$((failures - assumed)); skipped=$((skipped + assumed))
# Pull matrix artifacts if present
mkdir -p app/build/matrix-artifacts
"$adb" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} pull /sdcard/Download/matrix-artifacts/. app/build/matrix-artifacts/ >/dev/null 2>&1 || true
"$adb" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} pull /sdcard/Android/data/dev.wolzard.recon/files/matrix-artifacts/. app/build/matrix-artifacts/ >/dev/null 2>&1 || true
"$adb" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} pull /data/local/tmp/matrix-artifacts/. app/build/matrix-artifacts/ >/dev/null 2>&1 || true
if compgen -G "app/build/matrix-artifacts/*" > /dev/null; then
  echo "matrix artifacts saved to app/build/matrix-artifacts:"
  ls -la app/build/matrix-artifacts
fi

echo "connected tests: ran=$tests expected=$expected failures=$failures errors=$errors skipped=$skipped"
[[ $tests -eq $expected && $failures -eq 0 && $errors -eq 0 && $skipped -le ${MAX_SKIPPED:-0} ]] || { echo "FAIL"; exit 1; }
echo OK
