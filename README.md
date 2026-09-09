# S22 Updater 3.0

A rebuilt control center for the personal IONSTACK-S22 / KernelSU phone project.

## Download and install

[**Download S22-Updater-v3.apk**](https://github.com/Bodo121/S22-Updater/raw/refs/heads/main/S22-Updater-v3.apk)

```sh
sha256sum -c SHA256SUMS
adb install -r S22-Updater-v3.apk
```

Download `SHA256SUMS` beside the APK to verify it. The published v3 APK uses the
same signing certificate as v2. Android 9 or later is required.

See [CHANGELOG.md](CHANGELOG.md) for the startup crash fix and overhaul details.
The repository root contains the current APK; older builds remain in Git history.

## Interface and workflows

- **Home:** device information, explicit root check, correct KernelSU-Next
  Manager detection (`com.rifsxd.ksunext`) and Manager launch.
- **Updates:** schema-v3 feed, payload selection, separate download/install/export
  actions, progress, local SHA-256 and feed hash comparison.
- **Activity:** session diagnostics with a copy button; independent GitHub
  changelog refresh, so a changelog failure cannot block the feed.
- **Settings:** editable HTTPS feed URL, reset, automatic KernelSU setup option.
- Rounded cards, touch feedback, launcher icon, system light/dark palettes.
- Startup works without a network connection or root. Root is requested when
  the user taps **Check root access**, not during launch.

### Installing a payload

1. In Updates, check the feed and select the exact firmware payload.
2. Download it. Files are stored separately by payload ID.
3. In Home, check root and grant this app access in your root manager.
4. Use **Install downloaded file with root**. It copies the file to
   `/data/local/tmp/cve-2026-43499`, checks the copied SHA-256, then replaces
   the destination. Downloading alone is not reported as a device install.

An incomplete, oversized, wrong-size, or hash-mismatched download does not
replace the previous private copy. If the feed has no SHA-256, the UI says so:
calculating a local hash alone does not verify it against a publisher hash.

Without root, **Export downloaded file** uses Android's document picker.
Old v1/v2 private files are preserved; v3 uses per-payload files rather than
automatically adopting the old shared staging file.

### KernelSU setup

The Home action loads the exact `KSU-S22` module using an already-granted
`su` session. It checks `SM-S901B`, the full GZD7 kernel release, the expected
432728-byte module size and SHA-256. After `insmod`, it verifies
`/sys/module/kernelsu`; an already-loaded module is skipped.

Enable **Load KernelSU after a successful root check** to perform that setup
after checking root. After running IONSTACK, return to Home and tap the root
check. This requires root usable by this app: root available only to an adb
helper is not the same as an app-authorized `su` session.

The module URL, release and hash are pinned in `KernelSetup.java`, using the
pair documented in [KSU-S22](https://github.com/Bodo121/KSU-S22). Module loading
lasts for the current boot. Manager APK installation remains separate.

The old v2 runner button was never connected and its copy-only module routine
did not load KernelSU. Version 3 uses the documented module-load operation and
does not present the old nonfunctional exploit runner as working.

## Build from source

Needs JDK 17, Android build-tools 34 and `android-34/android.jar`.

```sh
bash build.sh
adb install -r out/S22-Updater-v3.apk
```

Override `JAVA_HOME`, `BT`, and `PLATFORM` for your installation. Version values
come from the manifest. The existing local signing key is reused, so this APK
can upgrade v2. Keep that key locally for future updates; it is git-ignored.

## Verification

```sh
bash tests/check.sh
bash tests/check.sh --device
```

The first command runs host tests for hashes, failed-update preservation,
atomic replacement, path validation, HTTPS, bounded/truncated transfers and
cancellation. It also builds the instrumentation test APK.

The second additionally installs both APKs on a connected test phone and checks
launch, status views, action listeners, navigation and activity recreation.
It does not request root, run the exploit or load a kernel module.

Root installation and module loading still require validation on the target
phone. APK signature verification and compilation are not runtime testing.

For this version, the local build, host tests, APK signature and alignment checks
passed. The Android instrumentation tests were built but not run because no
device was connected.

If launch fails, collect:

```sh
adb logcat -b crash -d
```

## Repository layout

```text
AndroidManifest.xml   App identity, version and permissions
build.sh              Build and sign using local Android tools
src/                  UI, downloads, file storage, root and KernelSU setup
res/                  Launcher icon and Android resources
tests/                Host regression tests and Android smoke test
S22-Updater-v3.apk     Current installable build
SHA256SUMS            Published APK checksum
CHANGELOG.md          Release notes
```

Build output and signing keys are ignored. A fresh clone generates its own debug
key; use the original local key to produce an update compatible with the published APK.

## Projects

- [IONSTACK-S22](https://github.com/Bodo121/IONSTACK-S22)
- [KSU-S22](https://github.com/Bodo121/KSU-S22)
- [S22-Updater](https://github.com/Bodo121/S22-Updater)
