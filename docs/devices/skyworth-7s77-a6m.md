# 创维 Skyworth 7S77_A6M（中国大陆，SkyOS / Android 8.0）

作者自用机，也是这个项目最初那 9 张卡片的来源。下面只写这台机器上确认过的事实。

| 字段 | 值 |
|---|---|
| 型号 / 主板 | Skyworth 7S77_A6M / `sky848_9s60` |
| Android / SDK | 8.0 / 26（SkyOS） |
| UI | 1920×1080，240 dpi |
| 原厂桌面 | `com.tianci.movieplatform`（`com.coocaa.homepage.vast.*`） |
| HOME 现状 | 固件按包名放行，原厂桌面不一定是被放行的那个名字 |
| 是否需要白名单包名 | **需要**：`com.coocaa.tvhome`（本项目早期默认值，也是当年验证过的唯一名字） |
| Home 键是否走 Android 偏好 | 是（`cmd package set-home-activity` 后按 Home 生效） |
| 安装时的特殊行为 | 无确认弹框；`adb install -r` 直接成功 |
| 信号源 | `com.coocaa.hdmi1` … `com.coocaa.hdmi4` trampoline + `com.skyworth.tv.sdk.provider` 的 `METHOD_GOTO_SOURCE`（0 基） |

## 后果（写给下一个人）

- 这台机器**必须**用白名单包名，所以 `tools/install.sh` 用默认 `com.ipodtvstyle.home` 时，
  安装会成功、启动会成功，但最后一步 HOME 归属会失败 —— 脚本会回滚并如实报出来。
  这时改用：

  ```bash
  BUILD_ARGS=-PlauncherApplicationId=com.coocaa.tvhome TV_HOST=<ip> tools/install.sh
  ```

- 这台机器的 `com.coocaa.tvhome` 最早是"原厂历史包名、当前没有应用占用"的空位。
  **不要**把它当成创维新机型也认的名字：新固件不一定还有这个空位，装之前先按
  [ADAPT.md §1](../ADAPT.md) 查一次；如果安装报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，
  说明这个包名已经被系统应用占了，换名字。

- HDMI 卡片的 `index` 是 0 基：包名里的数字 - 1。早期那个"卡片写 HDMI 2、实际切到 HDMI 3"
  的 bug 就是这么来的（见 ADAPT.md §4）。

## 恢复

```bash
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.tianci.movieplatform'
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py \
  'cmd package set-home-activity --user 0 com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity'
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall com.coocaa.tvhome'
```

## 未验证

- 本机型的开机路径是否也认 Android 的 HOME 偏好（目前只验证过"按 Home 键"和重启后仍生效）。
- SkyOS 的 OTA 之后白名单是否变化。
- 其它 Skyworth/Coocaa 主板（8R40、9R52、7T63x 等）与本文档无关，它们的 Home 逻辑不一样，
  不要在那些机型上套用这里的包名。
