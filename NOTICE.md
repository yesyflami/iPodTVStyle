# Notice

## Trademarks and app icons

The screenshots and the `res/drawable-nodpi/` folder contain icons and names of
third-party applications (Quark, Spotify, Mango TV, NetEase Music, Nintendo Switch,
HappyCast, Xigua, Coocaa HDMI, and others). Those names and marks are the property of
their respective owners and are used here **only to identify the apps on the author's own
television**. They are not part of this project's licence.

If you redistribute a build, replace the icons in
`launcher/app/src/main/res/drawable-nodpi/` with your own artwork, or drop the entries
from the `apps[]` array in `CarouselView.java`.

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
