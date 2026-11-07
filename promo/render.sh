#!/bin/bash
cd "$(dirname "$0")"
export CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
export FPS=30
export DURLEN=10.4
export WORKDIR="$PWD"
N=$(awk 'BEGIN{printf "%d", DURLEN*FPS}')
START=${1:-0}
END=${2:-$((N-1))}
render_frame() {
  i=$1
  t=$(awk -v i="$i" -v f="$FPS" 'BEGIN{printf "%.4f", i/f}')
  ud="/tmp/cr_ud_$i"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --user-data-dir="$ud" --no-first-run --disable-extensions \
    --window-size=1920,1080 --screenshot="/tmp/fr_$i.png" --virtual-time-budget=1500 \
    "file://$WORKDIR/index.html?t=$t" >/dev/null 2>&1
  if [ -f "/tmp/fr_$i.png" ]; then
    mv "/tmp/fr_$i.png" "$WORKDIR/frames/$(printf 'f%04d.png' $i)"
  else
    echo "$i" >> /tmp/render_fail.txt
  fi
  rm -rf "$ud"
}
export -f render_frame
seq "$START" "$END" | xargs -P 4 -I{} bash -c 'render_frame {}'
echo "done: $(ls frames | wc -l) frames"
