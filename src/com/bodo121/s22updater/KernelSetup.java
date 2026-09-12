package com.bodo121.s22updater;

import java.io.*;

/** Exact v5.0 module, helper-first loading; explicit post-condition verification. */
final class KernelSetup {
    static final String RELEASE = "5.10.237-android12-9-31999025-abS901BXXSNGZD7";
    static final String URL = "https://raw.githubusercontent.com/Bodo121/KSU-S22/main/kernelsu-r0s-S901BXXSNGZD7-kdp.ko";
    static final String SHA = "bab4be3cbb4fe3eac47f71852a8980c226a004a85e4ebc146971576277a9b97d";
    static final long SIZE = 432728;
    static final String DEVICE_MODULE = "/data/local/tmp/kernelsu.ko";
    static final String PROBE = "if [ -d /sys/module/kernelsu ]; then echo S22_KSU_PRESENT; "
            + "elif [ -r /proc/modules ]; then "
            + "if grep -q '^kernelsu ' /proc/modules; then echo S22_KSU_PRESENT; else echo S22_KSU_ABSENT; fi; "
            + "elif [ -r /sys/module ]; then echo S22_KSU_ABSENT; else echo S22_KSU_UNKNOWN; fi";
    interface Log { void line(String line); }

    static KernelSuController.Presence presence(Shell.Transport transport, File scratch) throws InterruptedException {
        CommandResult result = transport == null
                ? CommandRunner.local(new String[]{"/system/bin/sh", "-c", PROBE}, scratch, 5000)
                : transport.execute(PROBE, scratch, 5000);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (result.succeeded()) for (String line : result.stdout.split("\\n")) {
            if (line.trim().equals("S22_KSU_PRESENT")) return KernelSuController.Presence.PRESENT;
            if (line.trim().equals("S22_KSU_ABSENT")) return KernelSuController.Presence.ABSENT;
        }
        return KernelSuController.Presence.UNKNOWN;
    }
    static boolean isLoaded(Shell.Transport transport, File scratch) throws InterruptedException {
        return presence(transport, scratch) == KernelSuController.Presence.PRESENT;
    }
    static KernelSuController.Result loadVerified(String model, Shell.Transport transport, File scratch,
                                                   String helper, Log log) throws Exception {
        log.line("[KSU] Checking existing module");
        return KernelSuController.load(new KernelSuController.Operations() {
            public KernelSuController.Presence probe() throws Exception {
                KernelSuController.Presence state = presence(transport, scratch);
                if (state == KernelSuController.Presence.UNKNOWN && helper != null) {
                    CommandResult r = transport.execute(Shell.quote(helper) + " -c " + Shell.quote(PROBE), scratch, 5000);
                    if (r.succeeded() && r.stdout.trim().equals("S22_KSU_PRESENT")) state = KernelSuController.Presence.PRESENT;
                }
                log.line("[KSU] Module verification=" + state);
                return state;
            }
            public void pause() throws InterruptedException { Thread.sleep(250); }
            public CommandResult insmod() throws Exception {
                String release = transport.execute("uname -r", scratch, 5000).requireSuccess();
                if (!"SM-S901B".equals(model) || !RELEASE.equals(release))
                    throw new IOException("Unsupported KernelSU target: " + model + " / " + release);
                File module = File.createTempFile("kernelsu-", ".ko", scratch);
                try {
                    Network.download(URL, module, null);
                    PayloadStore.verify(module, SIZE, SHA);
                    String staged = DEVICE_MODULE + ".s22-stage";
                    if (transport instanceof Shell.Su)
                        transport.run("set -e; cp " + Shell.quote(module.getAbsolutePath()) + " " + Shell.quote(staged)
                                + "; chmod 644 " + Shell.quote(staged), scratch);
                    else transport.writeFile(module, staged, "644");
                    transport.run("set -e; actual=$(sha256sum " + Shell.quote(staged) + "); [ \"${actual%% *}\" = '"
                            + SHA + "' ]; mv -f " + Shell.quote(staged) + " " + Shell.quote(DEVICE_MODULE), scratch);
                    // Recheck after download/staging to avoid a concurrent duplicate load.
                    if (probe() == KernelSuController.Presence.PRESENT)
                        return new CommandResult(0, "", "", false, null);
                    String command = "insmod " + Shell.quote(DEVICE_MODULE);
                    if (helper != null && !helper.isEmpty()) command = Shell.quote(helper) + " -c " + Shell.quote(command);
                    log.line("[KSU] Running insmod via " + (helper == null ? transport.name() : "v5.0 helper"));
                    CommandResult r = transport.execute(command, scratch, 30000);
                    log.line("[KSU] " + r.diagnostic());
                    if (r.stdout.isEmpty()) log.line("[KSU] Empty stdout is normal; verifying module state");
                    return r;
                } finally { module.delete(); }
            }
        });
    }
    static String load(String model, Shell.Transport transport, File scratch, String helper) throws Exception {
        KernelSuController.Result result = loadVerified(model, transport, scratch, helper, line -> { });
        if (!result.loaded()) throw new IOException(result.message());
        return result.message();
    }
}
