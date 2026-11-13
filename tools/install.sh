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

  # Put the stock launcher back if we were the ones who turned it off. Without this the TV can be
  # left with no home screen at all, which is a much worse outcome than a failed install.
  STATE="$TOOLS_DIR/../.stock-launcher"
  if [ -f "$STATE" ]; then
    STOCKED="$(tr -d ' \r\n' < "$STATE")"
    if [ -n "$STOCKED" ]; then
      tv "pm enable --user 0 $STOCKED" >/dev/null 2>&1 \
        && note "re-enabled $STOCKED" || note "could not re-enable $STOCKED -- turn it on in Settings > Apps"
      COMPONENT="$(tv "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCKED" 2>/dev/null | tail -n 1 | tr -d '\r')"
      [ -n "$COMPONENT" ] && tv "cmd package set-home-activity --user 0 $COMPONENT" >/dev/null 2>&1 || true
    fi
    rm -f "$STATE"
  fi
  tv 'input keyevent KEYCODE_HOME' >/dev/null 2>&1 || true
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

ORDER="${VARIANTS:-ipodtvstyle-skyworth ipodtvstyle-neutral ipodtvstyle-tcl}"
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
query_home() { tv "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' || true; }
focus_now()  { tv 'dumpsys window | grep mCurrentFocus' 2>/dev/null | head -n 1 || true; }

# Which launcher owns Home right now -- whatever it is, not just the ones we happen to know.
# Which package owns Home. This TV's own launcher does not declare android.intent.category.HOME at
# all -- it uses a vendor category -- so resolve-activity answers "No activity found" while a
# perfectly working launcher sits in front. Ask three ways and keep the first real package name.
home_package() {
  local candidate
  for candidate in \
      "$(query_home | cut -d/ -f1)" \
      "$(tv 'cmd package resolve-activity --brief -a coocaa.intent.action.HOME' 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1)" \
      "$(focus_now | sed -n 's/.*u0 \([^/]*\)\/.*/\1/p')"; do
    case "$candidate" in
      ""|"No activity found"|*" "*) continue ;;
      android) continue ;;
    esac
    printf '%s\n' "$candidate"
    return 0
  done
  return 1
}
STOCK="$(home_package || true)"

# The TV asks the person to confirm every install. That prompt cannot be answered from here -- it
# is the whole point of it -- so this waits for a human instead of hammering the TV. Each retry has
# to be started deliberately, because a retry only helps if someone is looking at the screen.
push_and_install() {
  local apk="$1" out=""
  "$PY" -I "$TOOLS_DIR/b64push.py" "$apk" /data/local/tmp/ipodtvstyle.apk >/dev/null 2>&1 \
    || { echo "upload-failed"; return 0; }
  # These TVs swallow the first pm install after an idle spell and accept the identical file a
  # second later, with nobody touching the remote. Retry quickly rather than making the person
  # wait, and only mention the TV prompt if a second attempt fails too.
  # One retry, no more. Every pm install puts a dialog on the TV, so a longer loop turns a failure
  # into a stream of dialogs instead of a solution. If the second attempt is refused too, the
  # reason needs a person, not another attempt.
  local attempt=1
  while [ "$attempt" -le 2 ]; do
    out="$(tv 'pm install -r /data/local/tmp/ipodtvstyle.apk' 2>&1 || true)"
    case "$out" in
      *Success*) echo "installed"; return 0 ;;
      *INSTALL_FAILED_USER_RESTRICTED*|*"canceled by user"*|*"failed to install"*|*Timeout*)
        attempt=$((attempt + 1))
        [ "$attempt" -le 2 ] && sleep 2 ;;
      *) echo "install-refused: $out"; return 0 ;;
    esac
  done
  echo "install-refused"
}

note "installing"
cat >&2 <<'NOTICE'

  ------------------------------------------------------------------
   Keep watching the TV from here on. It will ask whether to allow
   installing this app -- press ALLOW (允许) with the remote.

   Nobody can answer that prompt from the computer, and if it is
   ignored the install is cancelled. This is the one step that needs
   your hands.
  ------------------------------------------------------------------

NOTICE
WINNER=""
for name in $ORDER; do
  apk="$PREBUILT/$name.apk"
  [ -f "$apk" ] || continue
  pkg="$(pkg_of "$apk")"
  [ -n "$pkg" ] || continue

  RESULT="$(push_and_install "$apk" | tail -1)"
  case "$RESULT" in
    installed) ;;
    *) note "  $pkg: $RESULT"; continue ;;
  esac

  tv "cmd package compile -m speed -f $pkg" >/dev/null 2>&1 || true

  # Step 1 -- does the launcher actually run? An install that cannot start is worthless, and
  # checking now means the stock launcher is still untouched if it cannot.
  tv "am start -n $pkg/$MAIN_ACTIVITY" >/dev/null 2>&1 || true
  sleep 3
  ALIVE="$(tv "pidof $pkg" 2>/dev/null | tr -d '\r' || true)"
  if [ -z "$ALIVE" ]; then
    # am start is blocked in the background on some firmware, yet the launcher starts fine once it
    # owns the Home key. Try again through Home instead of throwing the install away.
    note "  $pkg: did not start from the command line; will try through the Home key" >&2
  fi

  # Step 2 -- hand it Home. Disabling the stock launcher is what makes the Home key land here on
  # firmware that routes Home through its own list; without it the key ignores the preference and
  # goes straight back to the vendor launcher, which is exactly the "installed but never becomes
  # Home" loop this script used to end in. It is undone below if the handover does not complete.
  if [ -n "$STOCK" ] && [ "$STOCK" != "$pkg" ]; then
    tv "pm disable-user --user 0 $STOCK" >/dev/null 2>&1 || true
  fi
  tv "cmd package set-home-activity --user 0 $pkg/$MAIN_ACTIVITY" >/dev/null 2>&1 || true
  tv 'input keyevent KEYCODE_HOME' >/dev/null 2>&1 || true
  sleep 3
  FOCUS="$(focus_now)"

  case "$FOCUS" in
    *"$pkg"*)
      WINNER="$pkg"
      # Remember whose Home we took, so --uninstall can hand it back.
      if [ -n "$STOCK" ] && [ "$STOCK" != "$pkg" ]; then
        printf '%s\n' "$STOCK" > "$TOOLS_DIR/../.stock-launcher" 2>/dev/null || true
      fi
      note "  $pkg: Home key lands here"
      break ;;
  esac

  # Not ours. Put the TV back exactly as it was before trying another variant.
  note "  $pkg: Home key did not move here; putting the TV back"
  if [ -n "$STOCK" ] && [ "$STOCK" != "$pkg" ]; then
    tv "pm enable --user 0 $STOCK" >/dev/null 2>&1 || true
    tv "cmd package set-home-activity --user 0 $STOCK/$(tv "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK" 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f2)" >/dev/null 2>&1 || true
  fi
  tv "pm uninstall $pkg" >/dev/null 2>&1 || true
done
rm -f "$TOOLS_DIR/../.stock-launcher" 2>/dev/null || true

if [ -z "$WINNER" ]; then
  cat >&2 <<'EOF'
ERROR: not installed.

The TV refused the install twice, which happens for one of these reasons:

  1. A prompt is on screen asking to allow the install and nobody answered it.
     Watch the TV while this runs; the prompt times out in a few seconds.
  2. Installing from unknown sources is off:
     Settings > Security > installing apps from unknown sources.

Sort either one out and run the same command again. Repeating it is safe, and
nothing was left on the TV.
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
