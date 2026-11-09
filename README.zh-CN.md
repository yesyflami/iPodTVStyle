<p align="center">
  <img src="assets/logo-dark.png#gh-dark-mode-only" width="200" alt="iPodTVStyle">
  <img src="assets/logo-light.png#gh-light-mode-only" width="200" alt="iPodTVStyle">
</p>

<p align="center">
  <img src="assets/wordmark-dark.png#gh-dark-mode-only" width="460" alt="iPodTVStyle">
  <img src="assets/wordmark-light.png#gh-light-mode-only" width="460" alt="iPodTVStyle">
</p>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="screenshots/">看效果 / 截图</a> ·
  <a href="#快速开始">如何安装</a> ·
  <a href="docs/MODELS.md">机型清单</a> ·
  <a href="docs/INSTALL-PROMPT.md">复制给助手</a> ·
  <a href="docs/ADAPT.md">适配你的电视</a> ·
  <a href="docs/HOW-IT-WORKS.md">实现原理</a> ·
  <a href="docs/FAQ.md">常见问题</a>
</p>

---

> 把电视首页做成 Cover Flow。一个自定义 View 画完所有东西：3D 卡片、烘死的阴影、不会互相叠色的落地倒影、深浅两套主题、从系统读取的真实应用名。没有 WebView、没有第三方库、不需要 root，装上 180 KB。
>
> Cover Flow as a TV home screen. A single custom `View` that puts your apps on a mirror-black floor — 3D tiles, baked shadows, reflections that never stack colour, light and dark, real app names. No WebView, no libraries, no root.

![license](https://img.shields.io/badge/license-MIT-22c55e?style=flat&labelColor=2f353d)
![type](https://img.shields.io/badge/type-android%20launcher-8b93a1?style=flat&labelColor=2f353d)
![android](https://img.shields.io/badge/android-8.0%2B-3ddc84?style=flat&labelColor=2f353d)
![apk](https://img.shields.io/badge/apk-180%20KB-8957e5?style=flat&labelColor=2f353d)
![root](https://img.shields.io/badge/root-not%20required-f97316?style=flat&labelColor=2f353d)
![deps](https://img.shields.io/badge/dependencies-none-0ea5e9?style=flat&labelColor=2f353d)

▶ **MIT 许可，随便用。** Fork、改造、发布都行，保留版权声明即可。<br>
▶ **你的电视还是你的。** 不卸载任何东西 —— 原厂桌面还在机器里，一条命令就能回去。<br>
▶ **第一次来？** 先看 **[docs/ADAPT.md](docs/ADAPT.md)**，九成问题都出在包名上。<br>

---

## 之前 / 之后

| 原厂首页（信息流 + 广告位） | iPodTVStyle |
| :---: | :---: |
| <img src="screenshots/before-stock.jpg" width="440"> | <img src="screenshots/after-light.jpg" width="440"> |

能在低端电视 SoC 上跑顺，是因为所有昂贵的东西 —— 卡片阴影、落地倒影、图标 —— 都在启动时**一次性烘焙成位图**。每帧只做一件事：把预制贴图按投影矩阵贴到四个角上。

---

## 你会得到什么

- **Cover Flow 卡片轮播** —— 9 张卡片按 3D 透视排布，中间那张压住两侧邻居，方向键切换带 420 ms 缓动。
- **玻璃地板倒影** —— 每张卡下方是自己模糊后的镜像。因为每张贴图都自带一块**不透明的地板色**，更靠中间的卡片天然把外侧残留盖掉，倒影永远不会叠色。
- **深浅两套主题** —— 一键切换，重启后记住。
- **真实应用名** —— 从 `PackageManager` 读取，中文应用显示中文、英文显示英文，衬线渲染。
- **大号时钟** —— Roboto Black，直接读系统字体。
- **离线、免 root、可还原** —— 什么都不卸载。

<p align="center">
  <img src="screenshots/after-dark.jpg" width="720" alt="深色主题">
</p>

---

## 环境要求

| | |
|---|---|
| 电视 | Android TV / Google TV，**Android 8.0（API 26）** 及以上 —— 先在 [457 个机型的清单](docs/MODELS.md) 里查你这台 |
| ADB | 已开启网络调试（设置 → 开发者选项） |
| 构建 | JDK 17 + Android SDK 34，或直接用 Android Studio 打开 `launcher/` |

---

## 快速开始

准备一台与电视连在同一局域网的电脑，以及一个能跑终端命令的编程助手，例如
[Claude Code](https://claude.com/claude-code)、[Codex](https://github.com/openai/codex) 或
[Gemini CLI](https://github.com/google-gemini/gemini-cli)。

| 步骤 | 操作 |
|---|---|
| **1. 电视端** | 打开「设置 → 关于」，在「版本号」上连按七次，返回后进入**开发者选项**，打开 **USB 调试**；如果另有**网络调试 / ADB over network**，也一并打开。顺手记下电视 IP（设置 → 网络）。菜单路径因机型而异。 |
| **2. 电脑端** | 先克隆仓库并在助手里打开这个文件夹（`git clone https://github.com/yesyflami/iPodTVStyle`），再把 [`docs/INSTALL-PROMPT.md`](docs/INSTALL-PROMPT.md) 里的那段指令交给助手，**只需要改一处：电视 IP**。 |
| **3. 确认需求** | 电视上弹出「允许调试」时点允许；告诉助手你要哪几个应用进轮播（默认给了 9 个）、以及是否保留原厂桌面的还原点。 |
| **4. 检查结果** | 让助手按提示完成探测包名 → 编译 → 安装 → 设为默认桌面。然后自己验收：按 HOME 是否进新桌面、方向键是否顺、**还原命令是否已经交到你手上**。 |

先克隆仓库的意义在于：助手会直接使用仓库里的 `tools/build.sh`、`tools/install.sh` 和
[docs/ADAPT.md](docs/ADAPT.md) 的流程，不会自己另写一套；还原点的记录方式和还原命令都在其中。

装完按遥控器 **HOME 键** → 选 iPodTVStyle → 允许 / 始终。

如果它压根没出现在 HOME 选择器里，那是白名单问题，看 **[docs/ADAPT.md](docs/ADAPT.md) 第 1 节**。

```bash
# 想换回原厂桌面（把第 2 步记下的东西填进来）
adb shell cmd package set-home-activity <原桌面包名>/<原桌面Activity>
adb uninstall <homeApplicationId>
```

---

## 改成你自己的样子

| 想改什么 | 改哪里 |
|---|---|
| 轮播里的应用（名字、包名、图标、卡片底色、logo 大小） | `launcher/app/src/main/java/com/yesy/carouselhome/CarouselView.java` → `apps[]` |
| 电视认的 HOME 包名 | `launcher/gradle.properties` → `homeApplicationId` |
| 时钟字体、日期格式 | 同一文件 → `drawHeader()` |
| 动画时长、倾斜角度、卡片间距 | 同一文件 → 顶部常量区 |
| 应用名衬线子集 | `assets/app_name_serif.ttf` + `tools/make-font-subset.py` |

完整流程（含如何从自己的电视上抠应用图标）见 **[docs/ADAPT.md](docs/ADAPT.md)**。

---

## 原理

一块 `Canvas` 上画 3D 透视四边形，摄像机参数来自这个设计的 CSS 版本，另外加了几处针对老旧电视 GPU 的妥协。改渲染逻辑前先看 **[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md)**：投影公式、烘焙流程、遮挡裁剪、帧预算都在里面。

<p align="center">
  <img src="screenshots/header-closeup.jpg" width="640" alt="顶部图标">
</p>

---

## 致谢

▶ **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** / [@Gathin23](https://github.com/Gathin23) —— 这个项目证明了"不 root、纯 Wi-Fi、一条命令可还原"地换掉广告首页是可行的。如果你还想清掉预装臃肿软件，从那边开始，再把 iPodTVStyle 当作替换的桌面。<br>
▶ 视觉语言致敬 2007 年的 iPod nano / iTunes Cover Flow。<br>
▶ Roboto Black / Medium 运行时从 `/system/fonts` 读取，没有打包进 APK。内嵌的衬线子集来自 Songti SC，见 [NOTICE.md](NOTICE.md)。

## 许可

MIT，见 [LICENSE](LICENSE)。截图里出现的应用名称与图标归各自所有者，**不在**这个许可范围内 —— 见 [NOTICE.md](NOTICE.md)。

<p align="center"><sub>English → <a href="README.md">README.md</a></sub></p>
