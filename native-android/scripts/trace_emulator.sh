#!/usr/bin/env bash
set -euo pipefail
# Run after emulator-runner installs its pinned binary. Retain the actual host
# exit code if the VM disappears; kernel logs alone miss handled native crashes.
stak_emulator="${ANDROID_HOME:?}/emulator/emulator"
if [[ ! -f "$stak_emulator.stak-real" ]]; then
  mv "$stak_emulator" "$stak_emulator.stak-real"
fi
cat > "$stak_emulator" <<'WRAPPER'
#!/usr/bin/env bash
set -uo pipefail
stak_launch=false
for stak_arg in "$@"; do [[ "$stak_arg" == "-avd" ]] && stak_launch=true; done
if [[ "$stak_launch" == false ]]; then exec "$0.stak-real" "$@"; fi
stak_diagnostics="${STAK_EMULATOR_DIAGNOSTICS:-${GITHUB_WORKSPACE:?}/native-runner-diagnostics}"
mkdir -p "$stak_diagnostics"
"$0.stak-real" "$@" > >(tee "$stak_diagnostics/emulator.log") 2>&1 &
stak_emulator_pid=$!
printf '%s\n' "$stak_emulator_pid" > "$stak_diagnostics/emulator.pid"
trap 'kill -TERM "$stak_emulator_pid" 2>/dev/null || true' TERM INT
wait "$stak_emulator_pid"
stak_exit=$?
printf '%s\n' "$stak_exit" > "$stak_diagnostics/emulator.exit-code"
exit "$stak_exit"
WRAPPER
chmod +x "$stak_emulator"
