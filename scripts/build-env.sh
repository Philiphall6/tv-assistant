#!/usr/bin/env bash
# Shared build prerequisites; no credentials, hostnames or machine-specific paths.
set -euo pipefail
TVA_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TVA_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
: "${TVA_SDK:?Set ANDROID_SDK_ROOT to an Android SDK installation}"
TVA_TOOLS="$TVA_SDK/build-tools/36.1.0"
TVA_PLATFORM="$TVA_SDK/platforms/android-36/android.jar"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
for t in java javac jar python3; do command -v "$t" >/dev/null || { echo "Missing tool: $t" >&2; exit 1; }; done
for t in aapt2 d8 zipalign apksigner; do test -x "$TVA_TOOLS/$t" || { echo "Install Android build-tools 36.1.0" >&2; exit 1; }; done
test -f "$TVA_PLATFORM" || { echo "Install Android platform 36" >&2; exit 1; }
