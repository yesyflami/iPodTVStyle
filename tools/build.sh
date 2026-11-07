#!/usr/bin/env bash
# Build the release APK. Reads JAVA_HOME / ANDROID_HOME from the environment,
# or falls back to a couple of common locations.
set -euo pipefail
cd "$(dirname "$0")/../launcher"

: "${JAVA_HOME:=/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
export JAVA_HOME ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "JDK not found at JAVA_HOME=$JAVA_HOME — set it to a JDK 17 install." >&2
  exit 1
fi
if [ ! -d "$ANDROID_HOME/platforms" ]; then
  echo "Android SDK not found at ANDROID_HOME=$ANDROID_HOME" >&2
  exit 1
fi

echo "JAVA_HOME   = $JAVA_HOME"
echo "ANDROID_HOME= $ANDROID_HOME"
echo "homeApplicationId = $(grep -E '^homeApplicationId' gradle.properties | cut -d= -f2)"

./gradlew assembleRelease "$@"

APK=app/build/outputs/apk/release/app-release.apk
echo
echo "APK: $(pwd)/$APK ($(du -h "$APK" | cut -f1))"
echo "Next: tools/install.sh"
