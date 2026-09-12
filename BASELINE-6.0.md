# 6.0 compatibility baseline

Baseline: `v5.0`, `afbfb6fbf575542af2d70f08fb070f67c47a40fa`.
Starting revision: `bd51784a8445d001fdafd0fff04409c10c6abd5b`.

Compared `MainActivity.java`, `Shell.java`, `KernelSetup.java`, `PayloadStore.java`,
`Network.java`, `src/moe/shizuku/server`, `build.sh`, and release workflows using
`git diff v5.0..HEAD`. Shell, PayloadStore, Shizuku interfaces and build scripts
were unchanged. Network differed only in User-Agent. Root-runner changes were
UI job routing and posting fallback diagnostics to the UI thread.

Preserved execution contract:
- `/data/local/tmp/cve-2026-43499` and `cve-2026-43499-root` staging; mode 755;
  verified SHA before rename, Shizuku stdin streaming, su local-file copy.
- inherited environment and `/system/bin/sh` invocation;
  `EXPLOIT_ATTEMPTS=24`, `CVE43499_ROOT_HELPER`, `LD_PRELOAD` unchanged.
- `/data/local/tmp/cve-exploit.log`, combined stdout/stderr redirected there.
- 2-second log poll, 120-second stall watchdog, 15-minute total watchdog.
- completion marker followed by helper `-c 'id; getenforce'` verification.
- helper-first KernelSU loading and exact existing module URL/hash/size.

No offsets, primitives, race timing, binaries, or kernel internals changed.
6.0 extracts this code into ExploitRunner and adds preconditions/recovery around it.
Current View UI, themes, Shizuku, export, updater and signing identity are retained.

Baseline `./build.sh`: PASS before 6.0 source edits. `adb devices -l`: no devices.
Startup/real-device validation was unavailable, not claimed as passed.

False-failure audit: no empty-output failure condition exists in the inspected
v5.0/current loader. v5.0 KernelSetup.load coupled insmod with `&& test`, then
compared merged status text exactly to `loaded`. v5.1 replaced this with unsafe
`contains("loaded")` (also matches `unloaded`) and message-prefix/state coupling.
Shell.runLocal merged stderr into stdout and threw away structured exit results.
6.0 uses explicit probe markers and structured loader results; module presence
and application UID authorization are separate. The precise cause on the physical
device cannot be proven without its diagnostics.
