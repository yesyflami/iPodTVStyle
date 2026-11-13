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

- **Cover Flow 卡片轮播** —— 最多 9 张卡片按 3D 透视排布，中间那张压住两侧邻居，方向键切换带 420 ms 缓动。
- **玻璃地板倒影** —— 每张卡下方是自己模糊后的镜像。因为每张贴图都自带一块**不透明的地板色**，更靠中间的卡片天然把外侧残留盖掉，倒影永远不会叠色。
- **深浅两套主题** —— 一键切换，重启后记住。
- **你自己的应用** —— 启动时从 `PackageManager` 读本机清单：先 leanback 应用，再普通应用；名字、图标都来自应用本身，卡片底色由图标主色采样得到。源码里没有"作者的应用名单"，包里也不再带第三方 logo。
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
| 电脑 | **Python 3**、**JDK 17–22**、**Android SDK platform 34** + build-tools 34 |
| 系统 | macOS / Linux / Windows（Git Bash 或 PowerShell 脚本） |

仓库**不提供预编译 APK**。安装包用编译机的 debug key 签名后侧载，所以请在你自己的电脑上编译
再装到电视——别人编译的同名包签名不同，无法覆盖安装。

---

## 快速开始

准备一台与电视连在同一局域网的电脑，以及一个能执行终端命令的编程助手
（Claude Code、Codex、Gemini CLI 均可，下文以 Codex 为例）。

### 一、电视端

电视端需要完成两件事：**记下配对信息**，以及**打开 ADB 调试开关**。

**1. 记下配对信息**

用遥控器进入设置，找到「本机信息」一类的条目，记下其中的**电视型号**。
再进入当前连接的 Wi-Fi，查看「网络信息」，记下**IP 地址**（形如 `192.168.1.50`）。

这两项是后续让助手连接电视的依据。

**2. 打开 ADB 调试开关**

不同机型的开启方式略有差异。大多数电视可以在「版本号」上连按**七次**进入开发者模式；
若无法确定，搜索「电视型号 + adb」通常能在社区找到对应方法。

以创维电视为例：进入「本机信息」后，用遥控器依次按 **上上下下左右左右**，
即可进入开发者模式，随后打开其中的调试开关。

### 二、电脑端

确认电脑与电视连接在**同一个 Wi-Fi** 下。

把下面这段直接发给助手。**全文只有一处要你改**：`电视 IP：___` 那行，把 `___` 换成电视的 IP，其余原样发过去。

```text
请帮我把我的电视换成 iPodTVStyle 桌面。你的任务是把它装到能用为止，不是把报错转给我。

项目地址：https://github.com/yesyflami/iPodTVStyle
电视 IP：___

一、做什么

1. 克隆仓库并进入目录。
   如果报 "Failed to connect to 127.0.0.1" ，是代理环境变量问题，用这个重试：
     HTTPS_PROXY= HTTP_PROXY= git clone https://github.com/yesyflami/iPodTVStyle.git

2. 执行 tools/install.sh <上面那个 IP>（Windows 用 tools\install.ps1）。
   脚本只需要 Python 3，不需要 JDK、Android SDK、adb，不要自己装这些，
   也不要跑 tools/build.sh。

3. 安装过程中电视会弹「是否允许安装」。这是系统的确认框，脚本绕不过去，只能人去按。
   执行到这一步时提醒我看电视，等 10 秒再确认一次结果。

二、遇到这些情况，你自己处理，不要直接发给我

| 现象 | 你该做什么 |
|---|---|
| 报 INSTALL_FAILED_USER_RESTRICTED | 最多再跑一次脚本。还失败就问我在电视上点了没、未知来源开了没 |
| 报 "No activity found" | 这个固件不用标准 HOME 分类，是正常的，继续 |
| 报 "Home key did not move here" | 说明这台电视认白名单包名。直接告诉我这一句，不用贴全文 |
| 报 "could not reach" | 确认电视开着、和电脑同一个 WiFi、网络调试打开 |
| Python 相关报错 | Windows 装 Python 时勾 "Add python.exe to PATH" |
| 其它 | 你自己先读 docs/ADAPT.md 和 docs/FAQ.md 找答案 |

三、规则

- 一次只跑一遍脚本。不要连续重跑超过 3 次，每次重跑都会在电视上弹一次窗。
- 不要修改 launcher/ 下的任何前端代码。
- 不要卸载电视上的其它应用，不要 root。
- 失败时不要贴大段原始日志。用一句话告诉我：卡在第几步、需要我在电视上做什么。
- 成功时告诉我结果就行，不用复述脚本输出。

四、验收

装好后按电视遥控器的 HOME 键，应该直接进 iPodTVStyle（卡片式应用列表）。
如果按 HOME 还是回原厂桌面，就是没成功，告诉我。
```

**装的时候盯着电视**：电视会弹窗问是否允许安装，必须用遥控器点「允许」，几秒内不点就自动取消。
这一步电脑代替不了，任何工具都代替不了。

电脑上唯一需要的前置条件是 **Python 3**（Mac 和 Windows 都自带安装方式）。
第一次运行会花一分钟装一个小的 Python 环境，之后就是秒级。
不需要 JDK，不需要 Android SDK，不需要 adb。


填好后第一行应该是 `电视 IP：192.168.1.50` 这样——**全文只有这一处需要改**，其余原样发送。

安装完成后按遥控器 **HOME 键**，应当直接进入新桌面。

如果装上了但 HOME 没有落到新桌面上，脚本会明确告诉你并把原厂桌面放回去。
原因通常是这台机型有白名单，或者把 HOME 交给固件自己的属性（部分创维/酷开固件），
见 [docs/ADAPT.md](docs/ADAPT.md) 第 4、5 节。

---

## 改成你自己的样子

| 想改什么 | 改哪里 |
|---|---|
| 轮播里的应用 | 不用改 —— 启动时从 `PackageManager` 读 |
| 想隐藏某张卡片 | `<应用私有目录>/hidden.txt`，一行一个包名，不用重新编译（`adb push` 即可） |
| 电视认的包名（白名单机型） | `BUILD_ARGS=-PlauncherApplicationId=<包名> TV_HOST=… tools/install.sh`，逐机型说明在 `docs/devices/` |
| 时钟字体、日期格式 | `CarouselView.java` → `drawHeader()`（日期跟随电视语言） |
| 动画时长、倾斜角度、卡片间距 | 同一文件 → 顶部常量区 |
| 应用名衬线字体 | `assets/app_name_serif.ttf` + `tools/make-font-subset.py`（缺字自动回落系统字体） |

完整流程见 **[docs/ADAPT.md](docs/ADAPT.md)**。连接层（`tools/tv.py`、`b64push.py`、安装顺序与回滚）
来自 [CleanYourTV](https://github.com/Gathin23/cleanyourtv)（MIT），见 [tools/VENDORED.md](tools/VENDORED.md)。

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
