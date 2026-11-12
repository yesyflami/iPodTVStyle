# Notice

## Trademarks and app icons

No third-party icon is distributed with this project any more. The launcher reads every
tile — label, icon and colour — from the TV it is installed on, via `PackageManager`, so a
build carries only the apps of the machine it runs on.

The screenshots still contain icons and names of third-party applications (Quark, Spotify,
Mango TV, NetEase Music, HappyCast, Xigua, Coocaa HDMI, and others) because they are photos
of the author's own television. Those names and marks are the property of their respective
owners and appear here **only to show what the launcher looks like**. They are not part of
this project's licence.

The screenshots `screenshots/before-stock.jpg` is a photo of a stock Skyworth / Coocaa
home screen, included for before/after comparison only.

## Fonts

- **Roboto Black / Roboto Medium** are loaded at runtime from the device
  (`/system/fonts/Roboto-Black.ttf`), not redistributed here. Apache 2.0.
- **`launcher/app/src/main/assets/app_name_serif.ttf`** is a glyph subset generated from
  **Songti SC** (Apple macOS system font) using `tools/make-font-subset.py`. The subset
  contains only the characters used by the app names in this repository.
  If you generate your own subset from a different source font, make sure that font's
  licence allows embedding — **Noto Serif CJK / Source Han Serif** (SIL Open Font
  License) is a safe, cross-platform choice and works with the same script.

## Prior art

- [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT) — the project that
  demonstrated replacing an Android TV home screen over ADB without root. Its approach
  and documentation shaped this one.
- The Cover Flow visual language is inspired by Apple's iTunes / iPod nano (2007).
  This project is an independent reimplementation; no Apple code or assets are used.
