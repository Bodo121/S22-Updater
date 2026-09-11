package com.bodo121.s22updater;

import java.io.File;
import java.io.IOException;

/** Exact-build KernelSU late-load setup for SM-S901B / S901BXXSNGZD7. */
final class KernelSetup {
    static final String RELEASE = "5.10.237-android12-9-31999025-abS901BXXSNGZD7";
    static final String URL = "https://raw.githubusercontent.com/Bodo121/KSU-S22/main/kernelsu-r0s-S901BXXSNGZD7-kdp.ko";
    static final String SHA = "bab4be3cbb4fe3eac47f71852a8980c226a004a85e4ebc146971576277a9b97d";
    static final long SIZE = 432728;
    static final String DEVICE_MODULE = "/data/local/tmp/kernelsu.ko";

    static String status(Shell.Transport transport, File scratch) throws Exception {
        return transport.run(
                "if [ -d /sys/module/kernelsu ]; then printf loaded; else printf absent; fi", scratch);
    }

    /**
     * Tolerant presence check: insmod reports EEXIST ("File exists") when the
     * module is already live, and loader output varies, so sysfs presence —
     * matched loosely — is the verdict, never the loader's exit code.
     */
    static boolean isLoaded(Shell.Transport transport, File scratch) {
        try {
            String out = status(transport, scratch);
            return out != null && out.contains("loaded");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Pure functional verdict: whether KSU is live and working decides — the
     * insmod exit code never does (EEXIST when already live, EPERM quirks on
     * KDP). Kept side-effect free so host tests can assert the mapping.
     */
    static String verdict(boolean loaded, boolean suWorks, String lastError) {
        if (loaded && suWorks)
            return "KernelSU loaded and working for this boot: module live, su grants root";
        if (loaded)
            return "KernelSU loaded but su has not granted this app yet"
                    + " — approve it in the Manager, then tap Check root";
        String detail = lastError == null || lastError.isEmpty() ? "module not present" : lastError;
        return "KernelSU load failed: " + detail;
    }

    /** Verdict wording for the already-live fast path (same functional rule). */
    static String alreadyVerdict(boolean suWorks) {
        if (suWorks)
            return "KernelSU is already loaded and working for this boot";
        return "KernelSU is already loaded, but su has not granted this app yet"
                + " — approve it in the Manager";
    }

    /**
     * Loads the module through the given transport. Prefers the IONSTACK root
     * helper when a helper path is known (the KDP module needs the manual
     * loader); falls back to a plain insmod. Loader exit codes are recorded
     * for diagnostics only — success means KSU is verified working afterwards.
     */
    static String load(String model, Shell.Transport transport, File scratch, String helperPath)
            throws Exception {
        if (isLoaded(transport, scratch))
            return alreadyVerdict(Shell.suGrantsRoot(scratch));
        String release = transport.run("uname -r", scratch);
        if (!"SM-S901B".equals(model) || !RELEASE.equals(release))
            throw new IOException("This KernelSU module requires SM-S901B / S901BXXSNGZD7. Detected: "
                    + model + " / " + release);
        File module = File.createTempFile("kernelsu-", ".ko", scratch);
        try {
            Network.download(URL, module, null);
            PayloadStore.verify(module, SIZE, SHA);
            if (Thread.currentThread().isInterrupted())
                throw new java.io.InterruptedIOException("Cancelled");
            String staged = DEVICE_MODULE + ".s22-stage";
            String qStaged = Shell.quote(staged);
            String qModule = Shell.quote(module.getAbsolutePath());
            String qDevice = Shell.quote(DEVICE_MODULE);
            if (transport instanceof Shell.Su) {
                transport.run("set -e; cp " + qModule + " " + qStaged + "; chmod 644 " + qStaged,
                        scratch);
            } else {
                // A Shizuku shell (uid 2000) cannot read this app's private
                // files, so stream the bytes over stdin instead of cp.
                transport.writeFile(module, staged, "644");
            }
            transport.run("set -e; actual=$(sha256sum " + qStaged + "); [ \"${actual%% *}\" = '" + SHA
                    + "' ]; mv -f " + qStaged + " " + qDevice, scratch);
            // Fire the loader, but its exit code decides nothing: EEXIST when
            // already live, "Operation not permitted" quirks on KDP while
            // live. Afterwards, verify KSU is actually working (module live
            // AND su grants root) instead of trusting insmod.
            String lastError = "";
            if (helperPath != null && !helperPath.isEmpty() && !isLoaded(transport, scratch)) {
                try {
                    transport.run(Shell.quote(helperPath) + " -c "
                            + Shell.quote("insmod " + DEVICE_MODULE), scratch);
                } catch (Exception e) {
                    lastError = e.getMessage() == null ? "helper loader failed" : e.getMessage();
                }
            }
            if (!isLoaded(transport, scratch)) {
                try {
                    transport.run("insmod " + Shell.quote(DEVICE_MODULE), scratch);
                } catch (Exception e) {
                    lastError = e.getMessage() == null ? "loader failed" : e.getMessage();
                }
            }
            boolean live = isLoaded(transport, scratch);
            boolean works = live && Shell.suGrantsRoot(scratch);
            if (!live) throw new IOException(verdict(false, false, lastError));
            return verdict(true, works, lastError);
        } finally {
            module.delete();
        }
    }
}
