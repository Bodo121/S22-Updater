# S22 Updater 4.7

A rebuilt control center for the personal IONSTACK-S22 / KernelSU phone project.

## Download and install

Get the latest release (APK + SHA256SUMS + app-update.json) from
[Releases](https://github.com/Bodo121/S22-Updater/releases/latest):

```sh
sha256sum -c SHA256SUMS
adb install -r S22-Updater-v4.2.apk
```

Android 9 or later is required.

> Signing break from v3: v4 is signed with a new key (the v3 signing key was a
> local-only file and is gone), so uninstall v3 before installing v4. v4.2 also
> carries Android 9+ signing lineage from the local v4 debug key to the release
> key, so old local v4 debug builds can migrate without the package-conflict
> error. If your installed APK used any other lost key, Android still requires a
> one-time uninstall; future release-key updates then work normally.

## App updates

Settings → **Check for app updates** (optionally at every startup). The app
reads this repo's latest GitHub release, downloads the APK from the release's
`app-update.json` manifest, verifies its SHA-256 against the release
`SHA256SUMS`, then hands it to Android's package installer.

Releases are built by
[`.github/workflows/release.yml`](.github/workflows/release.yml): push a tag
matching the manifest (`v4.2` for versionName `4.2`) and the workflow builds,
signs, and publishes the APK + SHA256SUMS + app-update.json. `ci.yml` verifies
every other push with a full build and the host tests.

One-time setup for updatable releases — repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | base64 of the keystore that signed the installed app |
| `SIGN_STORE_PASS` | keystore password |
| `SIGN_KEY_PASS` | key password |
| `SIGN_ALIAS` | key alias |

Without them the release is signed with a throwaway key (fresh installs only).
With them, `signing-lineage.bin` is included automatically so Android 9+ can
accept the release key as the successor to the old local debug key.

### Permanent app identity

These values are frozen — changing any of them breaks in-place updates for
every installed app:

| Item | Value |
| --- | --- |
| Package name | `com.bodo121.s22updater` (`build.sh` fails otherwise) |
| Release cert SHA-256 | `8588a91199d914eb638776a6144705c3b1db24f0ec90472cd963aa25237ffeb5` |
| Keystore | `release.keystore` (git-ignored — keep a backup outside the repo) |
| Rotation proof | `signing-lineage.bin` (committed) |

Set `SIGN_EXPECTED_CERT_SHA256=8588a911…` (full digest above) when building a
release: the build fails instead of shipping a wrong-key APK. The v3 signing
key is lost, so v3 installs need the app's one-time "Uninstall old app" flow;
everything signed with the release key updates normally forever.

## Interface and workflows

- **Home:** one status card with a single context-aware action (check feed →
  download → root → run exploit → load KernelSU), a 4-step progress tracker,
  compact root/Shizuku rows, KernelSU-Next Manager detection
  (`com.rifsxd.ksunext`), device card, and live run log.
- **Log:** session diagnostics with a copy button; independent GitHub
  changelog refresh, so a changelog failure cannot block the feed.
- **Settings:** editable HTTPS feed URL, reset, Shizuku tools and handshake
  diagnosis, payload export, app updates with install-permission status, and
  automatic KernelSU setup option.
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

### Running the exploit

**Run exploit** stages the downloaded payload (and the root helper) into
`/data/local/tmp`, executes it with `EXPLOIT_ATTEMPTS=24`, streams the device
log with stall/total watchdogs, and looks for the `exploit completed` marker.
On success it verifies root through the helper and asks:

> Exploit completed — install KernelSU now?

It works through either privileged shell:

- **Root** (`su`): grant this app root in Home first.
- **Shizuku** (no root yet): install Shizuku, start it via wireless debugging,
  then Home → Authorize Shizuku shell. The app stages and runs everything
  through the Shizuku shell, exactly like `support/deploy.sh` over adb.

The root helper (`cve-2026-43499-root`) must come from the feed's optional
`helper: {url, size, sha256}` entry, or already be on the device from a
`deploy.sh` run. Without either, Run stops with instructions instead of
failing obscurely. Keep the phone idle with the screen on; on failure, reboot
for clean slabs and run again.

An incomplete, oversized, wrong-size, or hash-mismatched download does not
replace the previous private copy. If the feed has no SHA-256, the UI says so:
calculating a local hash alone does not verify it against a publisher hash.

Without root, **Export downloaded file** uses Android's document picker.
Old v1/v2 private files are preserved; v3 uses per-payload files rather than
automatically adopting the old shared staging file.

### KernelSU setup

The Home action loads the exact `KSU-S22` module through root or Shizuku. It
checks `SM-S901B`, the full GZD7 kernel release, the expected 432728-byte
module size and SHA-256. Loading prefers the IONSTACK root helper
(`<helper> -c 'insmod …'`, the documented manual-loader path) with a plain
`insmod` fallback. After loading, it verifies `/sys/module/kernelsu`; an
already-loaded module is skipped.

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
adb install -r out/S22-Updater-v4.2.apk
```

Override `JAVA_HOME`, `BT`, and `PLATFORM` for your installation. The output
filename follows the manifest version. `build.sh` downloads the pinned Shizuku
client AARs from Maven Central and verifies their SHA-256, compiles the
Shizuku binder stubs in `src/moe/shizuku/server/`, and dexes everything
without Gradle.

Signing: `SIGN_KEYSTORE`/`SIGN_STORE_PASS`/`SIGN_KEY_PASS`/`SIGN_ALIAS`
override the default throwaway debug key. Add `SIGN_LINEAGE=signing-lineage.bin`
when signing with the release key to include the debug-to-release migration
proof. Reuse one keystore for every build you ship, or Android will refuse
updates — back it up somewhere safe, it is git-ignored.

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
AndroidManifest.xml    App identity, version and permissions
build.sh               Build and sign using local Android tools
signing-lineage.bin    Android 9+ debug-to-release key-rotation proof
src/                   UI, downloads, file storage, root/Shizuku shells, KernelSU setup
src/moe/               Shizuku binder stubs (from Shizuku-API AIDL, Apache-2.0)
res/                   Launcher icon and Android resources
tests/                 Host regression tests and Android smoke test
.github/workflows/    CI build check + tagged release publisher
S22-Updater-v3.apk     Previous installable build (v4 needs a fresh install: new key)
SHA256SUMS             Published APK checksum
CHANGELOG.md           Release notes
```

Build output and signing keys are ignored. A fresh clone generates its own debug
key; use the original local key to produce an update compatible with the published APK.

## Projects

- [IONSTACK-S22](https://github.com/Bodo121/IONSTACK-S22)
- [KSU-S22](https://github.com/Bodo121/KSU-S22)
- [S22-Updater](https://github.com/Bodo121/S22-Updater)
