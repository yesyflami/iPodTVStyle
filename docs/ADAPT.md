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

## 4. 轮播里的应用从哪来

**从你自己的电视上读，不用改代码。** 桌面起来时向 `PackageManager` 要本机的应用清单：

1. 先查 `ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER`（真正的电视应用）；
2. 再查 `ACTION_MAIN` + `CATEGORY_LAUNCHER`（普通应用、侧载的手机应用）；
3. 同包名以 leanback 那条为准，去重；
4. 两组各自按系统语言排序，电视应用排在前面；
5. 名字用应用自带的 label（中文应用显示中文），图标用 `getApplicationIcon()`。

所以别人装完看到的是**他自己电视上的应用**，作者电视上那 9 个不会再冒出来。
装好之后又装了新应用：回到桌面（HOME）时会自动比对清单，变了就重建轮播。

### 想隐藏某个应用

`CarouselView.java` 里有一个默认为空的名单：

```java
private static final String[] HIDDEN_PACKAGES = {};
```

把不想看到的包名填进去即可（比如电视自带的某个广告入口）：

```java
private static final String[] HIDDEN_PACKAGES = {"com.example.adbox"};
```

### 卡片长什么样

| 元素 | 来源 |
|---|---|
| 名字 | `PackageManager` 报告的应用名 |
| 图标 | `PackageManager.getApplicationIcon()` |
| 底色 | 从图标里采样出来的主色；和图标本身明暗太接近时自动换成浅底或深底，保证 logo 看得见 |
| 比例 | 按图标原始比例居中，宽度上限 54%，不拉伸；图标本身是整块铺满的方形就直接铺满卡片 |
| 字体 | 内置衬线子集覆盖得到就用衬线，否则逐字回落到系统黑体 |

不用再从电视上抠图标、也不用往 `res/` 里放任何图片。

> 图标是各家厂商的商标，但这里读的是**你自己机器上已安装应用**的图标，
> 项目本身不再分发任何第三方 logo。

### 信号源（HDMI）卡片

创维/酷开电视的 HDMI 切换不是启动某个 Activity，而是向系统 provider 发一次调用：

```java
Bundle args = new Bundle();
args.putString("name", "HDMI");
args.putInt("index", 1);                       // 0 基：1 = HDMI 2
getContentResolver().call(
        Uri.parse("content://com.skyworth.tv.sdk.provider/method"),
        "METHOD_GOTO_SOURCE", null, args);
```

`index` 写错就会出现「卡片写着 HDMI 2，实际切到 HDMI 3」——这正是早期版本的一个真实 bug。

现在这一条不用手写了：`hdmiSourceIndex()` 按包名识别 SkyOS 的信号源 trampoline
（`com.coocaa.hdmi1` … `com.coocaa.hdmi4`），包名里的数字就是 1 基的输入号，
所以 `com.coocaa.hdmi2` 自动换算成 `index = 1`。provider 不存在时（非创维机型）
自动退回按包名启动，其它品牌不会崩。

---

## 5. 字体（中文衬线）

应用名默认用内置的衬线子集：

```
launcher/app/src/main/assets/app_name_serif.ttf
```

它只有 22 KB，覆盖常用字。覆盖不到的字会逐字回落到系统黑体（`Paint.hasGlyph()`），
**所以新应用名不会出现豆腐块**，也不需要为了换应用而重新生成字体。

想让所有中文名都用衬线，可以生成一个更大的子集：

```bash
tools/make-font-subset.py \
  --names "夸克网盘,云视听小电视,乐播投屏,应用圈,华数鲜时光,芒果TV,网易云音乐" \
  --out launcher/app/src/main/assets/app_name_serif.ttf
```

脚本默认从 macOS 的 `/System/Library/Fonts/Supplemental/Songti.ttc` 取字形。
Linux 上把 `SOURCE_FONT` 指向任意一个中文衬线字体（思源宋体 / Noto Serif CJK）即可。
需要 `pip install fonttools`。

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
| 启动后白屏/黑屏 | 轮播空的时候会退化成一张“设置”卡片，所以白屏多半是 `homeApplicationId` 不对或 `queries` 被改坏了 |
| 切换掉帧 | 关掉 `GEAR_SUPERSAMPLE=4` 那种高开销烘焙，或者把 `ANIMATION_MS` 调长 |
| 应用列表和电视上不一致 | 被 `HIDDEN_PACKAGES` 挡掉的不会出现；装完新应用回一次 HOME 才会刷新 |
| HDMI 卡片切错输入 | 只对 `com.coocaa.hdmiN` 这种 trampoline 生效；自定义包名要在 `hdmiSourceIndex()` 里补规则 |
| 中文名叫回黑体 | 衬线子集没覆盖这个字，属于正常回落；想统一衬线见第 5 节 |
| 电视重启后又回原厂桌面 | 有些固件会在开机时重置 HOME 角色，重新 `set-home-activity` 或停用原桌面 |
| OTA 升级后桌面被换回去 | 同上看，重新设一次 |
