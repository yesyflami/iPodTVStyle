# Notice

## Third-party code

| What | Where | Licence |
|---|---|---|
| `tools/tv.py`, `tools/b64push.py`, `tools/adb-closed.py`, `tools/requirements.txt` — vendored **verbatim** | [tools/](tools/), provenance in [tools/VENDORED.md](tools/VENDORED.md) | MIT, © [Gathin](https://github.com/Gathin23/cleanyourtv) ([CleanYourTV](https://github.com/Gathin23/cleanyourtv)) |
| The install order in `tools/install.sh` / `install.ps1` (install → verify start → disable stock → set HOME → verify → roll back) and `.gitattributes` | as adapted from the same project | same |

`hooks/`, `themes/`, `examples/` and the launcher source of that project are **not** used here.
This project is not affiliated with or endorsed by CleanYourTV.

## Trademarks and app icons

No third-party app icon is distributed with this project. The launcher reads every tile — label,
icon and colour — from the TV it is installed on, via `PackageManager`, so a build carries only
the apps of the machine it runs on.

Two exceptions, both historical and both about **screenshots**, not code:

- `screenshots/`, `promo/assets/` and `promo/poster.jpg` are photos of the author's own
  television and contain third-party marks (Quark, Spotify, Mango TV, NetEase Music, HappyCast,
  Xigua, Coocaa HDMI), including a **Nintendo Switch 2** logo that was used as a tile icon in
  an early build. Those marks belong to their owners and appear here **only to show what the
  launcher looks like**; they are not part of this project's licence. The icon files themselves
  were removed from the tree in 1.0.3, but remain in the git history of commits before it.
- `screenshots/before-stock.jpg` is a photo of a stock Skyworth / Coocaa home screen, included
  for before/after comparison only.

If you redistribute a build, it contains no third-party icon at all — only the icons of the
installed apps, resolved at runtime on the target device.

## Name and visual language

"iPodTVStyle" and the wordmark in `assets/` reference Apple's iPod and iTunes Cover Flow as a
**visual homage**. Apple does not endorse this project and no Apple code or assets are used. The
name is a risk this repository accepts for itself; a redistributor should pick a neutral name.

## Fonts

- **Roboto Black / Roboto Medium** are loaded at runtime from the device
  (`/system/fonts/Roboto-Black.ttf`), not redistributed here. Apache 2.0.
- **`launcher/app/src/main/assets/app_name_serif.ttf`** is a 22 KB glyph subset generated from
  **Songti SC**, a macOS system font, with `tools/make-font-subset.py`. macOS system fonts are
  **not licensed for redistribution**, and a subset does not change that.

  Known issue, unfixed as of 1.1.0: replace it with a subset of an OFL font before publishing a
  build widely. **Noto Serif CJK / Source Han Serif** (SIL Open Font License) works with the same
  script, and the licence text can ship next to it:

  ```bash
  SOURCE_FONT=/path/to/NotoSerifCJK-Bold.ttc tools/make-font-subset.py \
    --names "夸克网盘,芒果TV,网易云音乐" \
    --out launcher/app/src/main/assets/app_name_serif.ttf
  ```

  The launcher checks per glyph (`Paint.hasGlyph`) and falls back to the system face, so a smaller
  subset costs nothing but the serif look on names it does not cover.

## Prior art

- [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT) — the project that demonstrated
  replacing an Android TV home screen over ADB without root, and whose connection layer this
  repository now vendors. Start there if you also want to disable the stock bloatware.
- The Cover Flow visual language is inspired by Apple's iTunes / iPod nano (2007).
  This project is an independent reimplementation.
