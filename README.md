# S22 Updater 4.9

A rebuilt control center for the personal IONSTACK-S22 / KernelSU phone project.

## Download and install

Get the latest release (APK + SHA256SUMS + app-update.json) from
[Releases](https://github.com/Bodo121/S22-Updater/releases/latest):

```sh
sha256sum -c SHA256SUMS
adb install -r S22-Updater-v4.9.apk
```

Android 9 or later is required.

> Signing breaks (platform rule, not a bug): the v3 key is lost, and the v4.x
> build-machine keystore was lost with a wiped workspace, so v4.8 starts a new
> key (v2). Uninstall any older build before installing v4.8 — one time only.
> The app detects a signer change before installing and walks you through it
> instead of failing. The v2 keystore is backed up in GitHub Actions secrets,
> so this rotation never happens again.

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
With them, the release uses the backed-up v2 key — no lineage needed, since
the v2 key starts its own line.

### Permanent app identity

These values are frozen — changing any of them breaks in-place updates for
every installed app:

| Item | Value |
| --- | --- |
| Package name | `com.bodo121.s22updater` (`build.sh` fails otherwise) |
| Release cert SHA-256 (v2, since 4.8) | `6ba56772f7e69a226a983f8d06849602caae2c3d58a22717b35ccc6ff39712b7` |
| Previous cert (v4.2–v4.7) | `8588a91199d914eb638776a6144705c3b1db24f0ec90472cd963aa25237ffeb5` (key lost) |
| Keystore | `release.keystore` (git-ignored — backed up in Actions secrets + offline) |
| Old rotation proof | `signing-lineage.bin` (kept for history; not used by v2 builds) |

Set `SIGN_EXPECTED_CERT_SHA256=6ba56772…` (full digest above) when building a
release: the build fails instead of shipping a wrong-key APK. Any install
signed by an older key needs the app's one-time "Uninstall old app" flow;
everything on the v2 key updates normally forever.

## Interface and workflows

- **Home:** one status card with a single context-aware action (check feed →
  download → root → run exploit → load KernelSU), a 4-step progress tracker,
  compact root/Shizuku rows, KernelSU-Next Manager detection
  (`com.rifsxd.ksunext`), device card, and live run log.
- **Log:** session diagnostics with a copy button; independent GitHub
  changelog refresh, so a changelog failure cannot block the feed.
- **Lab (experimental):** late activation for reboot-required KernelSU
  modules plus Session Debloat. Debloat stops a package, blocks background
  appops, uninstalls/disables/suspends it for user 0 where Android allows it,
  overlay-whiteouts its system APKs in init's namespace, restarts userspace,
  and verifies on reopen. Unlocks after root is granted; runs only through
  KernelSU su. See the sections below.
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

### Lab: late module activation (experimental)

A real reboot wipes volatile root, so reboot-required modules can never see a
real boot. Lab replays the parts that matter **after** KernelSU is loaded,
all through KernelSU su:

1. Verify the module is loaded and su grants uid 0.
2. Stage a hash-verified runner to `/data/local/tmp/ksu-late-activate.sh`.
3. Apply each allowlisted module's `late-mounts.sh` in init's namespace.
4. Run each module's `late-post.sh`.
5. Restart zygote (or full userspace `stop`/`start`), then verify on reopen.

Opt a module in (as root, e.g. via `su`):

```sh
echo myoverlay > /data/adb/late-modules.allow
# /data/adb/modules/myoverlay/late-mounts.sh — mounts only, idempotent:
#   grep -q ' /system ' /proc/1/mountinfo || mount -t overlay overlay \
#     -o lowerdir=/system,upperdir=/data/ovr/upper,workdir=/data/ovr/work /system
# /data/adb/modules/myoverlay/late-post.sh   — optional post-mount scripts
```

The restart closes the app; reopening runs verification automatically (su,
module presence, init-namespace overlays, runner log tail). Steps that have
nothing to do report *skipped*, not failed. Boot-image, fstab, AVB, and
early-init modules remain impossible on a locked bootloader.

### Lab: session debloat (experimental)

Enter a package name in Lab, inspect it first, then **Remove for this
session**. The app stages a hash-verified runner to `/data/local/tmp`, then
runs this through KernelSU su:

1. Capture system APK paths from `cmd package path`.
2. Force-stop the package and block background appops.
3. Try `cmd package uninstall --user 0`, disable-user and suspend.
4. Overlay-whiteout every system APK path in init's mount namespace.
5. Restart zygote or full Android userspace.
6. Verify on reopen that user 0 no longer sees the package.

This is deliberately session-only: verified partitions are not modified and a
real reboot restores stock files. It is stronger than merely hiding a launcher
icon because the package is stopped, background-restricted, removed/disabled
from user 0 when possible, and its backing system APKs disappear from the
restarted framework view. Restore reverses the appops/user state and removes
S22 Updater's whiteouts for the current session.

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
