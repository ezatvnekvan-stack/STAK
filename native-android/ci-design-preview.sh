#!/usr/bin/env bash
set -euo pipefail
stak_root="$(cd "$(dirname "$0")/.." && pwd)"
stak_preview="$stak_root/native-design-preview"
mkdir -p "$stak_preview"
pull_design() {
  adb pull /sdcard/Pictures/STAK-native-evidence "$stak_preview/" || true
}
collect_design() {
  if [[ -n "${stak_snapshot_pid:-}" ]]; then kill "$stak_snapshot_pid" 2>/dev/null || true; fi
  pull_design
}
trap collect_design EXIT
adb shell settings put system font_scale 1.0
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
adb shell wm density 160
adb shell wm size 412x928
adb shell rm -rf /sdcard/Pictures/STAK-native-evidence
(
  trap - EXIT
  while sleep 10 && adb get-state >/dev/null 2>&1; do pull_design > "$stak_preview/pull.log" 2>&1; done
) &
stak_snapshot_pid=$!
cd "$stak_root/native-android"
stak_status=0
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.stak.nativeapp.OriginalReferenceTest || stak_status=$?
cp -R app/build/outputs/androidTest-results "$stak_preview/results"
cp -R app/build/reports/androidTests "$stak_preview/reports"
exit "$stak_status"
