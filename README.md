# S22 Updater — payload version manager for IONSTACK-S22

Android app (minSdk 28, single activity, no root needed) that tracks
exploit payload versions:

- Fetches the targets feed (default: this project's
  [`IONSTACK-S22/support/targets-v3.json`](https://github.com/Bodo121/IONSTACK-S22/blob/main/support/targets-v3.json);
  any Root-My-Galaxy schema-v3 feed URL can be pasted instead, e.g. the
  `BuSung-dev/Root-My-Galaxy` feed).
- Shows the **installed version** as the sha256 of its private payload copy
  (`filesDir/cve-2026-43499-app.so`). The app never touches
  `/data/local/tmp` directly — that path is shell-only on stock Android.
- Downloads the selected payload artifact, compares sha256 with the
  installed copy, and on mismatch offers **Download & install update**:
  the new file is staged and the app prompts to **restart to install
  the new file** (on next launch the staged file becomes current).
- Shows **changelogs** (latest commit messages) from
  `Bodo121/IONSTACK-S22` and `Bodo121/KSU-S22`.

Scope (v1): file management only — running the exploit stays a
`support/deploy.sh` / adb job (see the XDA tutorial). No root required.

## Build (no Android Studio)

Needs JDK 17 + Android build-tools + a platform `android.jar`:

```sh
./build.sh   # -> out/S22-Updater-v1.apk (signed, debug key)
```

Override via env: `JAVA_HOME`, `BT` (build-tools dir), `PLATFORM` (android.jar).

## Install

```sh
adb install S22-Updater-v1.apk
```

## Credits

Payload/exploit: [IONSTACK-S22](https://github.com/Bodo121/IONSTACK-S22),
based on the IonStack CVE-2026-43499 work and the S22U port by
[sarabpal-dev](https://github.com/sarabpal-dev/IonStack-S22U).
KernelSU: [KSU-S22](https://github.com/Bodo121/KSU-S22).

## Disclaimer

Educational use on devices you own or are explicitly authorized to test.
I am not responsible for bootloops, data loss, Knox trips, warranty issues,
or anything else you do with these files.
