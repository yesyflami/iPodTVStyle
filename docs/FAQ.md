# FAQ

**Q：按 HOME 键没有出现选择器，直接进了原厂桌面。**
包名不在固件的白名单里。跑
`adb shell cmd package resolve-activity -c android.intent.category.HOME`
看系统认的包名，然后改 `launcher/gradle.properties` 的 `homeApplicationId`
重新编译安装。详见 [ADAPT.md](ADAPT.md) 第 1 节。

**Q：安装时报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。**
电视上已经有一个同包名的 app（通常是原厂桌面），签名对不上。**不要卸载系统组件**，
换一个白名单里的包名。

**Q：装好了，但一重启就回原厂桌面。**
有些固件在开机时重新分配 HOME 角色。两个办法：
`adb shell cmd package set-home-activity <包名>/com.gathin.tvhome.MainActivity`，
或者停用原厂桌面 `adb shell pm disable-user --user 0 <原桌面包名>`（先记下包名）。

**Q：怎么完全回到原样？**
```bash
adb shell pm enable <原桌面包名>
adb shell cmd package set-home-activity <原桌面>/<原Activity>
adb uninstall <homeApplicationId>
```
动手前先把 `adb shell dumpsys package | grep -B4 HOME` 的输出存一份。

**Q：某个应用没出现在轮播里，或者名字/图标不对。**
- 没出现：它可能被 `HIDDEN_PACKAGES` 挡掉了；或者它既没有 `LEANBACK_LAUNCHER`
  也没有 `LAUNCHER` 入口（纯后台服务本来就不该出现在桌面）；或者
  `AndroidManifest.xml` 的 `<queries>` 被改坏了。
- 名字是英文：那是应用自己声明的 label —— 我们直接读 `PackageManager`，
  `HDMI2`、`Spotify` 这类本来就是英文。
- 图标读不到：卡片会退化成"应用名写在色块上"，看 `adb logcat | grep -i carousel`。

**Q：中文应用名看起来是黑体，和英文名不协调。**
内嵌的衬线字体子集覆盖不到这个字，代码里逐字检查 `Paint.hasGlyph()` 后回落系统黑体 ——
这是**正常行为**，不会出豆腐块。想让所有中文名都用衬线就生成更大的子集：
`tools/make-font-subset.py`。

**Q：切换的时候会掉帧。**
先看 `adb shell dumpsys gfxinfo <包名>`。常见原因：
- 第一次划到某张卡片时要现烘贴图（应用超过 15 个时才这样）→ 划过一轮之后就顺了
- 电视本身在跑别的负载
- 把 `navigate()` 里的 20 ms 节流调大一点

**Q：能不能像原厂那样直接显示视频/海报墙？**
可以，但那是另一套渲染器。当前实现是针对"Cover Flow 卡片"优化的：
每张卡片的贴图是预制位图，尺寸固定。要做视频墙需要换掉 `drawScene()`。

**Q：菜单键（三条杠）为什么不能用了？**
它被故意放行给系统，所以还是弹出原厂侧边栏。要拦下来就在
`onKeyDown()` 里加 `case KeyEvent.KEYCODE_MENU:`。

**Q：支持手机 / 平板吗？**
能装能跑，但布局是按 16:9 大屏调的，手机上卡片会很小。

**Q：为什么不做一个"设置界面"让我在电视上配置？**
有意为之。整个 app 只有一个 Activity、两个 Java 文件，改配置就是改代码重新编译。
加设置页会让这个 181 KB 的包变成一堆偏好存储 + 状态同步代码，
和"够小、够快、看得懂"的目标相悖。
