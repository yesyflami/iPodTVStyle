<p align="center">
  <img src="assets/logo-dark.png#gh-dark-mode-only" width="200" alt="iPodTVStyle">
  <img src="assets/logo-light.png#gh-light-mode-only" width="200" alt="iPodTVStyle">
</p>

<p align="center">
  <img src="assets/wordmark-dark.png#gh-dark-mode-only" width="460" alt="iPodTVStyle">
  <img src="assets/wordmark-light.png#gh-light-mode-only" width="460" alt="iPodTVStyle">
</p>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> ·
  <a href="screenshots/">Screenshots</a> ·
  <a href="#quick-start">How to install</a> ·
  <a href="docs/MODELS.md">Compatible TVs</a> ·
  <a href="docs/INSTALL-PROMPT.md">Prompt for your agent</a> ·
  <a href="docs/ADAPT.md">Adapt to your TV</a> ·
  <a href="docs/HOW-IT-WORKS.md">How it works</a> ·
  <a href="docs/FAQ.md">FAQ</a>
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

▶ **MIT, do what you want with it.** Fork it, ship it, just keep the notice.<br>
▶ **Your TV stays yours.** Nothing is uninstalled — the stock launcher is still on the device and comes back with one command.<br>
▶ **New here?** Read **[docs/ADAPT.md](docs/ADAPT.md)** — it is almost always the package name that trips people up.<br>

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
| TV | Android TV / Google TV, **Android 8.0 (API 26)** or newer — see the [457-model list](docs/MODELS.md) |
| ADB | enabled over Wi-Fi (Developer options → Network debugging) |
| Build | JDK 17 + Android SDK 34, or just open `launcher/` in Android Studio |

---

## Quick start

You need a computer on the same network as the TV, and a coding agent that can run
terminal commands — [Claude Code](https://claude.com/claude-code),
[Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli).

| Step | What to do |
|---|---|
| **1. On the TV** | Settings → About, tap **Build number** seven times, go back into **Developer options**, enable **USB debugging** and — if it exists — **Network debugging / ADB over network**. Note the TV's IP (Settings → Network). Menu names differ per vendor. |
| **2. On the computer** | Clone the repo and open the folder in your agent (`git clone https://github.com/yesyflami/iPodTVStyle`), then hand it the block in [`docs/INSTALL-PROMPT.md`](docs/INSTALL-PROMPT.md). **One thing to edit: the TV's IP.** |
| **3. Answer the agent** | Accept the "Allow debugging" prompt on the TV; tell the agent which apps should appear (nine are preset) and that you want a rollback point recorded. |
| **4. Verify** | Let the agent probe the home package → build → install → set-as-home. Then check yourself: does HOME land on the new launcher, do the arrow keys feel right, and **did you get a restore command in writing**. |

Cloning first matters: the agent then uses `tools/build.sh`, `tools/install.sh` and the
procedure in [docs/ADAPT.md](docs/ADAPT.md) instead of improvising its own; the rollback
record and the restore command live there.

Press **HOME** on the remote → pick iPodTVStyle → Always.

If it never appears in the HOME chooser, that is the allow-list problem — see
**[docs/ADAPT.md](docs/ADAPT.md) §1**. It is almost always the package name.

```bash
# back to the stock launcher (fill in what step 2 recorded)
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

▶ **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** by [@Gathin23](https://github.com/Gathin23) — the project that proved an ad-filled Android TV home screen can be replaced over Wi-Fi without root, and one command to undo. If you also want the bloatware gone, start there and use iPodTVStyle as the replacement home screen.<br>
▶ The visual language is a love letter to the 2007 iPod nano / iTunes Cover Flow.<br>
▶ Roboto Black and Roboto Medium are read from `/system/fonts` at runtime, not bundled. The serif subset is derived from Songti SC — see [NOTICE.md](NOTICE.md).

## License

MIT, see [LICENSE](LICENSE). App names and logos in the screenshots belong to their
owners and are **not** covered by that licence — see [NOTICE.md](NOTICE.md).

<p align="center"><sub>简体中文说明 → <a href="README.zh-CN.md">README.zh-CN.md</a></sub></p>
