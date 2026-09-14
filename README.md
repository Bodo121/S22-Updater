# S22 Updater 6.2

A clean control center for the personal IONSTACK-S22 / KernelSU phone project.
The app keeps the one-button root/update flow and persists progress across app
restarts within the same Android boot.

## Download And Install

Get the current APK, `SHA256SUMS`, and `app-update.json` from
[Releases](https://github.com/Bodo121/S22-Updater/releases/latest):

```sh
sha256sum -c SHA256SUMS
adb install -r S22-Updater-v6.2.apk
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

- **Home:** one context-aware action plus Device Doctor, exact-build status,
  temporary-root state, KernelSU module state, and KernelSU app authorization
  state. KernelSU loaded-but-ungranted is shown as permission pending, not a
  failed install.
- **Log:** session diagnostics, changelog refresh, share/export diagnostics, and
  a copyable GitHub issue report template.
- **Settings:** feed URL, customizable Material-style color palette, Shizuku
  tools, payload export, automatic KernelSU setup option, app updater, and
  install-permission status.

The UI uses a View-based Material-style system-accent palette, optional custom
color families, rounded/elevated cards, app-owned Material Symbols-style
outlined icons, and smooth tab/button/root-state/progress transitions. This repo
does not use Gradle/Compose, so dynamic color is implemented through the
platform accent color with static light/dark fallbacks rather than Compose's
`dynamicColorScheme` API.

## Persistent Progress

Durable state is stored in `SharedPreferences`:

- selected feed payload and cached feed JSON
- selected tab/page
- root, Shizuku, exploit, Device Doctor, and KernelSU state scoped to the current boot session
- live run log and session diagnostics
- last visible status text
- updater toggles and feed URL

Volatile root state is cleared automatically when the boot session changes,
because a real reboot wipes exploit/KSU state. If the kernel boot ID is
unavailable, the app uses stable same-boot fallbacks and never treats `unknown`
as proof of the same boot.

## Root And KernelSU Flow

The low-level temporary-root path is the known-working v5.0 implementation:
verified IONSTACK payload/helper staging to `/data/local/tmp`,
`EXPLOIT_ATTEMPTS=24`, `CVE43499_ROOT_HELPER`, `LD_PRELOAD`, live log polling,
120-second stall watchdog, 15-minute total watchdog, and helper `uid=0`
verification are preserved.

KernelSU loading is exact-build gated for `SM-S901B / S901BXXSNGZD7`. `insmod`
command output is diagnostic only: empty stdout is normal. The app checks module
presence before loading, skips duplicate `insmod`, runs the v5.0 helper-first
loader if needed, polls `/sys/module/kernelsu` or `/proc/modules`, then tests
whether S22-Updater itself has `su` uid 0. Module-loaded and app-authorized are
separate states.

The exploit is volatile: reboot clears root and the loaded module.

## Signed Manifest Support

`SignedManifest` supports Ed25519 verification of signed compatibility metadata.
The repository does not contain a private signing key. Until a public key is
compiled into the app, unsigned feed data is accepted only for the pinned
v5.0-known `r0s-S901BXXSNGZD7` payload URLs, sizes, and SHA-256 values.
