# Changelog

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
