# Changelog

## 6.0

### Added
- v5.0 compatibility baseline document and extracted `ExploitRunner` preserving
  known-working temporary-root execution behavior.
- Structured command results for exit code, stdout, stderr, timeout, and
  exception handling; stdout length is never a success/failure signal.
- Explicit KernelSU controller with module-loaded, permission-pending,
  root-granted, load-failed, and verification-failed states.
- Device Doctor preflight for Samsung SM-S901B / S901BXXSNGZD7 exact-build
  compatibility, kernel, fingerprint, SELinux, storage, battery, and boot hash.
- Verified payload cache helper with atomic `.part` downloads, reuse, and Clear
  payload cache action.
- Copyable GitHub issue report template and richer diagnostics.
- Ed25519 signed-manifest verification support, failing closed when no public
  key is configured; unsigned feed metadata is limited to the pinned v5.0-known
  target values.

### Changed
- Root workflow now starts with compatibility checking and self-heals by
  rediscovering actual KernelSU module state on startup/recheck.
- KernelSU load flow checks module presence before insmod, never reruns insmod
  against an already-loaded module, and verifies actual module state after the
  command including silent successful insmod.
- KSU module loaded but app su permission missing is reported as
  WAITING_FOR_MANAGER_PERMISSION, not load failure.

### Tested
- Host tests cover silent insmod success, missing module after exit 0, real load
  failure, already-loaded skip, File-exists/already-loaded, permission pending,
  full root, reboot reset, process restart rediscovery, uid parsing, timeout,
  unknown probe, delayed module visibility, separate stdout/stderr capture,
  pipe flood/no deadlock, process timeout, execution exception, and interruption.

## 5.1

### Added
- Hero six-step Home flow visual: connected numbered/checkmark stepper for
  feed, download, root, exploit, KernelSU, and Manager.
- Persistent root/KSU status chip in the header.
- Customizable Material-style color presets: System, Blue, Green, Purple,
  Orange.
- Diagnostics share/export from the Log tab.
- Cohesive app-owned Material Symbols-style outlined vector icons for tabs and
  actions.

### Changed
- Semantic contained state colors: accent for active work, green for completed
  flow/KSU/root states, red for contained failures.
- Motion polish for tab changes, buttons, progress transitions, logs, root
  grants, and stepper state changes.
- Root check now automatically completes the KernelSU stage when the KSU module
  is already loaded and the app has a valid KSU su grant.

### Fixed
- KernelSU stage auto-completes whenever root is granted and the module is
  already live: presence detection is tolerant (`contains`, not exact match),
  a successful root check re-marks the stage without re-running the loader,
  and the loader short-circuits before downloading when sysfs already shows
  the module — no more `insmod` failure on an already-loaded module.
- KernelSU setup via a rootless Shizuku shell now explains itself (insmod
  needs real root) instead of reporting a bare loader error.
- Action icons: full label mapping so Load/Install, changelog, restore,
  manager/Shizuku, dismiss, and theme-preset buttons no longer fall back to
  the gear; added a dedicated close icon, and the hero button icon now
  follows the active step.
- KernelSU verdict is now functional, not loader-based: after firing insmod
  the app verifies the module is live in sysfs AND that su grants root,
  instead of trusting the insmod exit code. Reports working / loaded-but-
  ungranted (with a Manager-approval hint) / failed; covered by host-runnable
  `VerdictTest` assertions in `tests/check.sh`.
- Split module-loaded from KSU-su-working in the Home state machine. The flow
  now stays on KernelSU until su actually grants uid 0, shows a warning chip
  while the module is live but ungranted, and only marks the Manager step done
  when a manager is installed and KSU is working.
- Non-root-flow jobs (app update, changelog, export, diagnostics) no longer
  clear or paint the Home root stepper's failure state.
- Theme color buttons are disabled while work is running, so palette changes
  cannot recreate the Activity and cancel an active exploit/update job.
- Helper download fallback logging now posts to the UI thread instead of
  mutating TextViews from a worker thread.
- `tests/check.sh` now makes verdict tests mandatory even from a clean tree and
  keeps the smoke-test debug keystore under `out/tests`.

## 5.0

### Removed
- Removed the experimental module/debloat features completely: navigation, UI,
  pending-verification logic, helper classes, stale tracked APK/checksum
  artifacts, and the old signing lineage file are gone.

### Added
- Persistent progress state with `SharedPreferences`: cached feed/selected
  payload, page, logs, visible status, and boot-scoped root/exploit/KernelSU
  state now survive app process restarts.
- Local-only `.signature-log` generated by `build.sh`, recording APK version,
  package, and signing fingerprint for every local build. The file is
  git-ignored and never published.
- View-based Material-style refresh: system-accent dynamic palette on modern
  Android, light/dark fallbacks, elevated rounded cards, action icons, tab
  transitions, button press motion, and root-grant confirmation animation.

### Fixed
- KernelSU root false negatives: root probing now uses `id -u` plus `id`
  fallback parsing, and after KernelSU loads the app re-probes `su` and marks
  root granted when KSU genuinely grants uid 0.
- App updates verify `app-update.json` SHA-256, re-check install permission at
  install time, and keep the install flow in-app unless Android blocks it.

## 4.7

### Fixed
- KernelSU verdict is now presence-based: a loader "Operation not permitted"
  warning no longer reports failure when /sys/module/kernelsu is live.

### Changed
- Simpler Home modeled on Root-My-Galaxy: one status card with a single
  context-aware action (check -> download -> root -> run -> KernelSU), a 4-step
  progress tracker, compact access rows, device card, and live log. Log and
  Settings keep history, feed, Shizuku tools, export, and app updates.

## 4.6

### Fixed
- Load KernelSU over Shizuku now streams the module over stdin instead of
  `cp` from app-private storage, which the shell user cannot read.
- App-update permission is shown in Settings, checked before downloading,
  and re-checked on resume.
- Exploit progress shows the latest live log line on the status view.
- Package-conflict sheet offers one-tap uninstall for incompatible installs.

### Changed
- Frozen app identity: package `com.bodo121.s22updater` is enforced by the
  build, and `SIGN_EXPECTED_CERT_SHA256` fails a release built with the wrong
  key.
