# promo/

Source for the 10-second teaser.

**The rendered video is not distributed with this repository** — the mp4 is ~12 MB and
does not belong in a source tree. Regenerate it locally whenever you want it:

```bash
cd promo
./render.sh                                   # 312 PNG frames into frames/ (needs Chrome)
swift encode_frames.swift frames out.mp4 30   # mux with AVFoundation (no ffmpeg needed)
```

Either delete `frames/` afterwards or leave it — it is gitignored.

## What is what

| File | Role |
|---|---|
| `index.html` | the animation itself, self-contained, no dependencies |
| `assets/` | the screenshots the piece cuts between |
| `render.sh` | walks 312 timestamps (10.4 s × 30 fps) through headless Chrome |
| `encode_frames.swift` | H.264 muxer using AVFoundation — this machine has no ffmpeg |
| `poster.jpg` | one still from the piece, kept as a preview image |

## The animation contract

`index.html` is not a video player, it is a *seekable* composition, so any frame can be
rendered deterministically:

- the root element carries `data-om-exportable-video-with-duration-secs="10.4"`
- `window.__animStage = { duration, setTime(seconds), setPlaying(bool) }`
- `?t=<seconds>` renders exactly one frame and stops (this is what `render.sh` uses)
- it also listens for the `data-om-seek-to-time-frame` event, so a host preview can scrub it

Every position is a pure function of `t`, which is why the frames are reproducible.

## Editing the cut

The whole timeline lives in the `T` object near the bottom of `index.html`:

```js
const T = {
  beforeEnd: 1.45, flashAt: 1.62, afterIn: 1.72, afterMid: 2.30,
  motionIn: 3.35, darkIn: 4.55, refIn: 5.35, hdrIn: 6.15,
  splitIn: 7.00, endIn: 8.60, end: 10.40
};
```

Change those, update `data-om-exportable-video-with-duration-secs` and the `DURATION`
constant to match, then re-run `render.sh`.
