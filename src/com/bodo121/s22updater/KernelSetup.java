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
     * Loads the module through the given transport. Prefers the IONSTACK root
     * helper when a helper path is known (the KDP module needs the manual
     * loader); falls back to a plain insmod.
     */
    static String load(String model, Shell.Transport transport, File scratch, String helperPath)
            throws Exception {
        if (isLoaded(transport, scratch))
            return "KernelSU is already loaded for this boot";
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
            // The KDP manual loader can report "Operation not permitted" while
            // the module is actually live, so the final verdict is presence in
            // /sys/module/kernelsu — not the loader's exit code.
            String lastError = "";
            if (helperPath != null && !helperPath.isEmpty()) {
                try {
                    transport.run(Shell.quote(helperPath) + " -c "
                            + Shell.quote("insmod " + DEVICE_MODULE)
                            + " && test -d /sys/module/kernelsu", scratch);
                    return "KernelSU loaded and verified for this boot";
                } catch (Exception e) {
                    lastError = e.getMessage() == null ? "loader failed" : e.getMessage();
                }
            }
            try {
                transport.run("insmod " + Shell.quote(DEVICE_MODULE)
                        + " && test -d /sys/module/kernelsu", scratch);
                return "KernelSU loaded and verified for this boot";
            } catch (Exception e) {
                lastError = e.getMessage() == null ? "loader failed" : e.getMessage();
            }
            if (isLoaded(transport, scratch))
                return "KernelSU is loaded and verified (loader warned: " + lastError + ")";
            throw new IOException("KernelSU load failed: " + lastError);
        } finally {
            module.delete();
        }
    }
}
