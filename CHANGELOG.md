# Changelog

## 4.8

### Added
- Experimental Lab tab: late module activation for reboot-required KernelSU
  modules. Replays boot-time module work for the current session — mounts in
  init's namespace, module scripts, then a zygote or full userspace restart —
  with per-step tracking (run/done/skipped/failed) and automatic verification
  when you reopen the app. Locked until Check root access reports granted;
  every step runs through KernelSU su. Opt-in per module via
  `/data/adb/late-modules.allow` plus `late-mounts.sh` / `late-post.sh` in the
  module dir.

### Changed
- After KernelSU loads, the app re-verifies su and switches the privileged
  shell to KernelSU su; the exploit helper stays bootstrap-only.
- New signing key (v2): the v4.x build-machine keystore was lost with a wiped
  workspace, so v4.8 starts a new key. One-time uninstall from any older
  build, then updates are normal again. The keystore is now also backed up in
  GitHub Actions secrets.

### Fixed
- Update flow, once and for all: install permission is re-checked at install
  time (not just before download), the package-conflict sheet no longer tells
  a stale key story — it explains any signer change and the one-time
  uninstall — and header/about versions now read the installed version
  instead of hardcoded strings.

## 4.7

### Fixed
- KernelSU verdict is now presence-based: a loader "Operation not permitted"
  warning no longer reports failure when /sys/module/kernelsu is live.

### Changed
- Simpler Home modeled on Root-My-Galaxy: one status card with a single
  context-aware action (check → download → root → run → KernelSU), a 4-step
  progress tracker, compact access rows, device card, and live log. Log and
  Settings keep history, feed, Shizuku tools, export, and app updates.

## 4.6

### Fixed
- Load KernelSU over Shizuku now streams the module over stdin instead of
  `cp` from app-private storage, which the shell user cannot read.
- App-update permission is shown in Settings, checked before downloading,
  and re-checked on resume — no more mid-update surprises.
- Exploit progress shows the latest live log line on the status view, like
  watching adb output.
- Package-conflict sheet now offers one-tap "Uninstall old app" for lost-key
  (v3) installs instead of a dead end.

### Changed
- Frozen app identity: package `com.bodo121.s22updater` is enforced by the
  build, and `SIGN_EXPECTED_CERT_SHA256` fails a release built with the wrong
  key, so one signature serves all future updates.

## 4.5

### Fixed
- Root cause of "no input stream": the hand-written Shizuku proxy decoded
  process streams with the legacy raw-fd parcel format, while Shizuku (like
  Root-My-Galaxy's generated stubs) uses typed-object parceling. Every
  Shizuku-spawned process now returns working stdin/stdout/stderr. Verified
  against transaction IDs and parcel code the AIDL compiler generates.

## 4.4

### Fixed
- Run-exploit no longer crashes with a raw NullPointerException when Shizuku
  returns an unusable process: null binders, refused processes and missing
  streams now produce plain-language errors naming the failed step.
- Run-exploit re-checks Shizuku authorization right before staging and routes
  to the authorize flow instead of failing mid-run.
- Diagnosis now probes server API version and `newProcess` support, since
  current Shizuku servers dropped the `newProcess` implementation the shell
  transport relies on.

### Verification
- Same checks as 4.3; on-device exploit run still needs phone testing.

## 4.3

### Fixed
- Run-exploit now exports `CVE43499_ROOT_HELPER` with the staged helper path,
  matching the rebuilt IONSTACK payload and the Root-My-Galaxy helper model.

### Verification
- Same checks as 4.2; on-device exploit run still needs phone testing.

## 4.2

### Fixed
- Shizuku provider registration now declares the manager API permission, actively
  requests binder delivery, waits before reporting unavailable, and uses AIDL
  transaction IDs that match Android's generated Binder stubs.
- App updates now preflight package name/signing compatibility and unknown-source
  install permission before launching Package Installer, so signature conflicts
  get a clear repair path instead of Android's generic package-conflict message.
- Release APK signing can include Android 9+ debug-to-release key lineage so old
  local debug-key installs can migrate to the stable release key.

### Changed
- Replaced old platform popups with rounded in-app sheets matching the UI.

## 4.1

### Added
- Shizuku handshake self-diagnosis: tests provider registration, the direct
  provider call, binder liveness, server version, authorization and a shell
  exec, reporting exactly which step fails with a copy button.

### Verification
- Same checks as 4.0; on-device Shizuku pairing still needs phone testing.

## 4.0

### Fixed
- Shizuku handshake: the client library needs the server Stub classes at
  runtime; complete hand-written stubs are now bundled, so the manager's
  binder delivery registers the app instead of dying silently. Added a sticky
  binder listener, resume-time recheck, an Open Shizuku button, and a guided
  authorize flow that actually triggers the manager approval dialog.
- App update check reports "no release published yet" with a releases-page
  shortcut instead of a bare HTTP 404.

### Added
- In-app self-updates: Settings → Check for app updates reads the repo's
  latest release (`app-update.json` + `SHA256SUMS`), verifies the APK hash and
  hands it to the package installer. Optional startup check.
- GitHub Actions: `ci.yml` (build + host tests on every push) and
  `release.yml` (tag `vX.Y` matching the manifest → signed APK + SHA256SUMS +
  app-update.json published as a GitHub release).
- Run exploit: stages payload + root helper to `/data/local/tmp` and executes
  with attempt budget, log watchdog and `exploit completed` detection.
- Shizuku shell transport (vendored client 13.1.5, hand-written binder stubs):
  full run/install flows work with no prior root once Shizuku is authorized.
- Post-run prompt: after verified root, the app asks whether to install
  KernelSU now.
- Feed `helper` artifact support (optional per-payload root-helper download),
  with fallback to an on-device helper staged by `deploy.sh`.

### Changed
- KernelSU loading prefers the root-helper manual-loader path with plain
  `insmod` fallback, through root or Shizuku.
- Build derives the APK filename from the manifest and accepts signing
  overrides for repeatable release keys.

### Verification
- Build, signature/alignment checks and host regression tests passed locally.
- On-device exploit, Shizuku and KernelSU flows still need phone testing.

## 3.0

### Fixed
- Startup callbacks no longer access unassigned status views.
- Action buttons have click listeners; long-running work runs off the UI thread.
- KernelSU-Next Manager detection uses its actual package ID and Android package visibility declarations.
- Failed downloads preserve the previous private copy; separate payload IDs use separate files.
- Downloading, copying a payload, and loading a kernel module have distinct status messages.
- Version metadata comes from the manifest and build output is named for v3.

### Changed
- Home, Updates, Activity and Settings navigation, system light/dark colors and launcher icon.
- Explicit root checks, payload download/install/export, progress and copyable diagnostics.
- Independent changelog refresh and editable HTTPS feed settings.
- Exact-build KernelSU late-load setup with module hash verification and loaded-state checking.
- Optional KernelSU setup after a successful app-authorized root check.

### Verification
- Build, signature/alignment checks and host regression tests passed locally.
- Launch/recreation instrumentation test is included and builds successfully.
- On-device launch, root installation and module loading still need phone testing.

## 2.0

Initial card UI and root/KernelSU integration attempt. This version had a startup
null-reference bug, unwired action buttons and inaccurate module installation status.
Superseded by 3.0.

## 1.0

Initial feed viewer, private-copy payload downloads and commit changelog display.
