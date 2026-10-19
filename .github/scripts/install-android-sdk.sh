#!/usr/bin/env bash
# Installs the Android SDK platform matching compileSdk plus build tools.
set -euo pipefail

SDKMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  SDKMANAGER="$(command -v sdkmanager)"
fi

COMPILE_SDK="$(
  python3 - <<'PY'
import tomllib
with open("gradle/libs.versions.toml", "rb") as f:
    print(tomllib.load(f)["versions"]["compileSdk"])
PY
)"

echo "compileSdk=${COMPILE_SDK}"
yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" "platform-tools" "platforms;android-${COMPILE_SDK}" "build-tools;36.0.0"
