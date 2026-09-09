# S22 Updater v2 — payload & KernelSU manager for IONSTACK-S22

Modern Android app (minSdk 28, single activity) for managing IONSTACK-S22 exploit payloads and KernelSU modules:

- **Modern Material UI**: Card-based clean layout with real-time status indicators, progress feedback, and recent changelogs.
- **Feed Integration**: Fetches schema-v3 targets JSON (defaults to IONSTACK-S22 support feed, custom URLs supported).
- **In-App Staged & Root Install**: Compares remote vs installed payload SHA-256. If root is available, allows direct installation to `/data/local/tmp/cve-2026-43499` and KernelSU modules to `/data/kernelsu/modules/`. Falls back to staging for manual/deploy.sh installation.
- **Root & KernelSU Checker**: Automatically checks `su` availability and detects KernelSU Manager (`me.tongfei.kerneldebug`).
- **Exploit Runner (Root)**: Optional root execution runner for the exploit binary.
- **Changelog Viewer**: Fetches and displays recent commit messages directly from `Bodo121/IONSTACK-S22` and `Bodo121/KSU-S22`.

## Build (no Android Studio)

Needs JDK 17 + Android build-tools + a platform `android.jar`:

```sh
./build.sh   # -> out/S22-Updater-v1.apk (signed with debug key)
```

## Install

```sh
adb install S22-Updater-v2.apk
```

## Credits

Payload/exploit: [IONSTACK-S22](https://github.com/Bodo121/IONSTACK-S22).
KernelSU: [KSU-S22](https://github.com/Bodo121/KSU-S22).

## Disclaimer

Educational use on devices you own or are explicitly authorized to test.
