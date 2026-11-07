> **iPodTVStyle** — turn any Android TV into an iPod-style *Cover Flow* home screen.

<p align="center">
  <img src="screenshots/after-light.jpg" width="880" alt="iPodTVStyle cover flow home screen">
</p>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> · <a href="docs/ADAPT.md">Adapt to your TV</a> · <a href="docs/FAQ.md">FAQ</a>
</p>

---

## Before / After

| Stock ad-filled home | iPodTVStyle |
|---|---|
| ![before](screenshots/before-stock.jpg) | ![after](screenshots/after-light.jpg) |

A single custom `View`, one Canvas, no WebView, no dependencies. 181 KB APK.
Smooth on low-end TV SoCs because everything expensive (card shadows, mirror
reflections, icons) is **baked into bitmaps once at startup** — the render loop
only warps prefabbed textures into quads.

https://github.com/user-attachments/assets/placeholder → see [`promo/tv-home-promo.mp4`](promo/tv-home-promo.mp4)

---

## What you get

- **Cover Flow carousel** — 9 tiles in 3D perspective, the centre tile overlaps its
  neighbours, arrow keys slide with a 590 ms-ish eased transition.
- **Glossy floor reflections** — a mirrored, blurred copy of each tile that fades into
  the background. Reflections never stack colour: each tile's texture carries an
  opaque floor patch, so the tile nearest the centre always erases the one behind it.
- **Light / dark theme** — one tap, remembered across reboots.
- **Real app names** — pulled from `PackageManager`, so Chinese apps show Chinese names,
  English apps show English names. Rendered in a serif face.
- **Live clock** — big, bold, Roboto Black.
- **Works offline, no root.** Nothing is uninstalled; the original launcher stays on the
  device and can be restored with one command.

---

## Requirements

| | |
|---|---|
| TV | Android TV / Google TV, **Android 8.0 (API 26)** or newer |
| ADB | enabled over Wi-Fi (Developer options → USB/Network debugging) |
| Build | JDK 17 + Android SDK 34 (or just open `launcher/` in Android Studio) |

---

## Quick start

```bash
# 1. connect to the TV (find its IP in Settings → Network)
adb connect 192.168.1.50:5555
adb devices                 # should list the TV as "device"

# 2. tell the build which home package your TV expects (see docs/ADAPT.md)
#    edit launcher/gradle.properties → homeApplicationId

# 3. build + install + launch
cd launcher
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n <homeApplicationId>/com.gathin.tvhome.MainActivity

# 4. press the HOME button on the remote → choose iPodTVStyle → "Always"
```

If it doesn't appear in the HOME chooser, that's the allow-list problem — read
**[docs/ADAPT.md](docs/ADAPT.md)**. It is almost always the package name.

### Restore the stock launcher

```bash
adb shell cmd package set-home-activity com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity
# or simply
adb uninstall <homeApplicationId>
```

---

## Make it yours

Everything you'd normally want to change lives in two files:

| What | Where |
|---|---|
| The apps in the carousel (name, package, icon, tile colour, logo size) | `launcher/app/src/main/java/com/yesy/carouselhome/CarouselView.java` → `apps[]` |
| Package name the TV expects for HOME | `launcher/gradle.properties` → `homeApplicationId` |
| Clock font, date format | same Java file, `drawHeader()` |
| Animation length, tilt angles, spacing | same Java file, the constants block at the top |
| App names font (serif subset) | `launcher/app/src/main/assets/app_name_serif.ttf` + `tools/make-font-subset.py` |

See **[docs/ADAPT.md](docs/ADAPT.md)** for the full walkthrough, including how to pull
app icons off your own TV and how to make a serif font subset that covers your apps.

---

## How it works

Perspective-projected quads on a single `Canvas`, with the camera maths copied from the
CSS version of this design — plus a few tricks for old TV GPUs. If you want to modify the
renderer, read **[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md)** first; it explains the
projection, the bake pipeline, the occlusion clipping and the frame budget.

---

## Credits

- **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** by [@Gathin23](https://github.com/Gathin23) —
  the project that showed how to strip an ad-filled Android TV home screen over Wi-Fi
  without root, and whose launcher work made this whole approach approachable. If you
  also want to disable the stock bloatware, start there and use iPodTVStyle as the
  replacement home screen.
- The visual language is a love letter to the 2007 iPod nano / iTunes Cover Flow.
- Roboto Black and Roboto Medium ship with Android and are loaded from `/system/fonts`.
  The bundled serif subset is derived from Songti SC (Apple) — see [`NOTICE.md`](NOTICE.md).

## License

MIT — see [LICENSE](LICENSE).
App names and logos shown in the screenshots belong to their owners; see [NOTICE.md](NOTICE.md).
