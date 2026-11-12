# FAQ

**Q：为什么不用 `adb`，非要装 Python？**
连接层是 `tools/tv.py`——一个纯 Python 的 ADB 客户端（来自 [CleanYourTV](https://github.com/Gathin23/cleanyourtv)，
MIT）。它直接走 TCP，所以在"没有 adb 二进制""adb 被别的工具占着""编程助手被沙箱限制了网络命令"
这些情况下仍然能连上电视。只需要一次：

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
```

不想用 Python 也没关系：电视端都是标准 ADB 命令，[ADAPT.md](ADAPT.md) 里逐条都写了对应的
`adb shell` 版本，照着敲一样能装。

**Q：`install.sh` 到底做了什么？失败了会不会把我的电视搞坏？**
它的顺序是固定的：**先装、先试启、最后才动原厂桌面**。任何一步不对就停，并把电视留在原样
（原厂桌面没被停用、HOME 没被改）。只有"新桌面确实启动起来了"之后，才停用**它认识的**
原厂桌面并把 HOME 指过去；最后再复查一次 HOME 归属，不对就回滚并把恢复命令写进
`~/ipodtvstyle-recovery-<ip>.txt`。详见 [ADAPT.md](ADAPT.md) 第 3 节。

**Q：安装时电视上弹了个"继续安装"，我没点。**
酷开/创维等固件会弹这个确认框，超时或选拒绝会得到 `INSTALL_FAILED_USER_RESTRICTED`。
在电视上点"继续安装"，然后重跑。`install.sh` 会识别这个错误并提示。

**Q：安装时报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。**
同包名的 app 已经存在，而且签名不同——最常见的原因是**你在另一台电脑上编译过**（debug key
每台机器不同），或者电视上原厂就有这个包。先把 HOME 还给原厂桌面，再 `pm uninstall` 掉旧包，
然后重跑。**不要卸载系统应用**。

**Q：装好了，但按 HOME 还是回原厂桌面。**
三种情况，按顺序排查：
1. 固件有包名白名单 → 见 [ADAPT.md](ADAPT.md) 第 1 节，编译时用
   `-PlauncherApplicationId=<白名单包名>`；
2. 这个机型的 Home 键不走 Android 的 HOME 偏好（部分创维/酷开固件走 `persist.service.homepage.*`）
   → 见第 5 节，先用 `STOCK_LAUNCHER=none` 当普通应用用；
3. 有多个 app 抢 HOME，落到了系统选择器 → 先在电视上手动选一次默认桌面，再重跑。

**Q：装好了，但一重启就回原厂桌面。**
有些固件在开机时重新分配 HOME 角色。重跑一次 `TV_HOST=... SKIP_BUILD=1 tools/install.sh`
（不重新编译，只重做交接），或者看 [ADAPT.md](ADAPT.md) 第 5 节。

**Q：怎么完全回到原样？**
```bash
# 1) 原厂桌面重新启用，HOME 还回去
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 <原桌面包名>'
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'cmd package set-home-activity --user 0 <原桌面>/<原Activity>'

# 2) 去掉新桌面
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall com.ipodtvstyle.home'
```
`install.sh` 每次成功都会把这几条按你这台电视的真实包名打印出来，照着复制就行。

**Q：某个应用没出现在轮播里，或者名字/图标不对。**
- 没出现：可能被 `hidden.txt` / `HIDDEN_PACKAGES` 挡掉了；或者它既没有 `LEANBACK_LAUNCHER`
  也没有 `LAUNCHER` 入口（纯后台服务本来就不该出现在桌面）；或者 `AndroidManifest.xml` 的
  `<queries>` 被改坏了。
- 名字是英文：那是应用自己声明的 label —— 我们直接读 `PackageManager`，
  `HDMI`、`Spotify` 这类本来就是英文。
- 图标读不到：卡片会退化成"应用名写在色块上"，看 `adb logcat | grep -i carousel`。

**Q：怎么把某个卡片去掉？**
不用重新编译，往应用的私有目录写一个 `hidden.txt`，一行一个包名：

```bash
adb push hidden.txt /sdcard/hidden.txt
adb shell run-as com.ipodtvstyle.home cp /sdcard/hidden.txt files/hidden.txt
```
回到桌面（HOME）就生效。详见 [ADAPT.md](ADAPT.md) 第 4 节。

**Q：中文应用名看起来是黑体，和英文名不协调。**
内嵌的衬线字体子集覆盖不到这个字，代码里逐字检查 `Paint.hasGlyph()` 后回落系统黑体 ——
这是**正常行为**，不会出豆腐块。想让所有中文名都用衬线就生成更大的子集：
`tools/make-font-subset.py`。

**Q：第一次按 HOME 会不会白屏一下？**
首帧只烘焙当前可见的 5 张卡片，其余在首帧之后每帧补 4 张（`WARMUP_CHUNK`）；窗口背景跟着
深浅主题走，所以深色模式不会再闪一下白。低端机觉得预热卡顿，把 `WARMUP_CHUNK` 调小即可。

**Q：切换的时候会掉帧。**
先看 `adb shell dumpsys gfxinfo <包名>`。常见原因：
- 还没预热到的卡片要现烘贴图 → 划到过的卡片不会重复烘焙，一轮之后就顺了
- 电视本身在跑别的负载
- 把 `navigate()` 里的 20 ms 节流调大一点

**Q：能不能像原厂那样直接显示视频/海报墙？**
可以，但那是另一套渲染器。当前实现是针对"Cover Flow 卡片"优化的：
每张卡片的贴图是预制位图，尺寸固定。要做视频墙需要换掉 `drawScene()`。

**Q：菜单键（三条杠）为什么不能用了？**
它被故意放行给系统，所以还是弹出原厂侧边栏。要拦下来就在
`onKeyDown()` 里加 `case KeyEvent.KEYCODE_MENU:`。

**Q：支持手机 / 平板吗？**
能装能跑，但布局是按 16:9 大屏调的，手机上卡片会很小；而且没有触摸处理，只能靠方向键。

**Q：为什么不做一个"设置界面"让我在电视上配置？**
有意为之。整个 app 只有一个 Activity、两个 Java 文件，刻意没有设置页和状态同步代码。
能不改代码就改的东西都放到了外部：`hidden.txt`（隐藏卡片）、`-PlauncherApplicationId`
（包名）、`tools/install.sh` 的环境变量（装法）。
