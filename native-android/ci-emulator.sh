#!/usr/bin/env bash
set -euo pipefail
stak_root="$(cd "$(dirname "$0")/.." && pwd)"
collect_evidence() {
  mkdir -p "$stak_root/native-evidence"
  adb pull /sdcard/Android/data/app.stak.nativeapp/files/evidence "$stak_root/native-evidence/" || true
}
trap collect_evidence EXIT
cd "$stak_root/native-android"
./gradlew connectedDebugAndroidTest
