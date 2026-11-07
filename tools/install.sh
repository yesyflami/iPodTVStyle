#!/usr/bin/env bash
# Install the freshly built APK on the TV and hand it the HOME role.
#
#   tools/install.sh 192.168.1.50:5555
#
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE/../launcher/app/build/outputs/apk/release/app-release.apk"
DEVICE="${1:-}"

if ! command -v adb >/dev/null; then
  echo "adb not found. Install platform-tools, or use the adb that ships with Android Studio." >&2
  exit 1
fi
if [ ! -f "$APK" ]; then
  echo "APK missing — run tools/build.sh first." >&2
  exit 1
fi

if [ -n "$DEVICE" ]; then
  echo "==> adb connect $DEVICE"
  adb connect "$DEVICE"
fi

echo "==> devices"
adb devices

PKG=$(grep -E '^homeApplicationId' "$HERE/../launcher/gradle.properties" | cut -d= -f2 | tr -d ' ')
echo "==> package: $PKG"

echo "==> installing"
adb install -r "$APK"

echo "==> launching"
adb shell am start -n "$PKG/com.gathin.tvhome.MainActivity" || true

echo
echo "Now press HOME on the remote and pick iPodTVStyle, then choose 'Always'."
echo "If no chooser appears, run:"
echo "  adb shell cmd package set-home-activity $PKG/com.gathin.tvhome.MainActivity"
echo
echo "To go back to the stock launcher later:"
echo "  adb uninstall $PKG"
