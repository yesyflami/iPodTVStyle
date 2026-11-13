#!/usr/bin/env bash
# Install the launcher on the TV and hand it the Home key - or put the stock launcher back and say
# exactly what happened. Windows: tools/install.ps1.
#
#   python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
#   TV_HOST=192.168.1.50 tools/install.sh
#
# Overrides:
#   SKIP_BUILD=1          install the APK that is already built
#   BUILD_ARGS=<args>     extra arguments for tools/build.sh, e.g.
#                         BUILD_ARGS=-PlauncherApplicationId=<package>
#   APP_ID=<package>      when the APK cannot name itself
#   STOCK_LAUNCHER=<pkg>  the launcher to disable (default: ask the TV; only a known one is accepted)
#   STOCK_LAUNCHER=none   keep the stock launcher and install ours as an ordinary app
#
# The order below is the point of this script, and it is adapted from CleanYourTV's
# launcher/build-and-install.sh (MIT): the APK is installed and verified first, the new launcher is
# started and verified second, a stock launcher is disabled only after that, and every step has a
# way back. A TV whose Home does not come up can only be rescued over ADB, so the way back is also
# written to a file next to where you ran this.
set -euo pipefail
cd "$(dirname "$0")/.."

die() { echo "ERROR: $*" >&2; exit 1; }
note() { echo "==> $*"; }

: "${TV_HOST:?set TV_HOST to the TV IP address, e.g.  TV_HOST=192.168.1.50 tools/install.sh}"

TOOLS="$(cd tools && pwd)"
APK=launcher/app/build/outputs/apk/release/app-release.apk
META="$(dirname "$APK")/output-metadata.json"
MAIN_ACTIVITY="${MAIN_ACTIVITY:-com.gathin.tvhome.MainActivity}"
# Ask the TV which app owns Home; only a launcher this script recognises is disabled by default.
# The answer can also be Android's chooser ("android"), a setup wizard, or another third-party
# launcher - none of which may be disabled here by accident.
KNOWN_STOCK_LAUNCHERS="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.mitv.tvhome"
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

# --- Python and the ADB client ---------------------------------------------------------------
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
if [ ! -x "$PY" ]; then
  note "first run: setting up a local Python environment (one minute, once)"
  HOST_PY="$(command -v python3 || command -v python || true)"
  [ -n "$HOST_PY" ] || die "Python 3 is not installed. Install it from https://www.python.org/downloads/
  (Windows: tick \"Add Python to PATH\" in the installer), then run this again."
  "$HOST_PY" -m venv "$TOOLS/venv" || die "could not create the Python environment in tools/venv.
  Nothing was installed on the TV."
  PY="$TOOLS/venv/bin/python"
  [ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
  [ -x "$PY" ] || die "the Python environment was created but its interpreter is missing."
  "$PY" -m pip install --quiet --disable-pip-version-check -r "$TOOLS/requirements.txt" \
    || die "could not download the Python packages the installer needs. Check your network and retry."
fi
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

note "TV: $TV_HOST"
tv --probe || die "could not reach the TV. Check TV_HOST, the 'Allow USB debugging' prompt on the TV,
  and that network debugging is still on (docs/ADAPT.md §2)."

# --- Pre-flight: the TV's SDK against the launcher's minSdk ----------------------------------
# Below minSdk, pm install answers INSTALL_FAILED_OLDER_SDK - and only after Gradle has spent a
# minute building. The number is read from the build file so it cannot drift from the code.
MIN_SDK="$(sed -n 's/^[[:space:]]*minSdk[[:space:]][[:space:]]*\([0-9]\{1,3\}\)[[:space:]]*$/\1/p' launcher/app/build.gradle 2>/dev/null | head -n 1 || true)"
TV_SDK="$(tv 'getprop ro.build.version.sdk' 2>/dev/null | tr -dc '0-9' || true)"
if [ -n "$MIN_SDK" ] && [ -n "$TV_SDK" ] && [ "$TV_SDK" -lt "$MIN_SDK" ]; then
  die "this TV runs SDK $TV_SDK and the launcher needs SDK $MIN_SDK or newer (minSdk in
  launcher/app/build.gradle). Nothing was built, installed or disabled."
fi

# --- Build ------------------------------------------------------------------------------------
if [ "${SKIP_BUILD:-0}" != 1 ]; then
  # BUILD_ARGS reaches build.sh untouched, e.g. BUILD_ARGS=-PlauncherApplicationId=<package> for a
  # firmware with a package-name HOME allowlist (docs/ADAPT.md §1).
  # shellcheck disable=SC2086
  "$TOOLS/build.sh" ${BUILD_ARGS:-}
fi
[ -f "$APK" ] || die "no APK at $APK. Run tools/build.sh first, or drop SKIP_BUILD=1."

# --- Which package did we just build? --------------------------------------------------------
# The identity is deliberately not a constant: see docs/ADAPT.md §4. The APK knows its own name.
APP_ID="${APP_ID:-}"
if [ -z "$APP_ID" ] && [ -f "$META" ]; then
  APP_ID="$(sed -n 's/.*"applicationId": *"\([^"]*\)".*/\1/p' "$META" | head -n 1)"
fi
if [ -z "$APP_ID" ]; then
  APP_ID="$(sed -n 's/^launcherApplicationId[[:space:]]*=[[:space:]]*//p' launcher/gradle.properties 2>/dev/null | tr -d ' \r' | head -n 1 || true)"
fi
[ -n "$APP_ID" ] || die "cannot tell which package this APK uses. Pass APP_ID=<package>.
  (The APK records it in $META.)"
note "package: $APP_ID   activity: $MAIN_ACTIVITY"

# --- Who owns Home right now? ----------------------------------------------------------------
# Disabling the stock launcher is OFF by default: it is the one step that can leave a TV with
# no home screen at all, and it is not needed on firmware that accepts any HOME app. Set
# DISABLE_STOCK=1 to opt in (that is what CleanYourTV's own installer does).
if [ "${DISABLE_STOCK:-0}" = 1 ] && [ -z "${STOCK_LAUNCHER:-}" ]; then
  HOME_PKG="$(tv "$HOME_QUERY" | tail -n 1 | tr -d '\r' | cut -d/ -f1)" || die "could not ask the
  TV which launcher owns Home. Pass STOCK_LAUNCHER=<package> yourself."
  case " $KNOWN_STOCK_LAUNCHERS " in
    *" $HOME_PKG "*) STOCK_LAUNCHER="$HOME_PKG" ;;
    *)
      die "Home resolves to '${HOME_PKG:-nothing}', which is not a launcher this script knows,
  so refusing to disable it by accident. Leave DISABLE_STOCK unset to keep it, or pass
  STOCK_LAUNCHER=<package> to name the one to disable." ;;
  esac
fi

if [ "${DISABLE_STOCK:-0}" = 1 ] && [ "${STOCK_LAUNCHER:-none}" != none ]; then
  note "stock launcher: $STOCK_LAUNCHER (will be disabled - DISABLE_STOCK=1)"
  DISABLE_STOCK_CMD="pm disable-user --user 0 $STOCK_LAUNCHER"
else
  STOCK_LAUNCHER=none
  note "stock launcher: left alone (default)"
  DISABLE_STOCK_CMD="echo stock launcher left alone"
fi

# --- Install (on its own, so a failed install cannot be followed by disabling the stock launcher)
note "uploading $(du -h "$APK" | cut -f1)"
"$PY" -I "$TOOLS/b64push.py" "$APK" /data/local/tmp/ipodtvstyle.apk
INSTALL_OUT="$(tv 'pm install -r /data/local/tmp/ipodtvstyle.apk; rm -f /data/local/tmp/ipodtvstyle.apk')"
echo "$INSTALL_OUT"
case "$INSTALL_OUT" in
  *Success*) ;;
  *INSTALL_FAILED_OLDER_SDK*)
    die "the APK is too new for this TV (INSTALL_FAILED_OLDER_SDK). The stock launcher was left
  untouched. Nothing else was changed." ;;
  *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
    die "a package called $APP_ID is already installed with a different signature - usually a build
  made on another computer. Uninstall that one first (it may be your Home app, so set Home back to
  the stock launcher BEFORE uninstalling), then run this again. The stock launcher was left
  untouched." ;;
  *INSTALL_FAILED_USER_RESTRICTED*|*INSTALL_FAILED_VERIFICATION_FAILURE*)
    die "the TV refused the install. On Coocaa/Skyworth sets an install confirmation appears on the
  TV screen ('继续安装' / Continue) and letting it time out produces exactly this answer. Accept it
  on the TV, keep the TV awake, and run this again. The stock launcher was left untouched." ;;
  *) die "pm install failed (see above). The stock launcher was left untouched." ;;
esac

# The APK names itself; believe the TV over our own guess.
if ! tv "pm list packages $APP_ID" | tr -d '\r' | grep -q "package:$APP_ID"; then
  die "the APK installed, but not as $APP_ID. Re-run with APP_ID=<the package it really uses>;
  nothing was disabled and Home is unchanged."
fi

# --- Start the new launcher and check it really came up --------------------------------------
# 'Status: ok' alone is not enough: some builds answer that for a task merely brought to the front,
# and an activity that cannot start still prints 'Error: Activity not started'.
if [ "$STOCK_LAUNCHER" = none ]; then
  START_CMD="am start -W -n $APP_ID/$MAIN_ACTIVITY"
else
  START_CMD="am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $APP_ID/$MAIN_ACTIVITY"
fi
START_OUT="$(tv "$START_CMD")"
echo "$START_OUT"
if ! printf '%s' "$START_OUT" | grep -qF 'Status: ok' || printf '%s' "$START_OUT" | grep -qi '^Error'; then
  die "the new launcher did not start (see above). $STOCK_LAUNCHER was left enabled and Home is
  unchanged, so the TV is exactly as it was."
fi

# --- Hand over Home -------------------------------------------------------------------------
# Order matters: the new launcher is already running, so disable the stock one, then point the
# preference at ours. Android drops the preference whenever the set of HOME apps changes.
note "handing the Home key to $APP_ID"
printf '%s\n' \
  "cmd package compile -m speed -f $APP_ID" \
  "$DISABLE_STOCK_CMD" \
  "cmd package set-home-activity --user 0 $APP_ID/$MAIN_ACTIVITY" \
  | tv --batch

query_home() { tv "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' || true; }

rollback_home() {
  if [ "$STOCK_LAUNCHER" = none ]; then
    echo "No stock launcher was disabled, so there is nothing to roll back." >&2
    return 1
  fi
  tv "pm enable --user 0 $STOCK_LAUNCHER" >/dev/null 2>&1 || true
  # set-home-activity wants pkg/activity, so let the TV resolve its own component.
  tv "cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK_LAUNCHER | tail -n 1)" >/dev/null 2>&1 || true
  BACK="$(query_home)"
  case "$BACK" in
    "$STOCK_LAUNCHER"/*) echo "$STOCK_LAUNCHER owns Home again." >&2; return 0 ;;
  esac
  REC="${HOME:-$PWD}/ipodtvstyle-recovery-$(printf '%s' "$TV_HOST" | tr -c 'A-Za-z0-9._-' '_').txt"
  {
    echo "# iPodTVStyle: the Home handover on $TV_HOST did not finish. From the repo root:"
    echo "TV_HOST=$TV_HOST tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 $STOCK_LAUNCHER'"
    echo "TV_HOST=$TV_HOST tools/venv/bin/python -I tools/tv.py 'cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK_LAUNCHER | tail -n 1)'"
    echo "# then check: TV_HOST=$TV_HOST tools/venv/bin/python -I tools/tv.py '$HOME_QUERY'"
  } > "$REC" 2>/dev/null || REC=""
  echo "ERROR: Home still resolves to '${BACK:-nothing}'." >&2
  if [ -n "$REC" ]; then
    echo "The commands that finish the recovery are in $REC - the TV still needs ADB for them." >&2
  else
    echo "Re-run once the TV answers: pm enable --user 0 $STOCK_LAUNCHER, then set its Home activity." >&2
  fi
  return 1
}

HOME_NOW="$(query_home)"
case "$HOME_NOW" in
  "$APP_ID"/*) note "Home now resolves to $HOME_NOW" ;;
  *)
    echo "ERROR: Home resolves to '${HOME_NOW:-nothing}', not to $APP_ID." >&2
    rollback_home || true
    echo "On some Coocaa/Skyworth firmware the physical Home key and the boot path use vendor
  properties, not Android's HOME preference: docs/ADAPT.md §5 covers what to do there. The
  launcher itself is installed and can be opened from the stock launcher's app list." >&2
    exit 1 ;;
esac

cat <<EOF

Installed. Press Home on the remote.

  package      : $APP_ID
  Home key     : $APP_ID/$MAIN_ACTIVITY
  stock        : $STOCK_LAUNCHER$( [ "$STOCK_LAUNCHER" = none ] && printf ' (left alone)' || true )

To go back:
  TV_HOST=$TV_HOST tools/venv/bin/python -I tools/tv.py "pm uninstall $APP_ID"
  # then pick your old launcher again on the TV, or force it:
  TV_HOST=$TV_HOST tools/venv/bin/python -I tools/tv.py '$HOME_QUERY'
When you are done with ADB, turn debugging off and check the port is closed:
  python3 tools/adb-closed.py $TV_HOST
EOF
