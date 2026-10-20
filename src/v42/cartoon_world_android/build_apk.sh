#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -x ./gradlew ]; then
  ./gradlew assembleDebug
else
  echo "gradlew not found. Open this folder in Android Studio or install Gradle 8.9+." >&2
  exit 2
fi
printf '\nAPK output: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
