# 适配到你的电视

> English summary: change `homeApplicationId` in `launcher/gradle.properties` to the
> package name *your* TV uses for its home screen, then `./gradlew assembleRelease`,
> `adb install -r`, and pick the app when you press HOME. Everything else is optional
> polish. The rest of this document is the detailed Chinese walkthrough.

这份文档按顺序解决四件事：**包名 → 连上电视 → 装上去并设为默认 → 换成你自己的应用**。

---

## 0. 不想自己敲命令？

如果你有编程助手（Claude Code / Codex / Gemini CLI / Cursor），
直接用 [README 快速开始](../README.zh-CN.md#快速开始) 里那段可复制的指令，填上型号和 IP 发过去就行。
下面这份是给想自己动手、或者要排查问题的人看的。

---

## 0. 原理先说清楚（30 秒）

Android TV 的"桌面"就是一个声明了 `android.intent.category.HOME` 的普通 app。
你现在看到的原厂首页，也是这样一个 app。

所以要换桌面，只需要：**装一个同样声明 HOME 的 app，然后让系统选它**。

唯一的坑是：**大多数电视固件不允许任意包名成为默认桌面**。厂商在系统里
留了一份白名单，只有白名单里的包名（也就是原厂桌面的包名）才能拿到 HOME 角色。
所以我们要把自己的 app **假装成原厂桌面包名**装进去 —— 这就是
`homeApplicationId` 这一行的意义。

---

## 0.5 先查你的机型在不在清单里

**[机型清单](MODELS.md)** 收录了 457 个机型，标了「已实测可换 / 明确不可换 / 未验证」。
先搜一下你的型号，能省掉大量试错：状态是 ❌ 的机器固件锁了 HOME，装不进去；
状态是 ⚠️ 的没被验证过，按本文档走一遍即可。

---

## 1. 找到你电视的 HOME 包名

连上 ADB 之后（见第 2 节），任选一条命令：

```bash
# 方法 A：直接问系统默认桌面是谁
adb shell cmd package resolve-activity -c android.intent.category.HOME

# 方法 B：看当前前台是哪个 app（按一下 HOME 键再执行）
adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"

# 方法 C：翻所有声明了 HOME 的 app
adb shell dumpsys package | grep -B4 "android.intent.category.HOME" | grep "Package \["
```

以创维 / 酷开（SkyOS）为例，输出是：

```
priority=0 ... name=com.coocaa.homepage.vast.HomePageActivity
packageName=com.tianci.movieplatform
```

这里 **`com.tianci.movieplatform` 是原厂桌面的包名**。但创维的白名单认的是
`com.coocaa.tvhome`（一个已经不再使用的历史包名），所以本项目在创维上
`homeApplicationId=com.coocaa.tvhome` 才能生效。

> 换句话说：**白名单里的名字不一定是"当前桌面"的包名**。如果直接填原厂桌面包名
> 装不进去（签名冲突），就说明要用白名单里另一个名字。

### 已知对照表

| 品牌 / 系统 | `homeApplicationId` 填什么 | 备注 |
|---|---|---|
| 创维 / 酷开 SkyOS | `com.coocaa.tvhome` | 本项目原始机型（Skyworth 7S77，Android 8） |
| Google TV / Sony | `com.google.android.tvlauncher` | 需要先停用原桌面，否则签名冲突 |
| 小米 / PatchWall | `com.mitv.tvhome` | 部分机型允许任意包名，见下方"例外" |
| TCL Android TV | `com.tcl.tvlauncher` | 同上 |
| 其他 | 用上面的命令查 | 通常就是 `mCurrentFocus` 里的那个包名 |

### 例外：不需要伪装的机型

有些固件的 HOME 角色不设白名单，任何声明了 HOME 的 app 都能被选中。判断方法：

1. 先不改包名，直接用默认的 `com.ipodtvstyle.home` 编译安装
2. 按 HOME 键，看选择器里有没有 iPodTVStyle
3. 有 → 你的电视不需要伪装包名，直接用干净的名字更安全
4. 没有 → 回到白名单方案

**建议先用默认包名试一次**，能省掉后面很多麻烦（比如 OTA 升级后原厂桌面回来、
或者卸载时把系统组件一起带走）。

---

## 2. ADB 连上电视

### 电视端

1. 设置 → 关于 → 连点"版本号"7 次，打开开发者选项
2. 设置 → 开发者选项 → 打开 **USB 调试** 和 **网络调试 / ADB over network**
3. 设置 → 网络，记下电视的 IP（有线/无线都可以，同一个局域网就行）

### 电脑端

```bash
adb connect 192.168.1.50:5555
adb devices
```

- 显示 `device` → 好了
- 显示 `unauthorized` → 电视上会弹一个"允许调试"的对话框，用遥控器同意
- 显示 `offline` → `adb disconnect && adb connect <ip>:5555` 重来
- 连不上 → 确认电视和电脑在同一网段；有些路由器开了 AP 隔离（客户端隔离），关掉

---

## 3. 编译、安装、设为默认桌面

### 编译

需要 **JDK 17** 和 **Android SDK 34**。装好 Android Studio 的话，直接用
Android Studio 打开 `launcher/` 目录也行。

命令行：

```bash
cd launcher
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
# 产物：launcher/app/build/outputs/apk/release/app-release.apk
```

不想装 SDK 的话，`tools/build.sh` 会把这两个环境变量读进来再调用 gradlew。

### 安装

```bash
adb install -r launcher/app/build/outputs/apk/release/app-release.apk
```

报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（签名冲突）→ 说明电视上已经有一个
同包名的 app（一般是原厂桌面本身）。这种包名不要直接用：

```bash
# 先看看同包名的是谁
adb shell pm path <homeApplicationId>
```

如果是系统应用，**不要卸载它** —— 换一个白名单里的包名，或者用第 1 节末尾的
"例外"路线。

### 设为默认桌面

按遥控器 **HOME 键**，系统会弹选择器，选 iPodTVStyle → 允许/始终。

如果选择器不出现：

```bash
# 直接把 HOME 角色指过去
adb shell cmd package set-home-activity <homeApplicationId>/com.gathin.tvhome.MainActivity
adb shell am start -n <homeApplicationId>/com.gathin.tvhome.MainActivity
```

或者先停用原厂桌面（**这一步要谨慎，先记下原桌面包名**）：

```bash
adb shell pm disable-user --user 0 com.tianci.movieplatform
```

### 还原

```bash
# 1) 把 HOME 角色还给原桌面
adb shell pm enable com.tianci.movieplatform
adb shell cmd package set-home-activity com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity

# 2) 卸载 iPodTVStyle（如果包名是伪装的原厂名，卸载前先确认它不是你唯一能进系统的桌面）
adb uninstall <homeApplicationId>
```

> **建议动手前先执行一次** `adb shell dumpsys package | grep -B4 HOME` 把原桌面包名
> 抄下来，存进手机备忘录。这一步 10 秒，能救命。

---

## 4. 换成你自己的应用

打开 `launcher/app/src/main/java/com/yesy/carouselhome/CarouselView.java`，
顶部的 `apps[]` 数组就是轮播内容：

```java
private final AppItem[] apps = {
    new AppItem("夸克网盘",  "com.quark.yun.tv",       R.drawable.quark,    0xfff7f7f5, .54f),
    new AppItem("HDMI2",    "com.coocaa.hdmi2",       R.drawable.hdmi2,    0xffe60012, .58f),
    ...
};
```

| 字段 | 含义 |
|---|---|
| 名字 | 兜底名字。**运行时会被 `PackageManager.getApplicationLabel()` 覆盖**，所以中文应用会显示中文名 |
| 包名 | 按确认键时要启动的 app；也用来查真实名字 |
| `R.drawable.xxx` | 卡片上的 logo，放在 `res/drawable-nodpi/` |
| 颜色 | 卡片底色（ARGB），尽量取 logo 的主色 |
| 比例 | logo 占卡片宽度的比例，0.48–0.70 之间比较好看；logo 本身留白多的可以放大 |

改完记得同步 `AndroidManifest.xml` 里的 `<queries>` 列表：

```xml
<package android:name="com.quark.yun.tv" />
```

Android 11+ 不加这一段的话，`getApplicationLabel()` 查不到别的 app 的名字，
就会退回数组里的兜底名字。

### 图标准备

推荐规格：**正方形、透明背景 PNG、≥ 256×256**。
名字随意，扩展名必须是小写 `.png`，放 `res/drawable-nodpi/`。

从电视上抠图标的两种方法：

```bash
# 方法 A：从 APK 里挖
adb shell pm path com.spotify.tv.android      # → /data/app/.../base.apk
adb pull /data/app/.../base.apk /tmp/app.apk
unzip -o /tmp/app.apk -d /tmp/app
# 在 /tmp/app/res/mipmap-xxxhdpi 里找最大的那张（通常是 ic_launcher.png）

# 方法 B：截屏裁切（图标是矢量绘制、APK 里没有位图时用这个）
adb exec-out screencap -p > /tmp/screen.png
# 然后自己裁成正方形
```

> 图标是各家厂商的商标，**只在自己家里用没问题**；如果要把改好的包发出去，
> 记得换成你自己的图或者留空。

---

## 5. 字体（中文衬线）

应用名用的是衬线字体。Android 系统自带的 `serif` 家族**只包含拉丁字形**，
中文会掉回黑体，看起来就跟英文名不协调。所以项目里内嵌了一个只含常用字的
字体子集（22 KB）：

```
launcher/app/src/main/assets/app_name_serif.ttf
```

它只覆盖当前这 9 个应用名里出现的汉字。**你改了应用名单之后要重新生成**，
否则新出现的字会掉回黑体：

```bash
tools/make-font-subset.py \
  --names "夸克网盘,云视听小电视,乐播投屏,应用圈,华数鲜时光,芒果TV,网易云音乐,HDMI2,Spotify" \
  --out launcher/app/src/main/assets/app_name_serif.ttf
```

脚本默认从 macOS 的 `/System/Library/Fonts/Supplemental/Songti.ttc` 取字形。
Linux 上把 `SOURCE_FONT` 指向任意一个中文衬线字体（思源宋体 / Noto Serif CJK）即可。
需要 `pip install fonttools`。

嫌麻烦也可以直接换成系统字体：把 `CarouselView.java` 里的

```java
private final Typeface nameFace = loadNameFace();
```

改成

```java
private final Typeface nameFace = Typeface.create(Typeface.SERIF, Typeface.BOLD);
```

英文名会是衬线，中文名掉回黑体 —— 能用，只是不统一。

---

## 6. 分辨率适配

设计基准是 **1920×1080**（`DESIGN_W` / `DESIGN_H`），渲染时按等比缩放到实际屏幕
并居中，所以 1280×720、4K 都能直接用，边缘会留黑边。

- **4K 电视**：系统通常仍然按 1080p 合成，不需要改
- **非 16:9 屏幕**（比如 16:10 的安卓投影）：会出现上下黑边，想铺满就改
  `DESIGN_H`，但卡片位置是按 1080 调的，建议别动
- 卡片实际尺寸由 `--card-w` 那组常量（`CARD` / `HTML_CARD`）决定，等比放大即可

---

## 7. 遥控器按键

| 按键 | 行为 |
|---|---|
| ← / → | 切换应用 |
| 上 / 下 | 进入 / 退出顶部按钮（主题、设置、Wi-Fi） |
| 确认 | 打开应用 / 触发按钮 |
| **菜单键（三条杠）** | **不拦截**，交还给系统，所以还是弹出原厂侧边栏 |

想让菜单键做别的事，改 `onKeyDown()` 里加一个 `case KeyEvent.KEYCODE_MENU`。

---

## 8. 常见坑

| 现象 | 原因 / 解法 |
|---|---|
| 按 HOME 没有选择器 | 包名不在白名单。回到第 1 节 |
| 装不上，`UPDATE_INCOMPATIBLE` | 同包名已存在（多半是原厂桌面）。换白名单里的另一个名字，别卸载系统组件 |
| 启动后白屏/黑屏 | 大概率是图标资源缺失，看 `adb logcat \| grep -i carousel` |
| 切换掉帧 | 关掉 `GEAR_SUPERSAMPLE=4` 那种高开销烘焙，或者把 `ANIMATION_MS` 调长 |
| 中文名变成黑体 | 字体子集没覆盖这个字，跑第 5 节的脚本 |
| 电视重启后又回原厂桌面 | 有些固件会在开机时重置 HOME 角色，重新 `set-home-activity` 或停用原桌面 |
| OTA 升级后桌面被换回去 | 同上看，重新设一次 |
