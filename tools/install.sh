#!/usr/bin/env bash
# Install iPodTVStyle on a TV. Needs nothing but a Python 3 on the computer and a TV on the
# same network -- no JDK, no Android SDK, no adb, no Gradle. The APK is prebuilt in prebuilt/.
#
#   tools/install.sh 192.168.1.50
#   TV_HOST=192.168.1.50 tools/install.sh
#
#   tools/install.sh 192.168.1.50 --uninstall     put the TV back
#
# It works through the APK variants in prebuilt/ until the TV accepts one as Home: firmware
# that only hands Home to a package name on its own allow-list will refuse the neutral name
# and accept the vendor one. Nothing is disabled; the stock launcher keeps working.
set -euo pipefail

TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(dirname "$TOOLS_DIR")"
PREBUILT="$REPO_DIR/prebuilt"
MAIN_ACTIVITY="com.gathin.tvhome.MainActivity"

die() { echo "ERROR: $*" >&2; exit 1; }
note() { echo "==> $*"; }

pkg_of() {
  # Every variant carries its package name in the zip's manifest, but parsing that needs a tool
  # we do not want to require. Keep a sidecar file next to each APK instead.
  local apk="$1" sidecar="${1%.apk}.package"
  [ -f "$sidecar" ] && tr -d ' \r\n' < "$sidecar"
}


# --- arguments -------------------------------------------------------------------------------
TV_HOST="${TV_HOST:-}"
ACTION=install
for arg in "$@"; do
  case "$arg" in
    --uninstall) ACTION=uninstall ;;
    --keep-stock|--disable-stock) : ;;   # accepted for compatibility, both are no-ops now
    -*) die "unknown option: $arg" ;;
    *)  TV_HOST="$arg" ;;
  esac
done
[ -n "$TV_HOST" ] || die "tell me the TV address:
  tools/install.sh 192.168.1.50        (Windows: tools\\install.ps1 192.168.1.50)
Find it on the TV under Settings > Network > Wi-Fi > network details."
TV_HOST="${TV_HOST%%:*}"
export TV_HOST

# --- Python + the ADB transport ---------------------------------------------------------------
PY="$TOOLS_DIR/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS_DIR/venv/Scripts/python.exe"
if [ ! -x "$PY" ]; then
  HOST_PY="$(command -v python3 || command -v python || true)"
  [ -n "$HOST_PY" ] || die "Python 3 is not installed. Get it from https://www.python.org/downloads/
  (on Windows tick \\"Add Python to PATH\\" in the installer), then run this again."
  note "first run: setting up a small Python environment (one minute, once)"
  "$HOST_PY" -m venv "$TOOLS_DIR/venv" || die "could not create the Python environment."
  PY="$TOOLS_DIR/venv/bin/python"
  [ -x "$PY" ] || PY="$TOOLS_DIR/venv/Scripts/python.exe"
  [ -x "$PY" ] || die "the Python environment was created but its interpreter is missing."
  "$PY" -m pip install --quiet --disable-pip-version-check -r "$TOOLS_DIR/requirements.txt" \
    || die "could not download the two Python packages the installer needs. Check the network."
fi
tv() { "$PY" -I "$TOOLS_DIR/tv.py" "$@"; }

# --- uninstall -------------------------------------------------------------------------------
if [ "$ACTION" = uninstall ]; then
  note "TV: $TV_HOST"
  tv --probe >/dev/null 2>&1 || die "could not reach the TV at $TV_HOST."
  for apk in "$PREBUILT"/*.apk; do
    [ -f "$apk" ] || continue
    pkg="$(pkg_of "$apk")"
    tv "pm uninstall $pkg" >/dev/null 2>&1 && note "removed $pkg" || true
  done
  note "done. Press Home on the remote; if the TV asks which launcher, pick your old one."
  exit 0
fi

# --- reach the TV ----------------------------------------------------------------------------
note "TV: $TV_HOST"
tv --probe >/dev/null 2>&1 || die "could not reach $TV_HOST.
  · is the TV on, and on the same network as this computer?
  · is Settings > Developer options > Network debugging switched on?
  · the first connection shows an 'Allow debugging' prompt on the TV -- accept it."
SDK="$(tv 'getprop ro.build.version.sdk' 2>/dev/null | tr -dc '0-9' || true)"
[ -n "$SDK" ] || die "connected, but could not read the TV's Android version."
[ "$SDK" -ge 26 ] || die "this TV runs Android SDK $SDK; iPodTVStyle needs 26 (Android 8.0) or newer."

# --- which package names to try ---------------------------------------------------------------
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
query_home() { tv "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' || true; }

ORDER="${VARIANTS:-ipodtvstyle-neutral ipodtvstyle-skyworth ipodtvstyle-xiaomi ipodtvstyle-tcl}"

note "installing"
WINNER=""
for name in $ORDER; do
  apk="$PREBUILT/$name.apk"
  [ -f "$apk" ] || continue
  pkg="$(pkg_of "$apk")"
  [ -n "$pkg" ] || continue

  "$PY" -I "$TOOLS_DIR/b64push.py" "$apk" /data/local/tmp/ipodtvstyle.apk >/dev/null 2>&1 \
    || die "could not upload the APK to the TV."
  OUT="$(tv 'pm install -r /data/local/tmp/ipodtvstyle.apk; rm -f /data/local/tmp/ipodtvstyle.apk' 2>&1 || true)"
  case "$OUT" in
    *Success*) ;;
    *) note "  $pkg: install refused, trying the next variant"; continue ;;
  esac

  tv "cmd package compile -m speed -f $pkg" >/dev/null 2>&1 || true
  tv "cmd package set-home-activity --user 0 $pkg/$MAIN_ACTIVITY" >/dev/null 2>&1 || true
  tv "am start -n $pkg/$MAIN_ACTIVITY" >/dev/null 2>&1 || true
  sleep 2

  # resolve-activity only echoes what set-home-activity just wrote. Coocaa/Skyworth firmware
  # routes the physical Home key through vendor properties and ignores that value, so press the
  # key and see who is actually in front -- that is the only answer that matters.
  tv 'input keyevent KEYCODE_HOME' >/dev/null 2>&1 || true
  sleep 2
  FOCUS="$(tv 'dumpsys window | grep mCurrentFocus' 2>/dev/null | head -n 1 || true)"
  case "$FOCUS" in
    *"$pkg"*) WINNER="$pkg"; note "  $pkg: Home key lands here"; break ;;
    *)
      note "  $pkg: installed, but the Home key still goes to the stock launcher"
      tv "pm uninstall $pkg" >/dev/null 2>&1 || true
      ;;
  esac
done

if [ -z "$WINNER" ]; then
  cat >&2 <<'EOF'
ERROR: every variant installed cleanly, but none was allowed to become Home.

This TV only hands Home to a package name on its own allow-list, and none of the
names shipped in prebuilt/ is on it. Nothing was left behind (each attempt was
uninstalled) and your stock launcher is untouched.

Options:
  · Contribute your model: docs/devices/README.md shows what to record.
  · Build a variant with your TV's own Home package name:
      tools/build.sh -PlauncherApplicationId=<the name>      (needs a JDK + Android SDK)
    Find that name with, on the TV:  pm list packages | grep -i launch
EOF
  exit 1
fi

note "done"
cat <<EOF

  package   : $WINNER
  Home key  : $WINNER/$MAIN_ACTIVITY

Press Home on the remote. If the TV asks which launcher to use, pick iPodTVStyle
and choose "Always".

To undo everything:
  tools/install.sh $TV_HOST --uninstall
  (Windows: tools\\install.ps1 $TV_HOST -Uninstall)

When you are done with debugging, turn it off on the TV:
Settings > Developer options > Network debugging.
EOF
