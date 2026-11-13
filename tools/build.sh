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
  local candidate version home

  # 1 · JAVA_HOME wins when it really is a JDK (a JRE has no jlink).
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jlink" ]; then
    printf '%s\n' "$JAVA_HOME"; return 0
  fi

  # 2 · macOS registry.
  if [ -x /usr/libexec/java_home ]; then
    for version in 17 21 22 18 19 20; do
      candidate="$(/usr/libexec/java_home -v "$version" 2>/dev/null || true)"
      if [ -n "$candidate" ] && [ -x "$candidate/bin/jlink" ]; then
        printf '%s\n' "$candidate"; return 0
      fi
    done
  fi

  # 3 · Where JDKs actually live. Android Studio's bundled JBR is a full JDK with jlink and is
  # the only JDK many machines have -- dropping it was why this script started failing on
  # machines that used to build fine.
  for candidate in \
      "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
      "${HOME:-}/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
      "${LOCALAPPDATA:-}/Programs/Android Studio/jbr" \
      "${PROGRAMFILES:-}/Android/Android Studio/jbr" \
      "${HOME:-}/android-studio/jbr" /opt/android-studio/jbr \
      /usr/lib/jvm/temurin-17-jdk-amd64 /usr/lib/jvm/temurin-21-jdk-amd64 \
      /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-21-openjdk-amd64 \
      /usr/lib/jvm/default-java \
      /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 \
      /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21 \
      "${HOME:-}/.sdkman/candidates/java/current" \
      ${HOME:-}/.sdkman/candidates/java/* \
      ${HOME:-}/.gradle/jdks/* \
      ${HOME:-}/.jdks/* \
      ${HOME:-}/Library/Java/JavaVirtualMachines/*/Contents/Home \
      "/Library/Java/JavaVirtualMachines/"*/Contents/Home; do
    [ -n "$candidate" ] || continue
    if [ -x "$candidate/bin/jlink" ]; then printf '%s\n' "$candidate"; return 0; fi
  done

  # 4 · Last resort: whatever java is on PATH. sed keeps paths with spaces intact.
  if command -v java >/dev/null 2>&1; then
    home="$(java -XshowSettings:properties -version 2>&1 \
      | sed -n 's/^[[:space:]]*java\.home = //p' | head -n 1)"
    if [ -n "${home:-}" ] && [ -x "$home/bin/jlink" ]; then
      printf '%s\n' "$home"; return 0
    fi
  fi
  return 1
}

JAVA_HOME="$(find_jdk || true)"
[ -n "$JAVA_HOME" ] || die "no JDK 17-22 found. A JRE does not work: the Android Gradle
  plugin runs jlink. Pick one:
    · Installed Android Studio? Its bundled JBR is a full JDK -- re-run this script, it is
      already checked at /Applications/Android Studio.app/Contents/jbr/Contents/Home
    · Otherwise install Temurin 17 from https://adoptium.net/temurin/releases/?version=17
    · Already have one? export JAVA_HOME=/path/to/jdk  (must contain bin/jlink)
  Check with:  \"\$JAVA_HOME/bin/jlink\" --version"
export JAVA_HOME
JAVA_MAJOR="$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)"
case "$JAVA_MAJOR" in
  17|18|19|20|21|22) ;;
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
