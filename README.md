# S22 Updater 5.0

A clean control center for the personal IONSTACK-S22 / KernelSU phone project.
The app keeps the one-button root/update flow and persists progress across app
restarts within the same Android boot.

## Download And Install

Get the current APK, `SHA256SUMS`, and `app-update.json` from
[Releases](https://github.com/Bodo121/S22-Updater/releases/latest):

```sh
sha256sum -c SHA256SUMS
adb install -r S22-Updater-v5.0.apk
```

Android 9 or later is required.

## App Updates

Settings -> **Check for app updates** reads the latest GitHub release,
downloads the APK from `app-update.json`, verifies its SHA-256, checks package
and signing compatibility, then opens Android's package installer directly.
No browser or store handoff is required for normal updates.

If Android reports a signing mismatch, the app explains the one-time platform
rule and offers an uninstall shortcut. After a fresh install on the current v2
signing key, future releases update normally.

## Signing Identity

| Item | Value |
| --- | --- |
| Package | `com.bodo121.s22updater` |
| Release cert SHA-256 (v2) | `6ba56772f7e69a226a983f8d06849602caae2c3d58a22717b35ccc6ff39712b7` |
| Keystore | `release.keystore` (git-ignored, backed up in GitHub Actions secrets) |

`build.sh` appends every build's version/APK/cert fingerprint to
`.signature-log`. That file is local-only and git-ignored so future work has a
persistent signing reference without pushing secrets or private fingerprints.

## Interface

- **Home:** one context-aware action: check feed -> download payload -> check
  root -> run exploit -> load KernelSU -> open Manager. Progress, root state,
  device info, and live exploit output are restored after app restarts.
- **Log:** session diagnostics and changelog refresh.
- **Settings:** feed URL, Shizuku tools, payload export, automatic KernelSU
  setup option, app updater, and install-permission status.

The UI uses a View-based Material-style system-accent palette, rounded/elevated
cards, cohesive action icons, and smooth tab/button/root-state transitions. This
repo does not use Gradle/Compose, so dynamic color is implemented through the
platform accent color with static light/dark fallbacks rather than Compose's
`dynamicColorScheme` API.

## Persistent Progress

Durable state is stored in `SharedPreferences`:

- selected feed payload and cached feed JSON
- selected tab/page
- root, Shizuku, exploit, and KernelSU state scoped to the current boot ID
- live run log and session diagnostics
- last visible status text
- updater toggles and feed URL

Volatile root state is cleared automatically if `/proc/sys/kernel/random/boot_id`
changes, because a real reboot wipes exploit/KSU state.

## Root And KernelSU Flow

The app stages the verified IONSTACK payload and helper to `/data/local/tmp`,
runs the exploit with `EXPLOIT_ATTEMPTS=24`, watches the live exploit log, and
verifies root through the helper. KernelSU loading is exact-build gated for
`SM-S901B / S901BXXSNGZD7`; after the module loads, the app immediately probes
KernelSU `su` (`id -u`, `id`, `/sys/module/kernelsu`) and records root success
when `su` genuinely grants uid 0.

The exploit is volatile: reboot clears root and the loaded module.
