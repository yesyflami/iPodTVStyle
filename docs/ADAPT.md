# 适配到你的电视

> English summary: `python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt`,
> then `TV_HOST=<your tv ip> tools/install.sh`. It builds, installs, starts the launcher, verifies it,
> takes the HOME key only after that, and rolls back if HOME does not land on it. Change the package
> name (`-PlauncherApplicationId=`) or hide tiles (`hidden.txt`) only if this TV needs it — §1, §4, §5.
> The rest of this document is the detailed Chinese walkthrough.

这份文档按顺序解决四件事：**确认电视怎么认桌面 → 连上电视 → 装上去并接管 HOME → 换成你自己的应用**。

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

唯一的坑是：**一部分电视固件不允许任意包名成为默认桌面**（厂商留了白名单），
还有一部分机型（部分创维/酷开）**根本不走 Android 的 HOME 偏好**，Home 键归固件属性管。
所以本项目默认用中性包名安装、由脚本先问电视再动手；只有在查明白名单之后，
才在**编译时**用 `-PlauncherApplicationId=` 显式指定一次（第 1 节）。

---

## 0.5 先查你的机型在不在清单里

**[机型清单](MODELS.md)** 收录了 457 个机型，标了「已实测可换 / 明确不可换 / 未验证」。
先搜一下你的型号，能省掉大量试错：状态是 ❌ 的机器固件锁了 HOME，装不进去；
状态是 ⚠️ 的没被验证过，按本文档走一遍即可。

---

## 1. 查清这台电视怎么认桌面

要弄清三件事：**现在谁拥有 HOME**、**固件认不认任意包名**、**Home 键是不是走 Android 的偏好**。
前两件一条命令就能看：

```bash
# 现在谁拥有 HOME（install.sh 问的也是这一句）
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py \
  'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

# 有哪些 app 声明了 HOME
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py \
  'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
```

**先别改包名。** 直接按第 3 节跑 `install.sh`，它会：

1. 用中性包名 `com.ipodtvstyle.home` 安装；
2. 启动新桌面，并校验它真的起来了；
3. 只有前两步都成功，才停用**它认识的**原厂桌面、把 HOME 指过去；
4. 如果 HOME 最后没有落到新桌面，它把原厂桌面放回去，并告诉你原因。

第 4 步通过 → **这台机器不需要改包名**，就这样用。

### 只有白名单机型才需要指定包名

固件只接受白名单包名时，`set-home-activity` 会"成功"但 HOME 仍归原厂（所以第 3 节的复验很关键）。
这时才查白名单里的名字，并在**编译时**传进去——不改源码，也不改 `gradle.properties`：

```bash
# 一步到位
BUILD_ARGS=-PlauncherApplicationId=<白名单包名> TV_HOST=192.168.1.50 tools/install.sh

# 或者先单独编译，再只做安装交接
tools/build.sh -PlauncherApplicationId=<白名单包名>
TV_HOST=192.168.1.50 SKIP_BUILD=1 tools/install.sh
```

| 品牌 / 系统 | 已知的白名单包名 | 备注 |
|---|---|---|
| 创维 / 酷开 SkyOS | 见 `docs/devices/`；作者原机型用过 `com.coocaa.tvhome` | 作者那台 7S77（Android 8）认这个名字；**新型号不一定还认** |
| 小米 / PatchWall | 上游记录过 `com.ts.launcher` | 部分机型接受任意包名，先按默认试 |
| ColorOS TV（OPPO / 一加） | 上游记录过 `com.oneplus.tv.launcher2` | 默认 applicationId 会被拒 |
| Google TV / Sony | 通常不需要 | 停用原厂桌面即可拿到 HOME |

> 白名单里的名字**不一定等于当前桌面的包名**。直接填原厂桌面包名一般会被签名冲突
> （`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）挡住，因为那通常是系统应用。

---

## 2. 连上电视

### 电视端

1. 设置 → 关于 → 连点"版本号"7 次，打开开发者选项
2. 设置 → 开发者选项 → 打开 **USB 调试** 和 **网络调试 / ADB over network**
3. 设置 → 网络，记下电视的 IP（有线/无线都可以，同一个局域网就行）

### 电脑端

这个项目**不需要电脑上装 `adb`**：连接层是 `tools/tv.py`，一个纯 Python 的 ADB 客户端
（来自 [CleanYourTV](https://github.com/Gathin23/cleanyourtv)，MIT）。它直接用 TCP 连电视，
所以在被沙箱限制、没有 adb 二进制、或者 adb 被别的工具占着的机器上也能用。

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt   # 只需一次
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py --probe
```

- 打出型号 / Android 版本 → 通了
- 电视上弹"允许调试" → 用遥控器同意，再跑一次
- 端口被拒 → 电视的**网络调试**开关没开，或者路由器开了客户端隔离（AP isolation）
- 电视只开了「无线调试 + 配对码」（部分 Google TV）→ 用系统 `adb pair` 配一次，然后
  `TV_ADB_BINARY=$(which adb) TV_HOST=<ip>:<port>`，详见上游的 `tools/native-adb.md`

想继续用 `adb` 也行：`adb connect 192.168.1.50:5555 && adb devices`，只要显示 `device` 就够。
后面所有脚本用的是 `tools/tv.py`，两者不冲突。

---

## 3. 编译、安装、设为默认桌面

**一条命令：**

```bash
TV_HOST=192.168.1.50 tools/install.sh
```

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File tools\install.ps1 -TvHost 192.168.1.50
```

它按下面的顺序做，任何一步不对就停下，并把电视恢复到原样：

| 步骤 | 检查什么 |
|---|---|
| 1. 探测 | `tv.py --probe` 通不通；电视的 SDK 是否 ≥ `minSdk`（低于就直接停，不白编译一分钟） |
| 2. 编译 | `tools/build.sh`：自动找 JDK 17–22 与 Android SDK（不再写死 macOS 路径），产物 `launcher/app/build/outputs/apk/release/app-release.apk` |
| 3. 上传 | `b64push.py` 分块上传并用 md5 校验，失败不动目标文件 |
| 4. 安装 | `pm install` **单独执行**并判 `Success`；失败时原厂桌面完全没被动过 |
| 5. 启动 | `am start -W` 并同时校验 `Status: ok` 且没有 `Error:` —— 只是"把任务提到前台"不算 |
| 6. 交接 | 只有前 5 步都过了，才 `pm disable-user` 掉**它认识的**原厂桌面，然后 `cmd package set-home-activity` |
| 7. 复验 | 再查一次 HOME 归属；不是我们的就 `pm enable` 原厂桌面、把 HOME 还回去，并把恢复命令写进文件 |

它只停用认识的 4 个原厂桌面（`com.google.android.tvlauncher`、
`com.google.android.apps.tv.launcherx`、`com.google.android.leanbacklauncher`、`com.mitv.tvhome`）。
电视报的是别的名字（或者 `android` = 选择器）时它**不会猜**，而是停下来让你明确指定：

```bash
STOCK_LAUNCHER=com.tianci.movieplatform TV_HOST=192.168.1.50 tools/install.sh
STOCK_LAUNCHER=none TV_HOST=192.168.1.50 tools/install.sh   # 不动原厂桌面，只把新桌面装成普通应用
```

`STOCK_LAUNCHER=none` 是**最安全的路线**：原厂桌面继续当 Home，新桌面从应用列表里打开。
适合固件把 HOME 交给自家属性、或你只是想先看看效果的机型。

### 只在需要时才换包名

默认包名是中性的 `com.ipodtvstyle.home`。**只有当固件只把 HOME 交给白名单里的包名**时，
才需要在编译时指定（见 §4），不要在源码里改默认值。

### 还原

脚本结束时会把还原命令打出来，同时恢复文件会落在 `~/ipodtvstyle-recovery-<ip>.txt`：

```bash
# 1) 原厂桌面重新启用，并把 HOME 还给它
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 <stock>'
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py \
  'cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME <stock> | tail -n 1)'

# 2) 确认 HOME 归位
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py \
  'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

# 3) 想彻底去掉新桌面
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py 'pm uninstall com.ipodtvstyle.home'
```

用完 ADB 记得关掉调试，并确认端口真的关了：

```bash
python3 tools/adb-closed.py 192.168.1.50
```

---

## 4. 轮播里的应用从哪来

**从你自己的电视上读，不用改代码。** 桌面起来时向 `PackageManager` 要本机的应用清单：

1. 先查 `ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER`（真正的电视应用）；
2. 再查 `ACTION_MAIN` + `CATEGORY_LAUNCHER`（普通应用、侧载的手机应用）；
3. 同包名以 leanback 那条为准，去重；
4. 只出现在 `LAUNCHER` 里的系统命名空间条目（`android`、`com.android.*`、
   `com.google.android.*`）会被跳过——那些通常是设置面板，不是能打开的应用；
5. 每组按系统语言排序，电视应用在前、普通应用在后；
6. 信号源 trampoline（`com.coocaa.hdmi1..4`）统一排在**最后**，不会插在应用中间；
7. 名字用应用自带的 label（中文应用显示中文），图标用 `getApplicationIcon()`。

所以别人装完看到的是**他自己电视上的应用**，作者电视上那 9 个不会再冒出来。
装好之后又装了新应用，或改了 `hidden.txt`：回到桌面（HOME）时会比对一次，变了就重建轮播。

### 想隐藏某张卡片（不用重新编译）

往应用的私有目录里放一个文本文件，一行一个包名，`#` 开头是注释：

```bash
cat > /tmp/hidden.txt <<'EOF'
# 电视自带的广告入口
com.example.adbox
EOF
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py \
  'mkdir -p /sdcard/Android/data/com.ipodtvstyle.home/files'
# 私有目录需要 run-as 或 root 才能直接写，最省事的还是用 adb：
adb push /tmp/hidden.txt /sdcard/hidden.txt
adb shell run-as com.ipodtvstyle.home cp /sdcard/hidden.txt files/hidden.txt
```

**只在自家电视上用**：`run-as` 只对 debug 签名的包有效，本项目的 release 正是 debug 签名，
所以能这么写；换签名之后就得先用 root 或者重新编译。

想一次确认当前名单：

```bash
adb shell run-as com.ipodtvstyle.home cat files/hidden.txt
```

### 卡片长什么样

| 元素 | 来源 |
|---|---|
| 名字 | `PackageManager` 报告的应用名 |
| 图标 | `PackageManager.getApplicationIcon()` |
| 底色 | 从图标里采样出来的主色；和图标本身明暗太接近时自动换成浅底或深底，保证 logo 看得见 |
| 比例 | 按图标原始比例居中，宽度上限 54%，不拉伸；图标本身是整块铺满的方形就直接铺满卡片 |
| 字体 | 内置衬线子集覆盖得到就用衬线，否则逐字回落到系统黑体 |
| 日期 | 跟随电视语言：英文 `SUNDAY, NOV 8, 2026`，中文 `2026年11月8日星期日` |

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

## 5. 有些机型不把 HOME 交给第三方桌面

`tools/install.sh` 走的是标准做法：装好 → 校验能启动 → `pm disable-user` 原厂桌面 →
`cmd package set-home-activity`。这套在绝大多数 Android TV / Google TV 上成立，但**不是所有**：

| 机型 | 现象 | 现在怎么办 |
|---|---|---|
| 创维 / 酷开（部分固件） | Home 键和开机路径读固件属性 `persist.service.homepage.*`，每次开机被重写；`pm hide com.tianci.movieplatform` 会卡在开机 logo | 先 `STOCK_LAUNCHER=none`（原厂桌面留着，新桌面当普通应用）；想要 Home 键直达，得借助外部无障碍中继（如 Home on Fire）+ 开机自启，见上游 `docs/devices/skyworth-7t634-p3h.md` |
| 小米 / 部分 ColorOS TV | 固件只接受白名单包名 | 编译时 `-PlauncherApplicationId=<白名单包名>`，逐机型见 `docs/devices/` |
| 装了选择器却进不去 | HOME 被多个应用抢，落到了 `android`（选择器） | 先在电视上手动选一次默认桌面，再跑 `install.sh` |

脚本每次都会**先问电视**「HOME 现在归谁」，然后把结论打出来。凡是它认不出的答案，
它不会猜——停下来让你用 `STOCK_LAUNCHER=` 或 `-StockLauncher` 明确指定。

> 结论：能不能接管 Home 取决于固件，不取决于这个桌面写得好不好。`install.sh` 的职责是
> **不让一次失败把你困住**：先装、先试启、最后才动原厂桌面，失败就把原厂桌面放回去并写出恢复命令。

---

## 6. 字体（中文衬线）

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

## 7. 分辨率适配

设计基准是 **1920×1080**（`DESIGN_W` / `DESIGN_H`），渲染时按等比缩放到实际屏幕
并居中，所以 1280×720、4K 都能直接用，边缘会留黑边。

- **4K 电视**：系统通常仍然按 1080p 合成，不需要改
- **非 16:9 屏幕**（比如 16:10 的安卓投影）：会出现上下黑边，想铺满就改
  `DESIGN_H`，但卡片位置是按 1080 调的，建议别动
- 卡片实际尺寸由 `--card-w` 那组常量（`CARD` / `HTML_CARD`）决定，等比放大即可

---

## 8. 遥控器按键

| 按键 | 行为 |
|---|---|
| ← / → | 切换应用 |
| 上 / 下 | 进入 / 退出顶部按钮（主题、设置、Wi-Fi） |
| 确认 | 打开应用 / 触发按钮 |
| **菜单键（三条杠）** | **不拦截**，交还给系统，所以还是弹出原厂侧边栏 |

想让菜单键做别的事，改 `onKeyDown()` 里加一个 `case KeyEvent.KEYCODE_MENU`。

---

## 9. 常见坑

| 现象 | 原因 / 解法 |
|---|---|
| `install.sh` 说连不上电视 | 网络调试没开，或路由器开了客户端隔离。`tools/tv.py --probe` 先单独跑一次，见第 2 节 |
| 报"no Python environment yet" | 先 `python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt` |
| 报 `INSTALL_FAILED_USER_RESTRICTED` | 电视屏幕上有个"继续安装"确认框，超时或选了拒绝就变成这个错误。在电视上点继续，重跑 |
| 报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 同包名已存在且签名不同——通常是**别人电脑上编译的同一个包**。先把 HOME 还给原厂桌面，再 `pm uninstall` 掉旧包，然后重跑 |
| 报 `INSTALL_FAILED_OLDER_SDK` | 电视低于 Android 8.0（minSdk 26）。脚本在编译前就会拦住；如果用 `SKIP_BUILD=1` 跳过了预检才会走到这一步 |
| 装好了但 HOME 没变 | 固件白名单或厂商 Home 逻辑。脚本会打印"Home resolves to …"，按第 1 节和第 5 节处理 |
| 按 HOME 没有选择器 | 部分固件不弹选择器，只认 `set-home-activity` —— 这也正是脚本在做的事 |
| 第一次按 HOME 慢 / 白一下 | 已修：首帧只烘 5 张卡，其余在首帧之后分批预热；窗口背景跟随深浅主题。仍是低端机的话把 `WARMUP_CHUNK` 调小 |
| 切换掉帧 | 关掉 `GEAR_SUPERSAMPLE=4` 那种高开销烘焙，或者把 `ANIMATION_MS` 调长 |
| 应用列表和电视上不一致 | 被 `hidden.txt` / `HIDDEN_PACKAGES` 挡掉的不会出现；装完新应用或改完 `hidden.txt`，回一次 HOME 才会刷新 |
| 轮播里混进了系统设置面板 | 只出现在 `LAUNCHER` 里的 `com.android.*` / `com.google.android.*` 已被默认跳过；要彻底去掉就写进 `hidden.txt` |
| HDMI 卡片切错输入 | 只对 `com.coocaa.hdmiN` 这种 trampoline 生效；自定义包名要在 `hdmiSourceIndex()` 里补规则 |
| 中文名叫回黑体 | 衬线子集没覆盖这个字，属于正常回落；想统一衬线见第 6 节 |
| 日期变成英文 | 日期跟随电视语言；电视是英文系统就显示英文。想强制中文要改系统语言 |
| 电视重启后又回原厂桌面 | 有些固件会在开机时重置 HOME 角色，重跑 `install.sh`（`SKIP_BUILD=1`），或见第 5 节 |
| OTA 升级后桌面被换回去 | 同上，升级后重跑一次 |

