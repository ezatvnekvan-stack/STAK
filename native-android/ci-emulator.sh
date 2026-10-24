#!/usr/bin/env bash
set -euo pipefail
stak_root="$(cd "$(dirname "$0")/.." && pwd)"
variant="${1:-debug}"
variant_cap="${variant^}"
pull_evidence() {
  mkdir -p "$stak_root/native-evidence/$variant"
  adb pull /sdcard/Pictures/STAK-native-evidence "$stak_root/native-evidence/$variant/" || true
  adb pull /sdcard/Download/STAK-native-metrics "$stak_root/native-evidence/$variant/" || true
}
collect_evidence() {
  pull_evidence
  for stak_pid in "${stak_logcat_pid:-}" "${stak_snapshot_pid:-}"; do
    if [[ -n "$stak_pid" ]]; then kill "$stak_pid" 2>/dev/null || true; fi
  done
}
trap collect_evidence EXIT
cd "$stak_root/native-android"
adb shell settings put system font_scale 1.0
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
adb shell wm density 160
adb shell wm size 412x928
adb shell rm -rf /sdcard/Pictures/STAK-native-evidence /sdcard/Download/STAK-native-metrics
mkdir -p "$stak_root/native-evidence/$variant"
adb logcat -v threadtime > "$stak_root/native-evidence/$variant/full-logcat.txt" 2>&1 &
stak_logcat_pid=$!
# Retain completed captures/metrics even if the VM later disconnects.
(
  trap - EXIT
  while sleep 20 && adb get-state >/dev/null 2>&1; do
    pull_evidence > "$stak_root/native-evidence/$variant/periodic-pull.log" 2>&1
  done
) &
stak_snapshot_pid=$!
rm -rf app/build/outputs/androidTest-results app/build/reports/androidTests
stak_test_status=0
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" -PstakTestBuildType="$variant" "connected${variant_cap}AndroidTest" || stak_test_status=$?
# Gradle replaces these reports on the second invocation; retain the functional run.
mkdir -p "$stak_root/native-evidence/$variant/first-pass"
cp -R app/build/reports/androidTests "$stak_root/native-evidence/$variant/first-pass/reports"
cp -R app/build/outputs/androidTest-results "$stak_root/native-evidence/$variant/first-pass/results"
adb shell wm size 360x928
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" -PstakTestBuildType="$variant" "connected${variant_cap}AndroidTest" -Pandroid.testInstrumentationRunnerArguments.class=app.stak.nativeapp.OriginalReferenceTest,app.stak.nativeapp.BetaUiTest,app.stak.nativeapp.SourceFlowTest,app.stak.nativeapp.SourcesMotionTest,app.stak.nativeapp.GlassUiTest || stak_test_status=$?
mkdir -p "$stak_root/native-evidence/$variant/second-pass"
cp -R app/build/outputs/androidTest-results "$stak_root/native-evidence/$variant/second-pass/results"
exit "$stak_test_status"
