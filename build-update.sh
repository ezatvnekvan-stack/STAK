#!/usr/bin/env sh
# Official signed update export. Contributor builds use ./gradlew directly.
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -f cloud-config.env ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      STAK_CLOUD_URL=*) export STAK_CLOUD_URL="${line#*=}" ;;
      STAK_CLOUD_PUBLIC_KEY=*) export STAK_CLOUD_PUBLIC_KEY="${line#*=}" ;;
    esac
  done < cloud-config.env
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${STAK_KEYSTORE_PATH:=$PWD/signing/stak-preview.keystore}"
export STAK_KEYSTORE_PATH
[ -n "$ANDROID_HOME" ] || { echo 'Set ANDROID_HOME to Android SDK with build-tools 35.0.0.' >&2; exit 1; }
[ -f "$STAK_KEYSTORE_PATH" ] || { echo 'Original private signing key is required for an official update.' >&2; exit 1; }
case "${STAK_CLOUD_URL:-}" in https://*) ;; *) echo 'Set the existing STAK_CLOUD_URL.' >&2; exit 1;; esac
case "${STAK_CLOUD_PUBLIC_KEY:-}" in sb_publishable_*) ;; *) echo 'Use the existing publishable cloud key, never an admin key.' >&2; exit 1;; esac
./gradlew :app:assemblePreview "$@"
apk=app/build/outputs/apk/preview/app-preview.apk
tools="$ANDROID_HOME/build-tools/35.0.0"
expected=bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430
certificate="$("$tools/apksigner" verify --print-certs "$apk")"
printf '%s\n' "$certificate" | grep -F "$expected" >/dev/null || { echo 'Wrong signing identity; refusing export.' >&2; exit 1; }
package="$("$tools/aapt" dump badging "$apk" | sed -n '1p')"
printf '%s\n' "$package" | grep -F "name='app.stak.nativeapp.parity'" >/dev/null || { echo 'Wrong application ID.' >&2; exit 1; }
code="$(printf '%s\n' "$package" | sed -n "s/.*versionCode='\([0-9][0-9]*\)'.*/\1/p")"
version="$(printf '%s\n' "$package" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
[ -n "$code" ] && [ "$code" -ge 70 ] || { echo 'Invalid or stale version code.' >&2; exit 1; }
mkdir -p distribution
cp "$apk" distribution/STAK-current.apk
printf 'Verified official update: distribution/STAK-current.apk (%s, code %s)\n' "$version" "$code"
