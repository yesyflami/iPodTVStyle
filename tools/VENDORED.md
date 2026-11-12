# Vendored files

These four files are copied verbatim from [CleanYourTV](https://github.com/Gathin23/cleanyourtv)
(MIT, © Gathin) and are **not** modified here, so they can be replaced wholesale when upstream
fixes something:

| File | What it is |
|---|---|
| [`tv.py`](tv.py) | Pure-Python ADB-over-TCP client. No `adb` binary needed, which is what makes the install work from a sandboxed coding agent. Reads `TV_HOST` / `TV_PORT`; optionally `TV_ADB_BINARY` for a paired Wireless-debugging transport. |
| [`b64push.py`](b64push.py) | Uploads the APK as base64 chunks through shell commands, and verifies it with md5sum before it replaces the target. |
| [`adb-closed.py`](adb-closed.py) | Checks that the TV's network ADB port is really closed once you turn debugging off. |
| [`requirements.txt`](requirements.txt) | `adb-shell`, `rsa`, `cryptography`. |

Everything else under `tools/` is ours: `build.sh`, `install.sh` and their PowerShell twins, and
the font-subset script.

Licence: MIT, the same as this repository. See the upstream [`LICENSE`](https://github.com/Gathin23/cleanyourtv/blob/main/LICENSE).
Upstream changes these files often; if an install starts failing, diff against upstream before
debugging our own scripts.

The *handover order* in `install.sh` follows upstream's `launcher/build-and-install.sh` as well:
install and verify the APK, start the new launcher and verify it came up, only then disable a stock
launcher we recognise, and roll all of it back if Home does not land on ours.
