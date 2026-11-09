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

一台与电视连在同一局域网的电脑，加一个能跑终端命令的编程助手：
[Claude Code](https://claude.com/claude-code)、[Codex](https://github.com/openai/codex)、
[Gemini CLI](https://github.com/google-gemini/gemini-cli) 都行。

### 一、电视端

1. 打开**开发者选项**：设置 → 关于，在「版本号」上连按**七次**。
2. 进去打开 **USB 调试**；如果还有 **网络调试 / ADB over network**，一并打开。
   菜单位置和叫法因机型而异，翻一遍开发者选项里的每一项基本都能找到。

### 二、电脑端

1. **先记下电视的型号和 IP**

   - 型号：设置 → 关于 → 型号 / 设备名称（例如 `7S77_A6M`）
   - IP：设置 → 网络 → 当前 Wi-Fi → IP 地址（形如 `192.168.1.50`）

2. **克隆仓库，在助手里打开这个文件夹**

   ```bash
   git clone https://github.com/yesyflami/iPodTVStyle
   ```

3. **复制下面这段，把型号和 IP 填进去，整段发给助手**

```text
帮我用 ADB 把电视换成 iPodTVStyle 桌面。

电视型号：___
电视 IP：___

请按仓库 docs/ADAPT.md 执行，不要自己发明步骤：
1. adb connect ___ ，确认 adb devices 状态是 device
2. 先记录原厂桌面（还原要用）：
   adb shell cmd package resolve-activity -c android.intent.category.HOME
3. 判断这台机器的 HOME 策略，把结论告诉我再动手
4. 改 launcher/gradle.properties 里的 homeApplicationId
5. ./tools/build.sh
6. ./tools/install.sh ___
7. 最后把还原命令拼好给我

约束：不要卸载系统应用、不要 root、不要 adb kill-server；
遇到 INSTALL_FAILED_UPDATE_INCOMPATIBLE 立刻停下问我，不要强行覆盖。
```

4. **验收**

   - 电视上点「允许调试」
   - 装完按 **HOME 键** → 桌面选择器 → 选 iPodTVStyle → **始终**
   - 方向键滑动是否顺畅
   - **助手有没有把还原命令交给你**

没出现在 HOME 选择器里？那是白名单问题，看 [docs/ADAPT.md](docs/ADAPT.md) 第 1 节。

还原回原厂桌面：

```bash
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
