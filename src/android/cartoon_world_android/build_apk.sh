#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed. Open this folder in Android Studio or install Gradle 8.9+ and configure Android SDK Platform 35." >&2
  exit 2
fi
gradle assembleDebug
printf '\nAPK output: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
