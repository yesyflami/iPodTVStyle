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

**Q：应用名显示成英文 / 显示成我数组里写的兜底名字。**
两种可能：
1. `AndroidManifest.xml` 的 `<queries>` 没加这个包名（Android 11+ 必须加才能查到别人的 label）
2. 这个 app 真的就叫那个名字 —— 我们用 `PackageManager.getApplicationLabel()`，
   `HDMI2`、`Spotify` 这类本来就是英文

**Q：中文应用名看起来是黑体，和英文名不协调。**
内嵌的衬线字体子集没覆盖这个字。跑 `tools/make-font-subset.py` 重新生成，
或者换回系统 `serif`（英文衬线、中文黑体）。

**Q：切换的时候会掉帧。**
先看 `adb shell dumpsys gfxinfo <包名>`。常见原因：
- 卡片图标太大（> 512×512）→ 重新导出成 256 左右
- 电视本身在跑别的负载
- 把 `navigate()` 里的 20 ms 节流调大一点

**Q：能不能像原厂那样直接显示视频/海报墙？**
可以，但那是另一套渲染器。当前实现是针对"9 个 app 的 Cover Flow"优化的：
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
