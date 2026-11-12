#!/usr/bin/env bash
# Build the release APK.
#
# Nothing here is pinned to one computer: the JDK and the Android SDK are looked up in the places
# they normally live, and each candidate is checked before it is trusted. Windows: tools/build.ps1.
#
#   tools/build.sh
#   tools/build.sh -PlauncherApplicationId=com.coocaa.cleanhome   # firmware with a HOME allowlist
set -euo pipefail
cd "$(dirname "$0")/../launcher"

die() { echo "ERROR: $*" >&2; exit 1; }

# --- JDK -------------------------------------------------------------------------------------
# Gradle 8.9 stops on JDK 23+ ("Unsupported class file major version"), and a JRE is not enough:
# the Android Gradle plugin needs jlink. So every candidate must be a JDK, not just a java binary.
find_jdk() {
  local candidate wanted
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jlink" ]; then
    printf '%s\n' "$JAVA_HOME"; return 0
  fi
  if [ -x /usr/libexec/java_home ]; then                       # macOS
    for wanted in 17 21 22; do
      candidate="$(/usr/libexec/java_home -v "$wanted" 2>/dev/null || true)"
      if [ -n "$candidate" ] && [ -x "$candidate/bin/jlink" ]; then
        printf '%s\n' "$candidate"; return 0
      fi
    done
  fi
  for candidate in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17; do
    if [ -x "$candidate/bin/jlink" ]; then printf '%s\n' "$candidate"; return 0; fi
  done
  if command -v java >/dev/null 2>&1; then
    candidate="$(java -XshowSettings:properties -version 2>&1 | awk '/^ *java.home = / {print $3}')"
    if [ -n "${candidate:-}" ] && [ -x "$candidate/bin/jlink" ]; then
      printf '%s\n' "$candidate"; return 0
    fi
  fi
  return 1
}

JAVA_HOME="$(find_jdk || true)"
[ -n "$JAVA_HOME" ] || die "no JDK 17-22 found. Install Temurin 17 or set JAVA_HOME to a JDK.
  A JRE does not work: the Android Gradle plugin runs jlink."
export JAVA_HOME
JAVA_MAJOR="$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)"
case "$JAVA_MAJOR" in
  17|21|22) ;;
  *) die "JDK $JAVA_MAJOR at $JAVA_HOME: Gradle 8.9 needs 17, 21 or 22 (23+ stops with
  'Unsupported class file major version'). Point JAVA_HOME at a JDK 17." ;;
esac

# --- Android SDK -----------------------------------------------------------------------------
find_sdk() {
  local candidate
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
      "${HOME:-}/Library/Android/sdk" \
      "${HOME:-}/Android/Sdk" \
      "${LOCALAPPDATA:-}/Android/Sdk" \
      "${HOME:-}/AppData/Local/Android/Sdk" \
      /opt/homebrew/share/android-commandlinetools \
      /usr/local/share/android-commandlinetools; do
    if [ -n "$candidate" ] && [ -d "$candidate/platforms" ]; then
      printf '%s\n' "$candidate"; return 0
    fi
  done
  return 1
}

ANDROID_HOME="$(find_sdk || true)"
[ -n "$ANDROID_HOME" ] || die "no Android SDK found. Install Android Studio, or the command-line
  tools plus  sdkmanager 'platforms;android-34' 'build-tools;34.0.0' , then set ANDROID_HOME."
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ ! -d "$ANDROID_HOME/platforms/android-34" ]; then
  echo "WARNING: $ANDROID_HOME/platforms/android-34 is missing; this build targets SDK 34." >&2
  echo "  sdkmanager \"platforms;android-34\" \"build-tools;34.0.0\"" >&2
fi

# local.properties is a Java properties file, where a backslash is an escape: Git Bash on Windows
# hands us C:\Users\...\Sdk. Forward slashes work on every platform, so write those.
if [ ! -f local.properties ]; then
  printf 'sdk.dir=%s\n' "$(printf '%s' "$ANDROID_HOME" | tr '\\' '/')" > local.properties
fi

echo "JAVA_HOME    = $JAVA_HOME"
echo "ANDROID_HOME = $ANDROID_HOME"
echo "applicationId= $(sed -n 's/^launcherApplicationId[[:space:]]*=[[:space:]]*//p' gradle.properties 2>/dev/null | tr -d '\r' || true)"

./gradlew assembleRelease --console=plain "$@"

APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || die "the build finished but $APK is missing."
echo
echo "APK    : $(pwd)/$APK ($(du -h "$APK" | cut -f1))"
echo "Next   : TV_HOST=192.168.1.50 tools/install.sh"
