# 交给助手的安装指令（复制即用）

把下面整段复制给你的编程助手（Claude Code / Codex / Gemini CLI / Cursor 都行），
**只需要改一处：电视 IP**。

这一份是「执行手册」，不是文档。助手照着做，不要让它自己发挥。

---

```text
我在用 iPodTVStyle 把电视桌面换成 Cover Flow。请严格按仓库 docs/ADAPT.md 的流程执行，
不要自己发明步骤，也不要跳过探测包名那一步。

【我的电视】
- IP：192.168.1.50        ← 改成你电视的 IP（设置 → 网络里看）
- 品牌型号：创维 7S77_A6M
- 系统：Android 8.0
- 当前桌面：创维原厂（cyberui 之类）

【请依次完成】

1. 连接
   adb connect 192.168.1.50:5555
   adb devices        # 必须是 device；unauthorized 就到电视上点「允许」

2. 先记录还原点（这一步最重要，做之前不要动任何东西）
   adb shell cmd package resolve-activity -c android.intent.category.HOME
   把输出里的 packageName 和 Activity 名字完整抄下来，最后要写进还原命令

3. 探测这台机器的 HOME 策略（docs/ADAPT.md 第 1 节）
   - 查到包名，且和当前前台桌面一致 → 说明不需要伪装，用默认 com.ipodtvstyle.home
   - 查到包名，但和当前前台桌面不同 → 说明有白名单，用查到的那个包名
   - 查不到 / 报错 → 停下来告诉我，这台可能锁了 HOME

4. 改配置
   launcher/gradle.properties 里的 homeApplicationId 改成第 3 步的结论

5. 编译
   ./tools/build.sh

6. 安装并设为默认桌面
   ./tools/install.sh 192.168.1.50:5555

7. 交付还原命令
   把第 2 步抄下来的包名和 Activity 拼成一条能直接粘贴执行的还原命令给我，
   我要存到手机备忘录里

【约束，不要违反】
- 不要卸载任何系统应用，不要 root，不要刷机
- 不要执行 adb kill-server（我这边其他工具也在用同一个 adb 服务）
- 如果 adb install 报 INSTALL_FAILED_UPDATE_INCOMPATIBLE，立刻停下告诉我，
  不要尝试强行覆盖或卸载原包 —— 那可能是原厂桌面本身
- 每一步做完告诉我结果，失败就停，不要连续试错
```

---

## English version

```text
I want to replace my TV's home screen with iPodTVStyle (Cover Flow). Follow
docs/ADAPT.md in this repo exactly — do not invent steps and do not skip the
package-name probe.

【My TV】
- IP: 192.168.1.50          ← change this
- Model: Skyworth 7S77_A6M
- OS: Android 8.0

【Do this, in order】

1. Connect
   adb connect 192.168.1.50:5555
   adb devices            # must say "device"

2. Record the rollback point BEFORE touching anything
   adb shell cmd package resolve-activity -c android.intent.category.HOME
   copy the packageName and Activity verbatim

3. Probe the HOME policy (docs/ADAPT.md §1)
   - resolves to the current launcher  → no spoofing, keep com.ipodtvstyle.home
   - resolves to a *different* package → firmware allow-list, use that name
   - errors out                        → stop and tell me, HOME may be locked

4. Edit launcher/gradle.properties → homeApplicationId

5. Build:  ./tools/build.sh

6. Install: ./tools/install.sh 192.168.1.50:5555

7. Give me the exact restore command, ready to paste

【Rules】
- Never uninstall system apps, never root, never flash
- Do not run `adb kill-server` — other tools of mine share it
- On INSTALL_FAILED_UPDATE_INCOMPATIBLE stop immediately and tell me; do not
  force-overwrite or uninstall the existing package
- Report after each step; stop on the first failure instead of retrying blindly
```
