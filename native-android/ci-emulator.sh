#!/usr/bin/env bash
set -euo pipefail
stak_root="$(cd "$(dirname "$0")/.." && pwd)"
collect_evidence() {
  mkdir -p "$stak_root/native-evidence"
  adb pull /sdcard/Pictures/STAK-native-evidence "$stak_root/native-evidence/" || true
}
trap collect_evidence EXIT
cd "$stak_root/native-android"
adb shell settings put system font_scale 1.0
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
adb shell wm density 160
adb shell wm size 412x928
stak_test_status=0
./gradlew connectedDebugAndroidTest || stak_test_status=$?
# Gradle replaces these reports on the second invocation; retain the functional run.
mkdir -p "$stak_root/native-evidence/first-pass"
cp -R app/build/reports/androidTests "$stak_root/native-evidence/first-pass/reports"
cp -R app/build/outputs/androidTest-results "$stak_root/native-evidence/first-pass/results"
adb shell wm size 360x928
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.stak.nativeapp.OriginalReferenceTest,app.stak.nativeapp.BetaUiTest || stak_test_status=$?
exit "$stak_test_status"
