# Changelog

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
