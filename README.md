<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo-dark.png">
    <img src="assets/logo-light.png" width="130" alt="iPodTVStyle">
  </picture>
</p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/wordmark-dark.png">
    <img src="assets/wordmark-light.png" width="430" alt="iPodTVStyle">
  </picture>
</p>

---

> Cover Flow as a TV home screen. A single custom `View` that puts your apps on a
> mirror-black floor — 3D tiles, baked shadows, reflections that never stack colour,
> light and dark, real app names. No WebView, no libraries, no root, 180 KB.
>
> 把电视首页做成 Cover Flow。一个自定义 View 画完所有东西：3D 卡片、烘死的阴影、
> 不会互相叠色的落地倒影、深浅两套主题、从系统读取的真实应用名。没有 WebView、
> 没有第三方库、不需要 root，装上 180 KB。

![license](https://img.shields.io/badge/license-MIT-22c55e?style=flat&labelColor=2f353d)
![type](https://img.shields.io/badge/type-android%20launcher-8b93a1?style=flat&labelColor=2f353d)
![android](https://img.shields.io/badge/android-8.0%2B-3ddc84?style=flat&labelColor=2f353d)
![apk](https://img.shields.io/badge/apk-180%20KB-8957e5?style=flat&labelColor=2f353d)
![root](https://img.shields.io/badge/root-not%20required-f97316?style=flat&labelColor=2f353d)
![deps](https://img.shields.io/badge/dependencies-none-0ea5e9?style=flat&labelColor=2f353d)

▶ **MIT, do what you want with it.** Fork it, ship it, just keep the notice.
▶ **Your TV stays yours.** Nothing is uninstalled — the stock launcher is still on the
device and comes back with one command.
▶ **New here?** Read **[docs/ADAPT.md](docs/ADAPT.md)** — it is almost always the
package name that trips people up.
▶ **Ten second teaser** → [promo/tv-home-promo.mp4](promo/tv-home-promo.mp4)

---

## Before / After

| Stock ad-filled home | iPodTVStyle |
| :---: | :---: |
| <img src="screenshots/before-stock.jpg" width="440"> | <img src="screenshots/after-light.jpg" width="440"> |

Smooth on low-end TV SoCs because everything expensive — card shadows, mirror
reflections, icons — is **baked into bitmaps once at startup**. The render loop only
warps prefabbed textures into projected quads.

---

## What you get

- **Cover Flow carousel** — 9 tiles in 3D perspective, the centre tile overlaps its
  neighbours, arrow keys slide with a 420 ms eased transition.
- **Glossy floor reflections** — each tile sits on a blurred mirror image. Because every
  texture carries an opaque floor patch, the tile nearest the centre always erases the
  one behind it: reflections never stack colour.
- **Light / dark theme** — one tap, remembered across reboots.
- **Real app names** — pulled from `PackageManager`, so Chinese apps show Chinese names
  and English apps show English names, in a serif face.
- **Live clock** — big and bold, Roboto Black from the system.
- **Offline, no root, reversible.** Nothing is uninstalled.

<p align="center">
  <img src="screenshots/after-dark.jpg" width="720" alt="dark theme">
</p>

---

## Requirements

| | |
|---|---|
| TV | Android TV / Google TV, **Android 8.0 (API 26)** or newer |
| ADB | enabled over Wi-Fi (Developer options → Network debugging) |
| Build | JDK 17 + Android SDK 34, or just open `launcher/` in Android Studio |

---

## Quick start

```bash
# 1 · connect to the TV
adb connect 192.168.1.50:5555
adb devices

# 2 · write down the stock home activity FIRST, so you can always go back
adb shell cmd package resolve-activity -c android.intent.category.HOME

# 3 · set the home package your TV's firmware allows
#     launcher/gradle.properties → homeApplicationId

# 4 · build, install, launch
cd launcher
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n <homeApplicationId>/com.gathin.tvhome.MainActivity

# 5 · press HOME on the remote → pick iPodTVStyle → "Always"
```

If it does not show up in the HOME chooser, that is the allow-list problem described in
**[docs/ADAPT.md](docs/ADAPT.md)**. It is almost always the package name.

```bash
# back to the stock launcher
adb shell cmd package set-home-activity <stock-home-package>/<stock-home-activity>
adb uninstall <homeApplicationId>
```

---

## Make it yours

| What | Where |
|---|---|
| Apps in the carousel (name, package, icon, tile colour, logo size) | `launcher/app/src/main/java/com/yesy/carouselhome/CarouselView.java` → `apps[]` |
| Home package the TV expects | `launcher/gradle.properties` → `homeApplicationId` |
| Clock font, date format | same Java file → `drawHeader()` |
| Animation length, tilt, spacing | same Java file → constants block at the top |
| Serif subset for app names | `assets/app_name_serif.ttf` + `tools/make-font-subset.py` |

Full walkthrough — including pulling icons off your own TV — in
**[docs/ADAPT.md](docs/ADAPT.md)**.

---

## How it works

Perspective-projected quads on a single `Canvas`, camera maths carried over from the CSS
version of this design, plus a few compromises for old TV GPUs. Before changing the
renderer, read **[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md)**: the projection, the bake
pipeline, the occlusion clipping and the frame budget are all explained there.

<p align="center">
  <img src="screenshots/header-closeup.jpg" width="640" alt="header icons">
</p>

---

## Credits

▶ **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** by [@Gathin23](https://github.com/Gathin23) —
the project that proved an ad-filled Android TV home screen can be replaced over Wi-Fi
without root, and one command to undo. If you also want the bloatware gone, start there
and use iPodTVStyle as the replacement home screen.
▶ The visual language is a love letter to the 2007 iPod nano / iTunes Cover Flow.
▶ Roboto Black and Roboto Medium are read from `/system/fonts` at runtime, not bundled.
The serif subset is derived from Songti SC — see [NOTICE.md](NOTICE.md).

## License

MIT, see [LICENSE](LICENSE). App names and logos in the screenshots belong to their
owners and are **not** covered by that licence — see [NOTICE.md](NOTICE.md).

<p align="center"><sub>简体中文说明 → <a href="README.zh-CN.md">README.zh-CN.md</a></sub></p>
