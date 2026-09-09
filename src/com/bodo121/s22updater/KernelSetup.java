package com.bodo121.s22updater;

import java.io.File;
import java.io.IOException;

final class KernelSetup {
    static final String RELEASE = "5.10.237-android12-9-31999025-abS901BXXSNGZD7";
    static final String URL = "https://raw.githubusercontent.com/Bodo121/KSU-S22/main/kernelsu-r0s-S901BXXSNGZD7-kdp.ko";
    static final String SHA = "bab4be3cbb4fe3eac47f71852a8980c226a004a85e4ebc146971576277a9b97d";

    static String status(File cache) throws Exception {
        return RootShell.run("if [ -d /sys/module/kernelsu ]; then printf loaded; else printf absent; fi", cache);
    }

    static String load(String model, File cache) throws Exception {
        if ("loaded".equals(status(cache))) return "KernelSU is already loaded for this boot";
        String release = RootShell.run("uname -r", cache);
        if (!"SM-S901B".equals(model) || !RELEASE.equals(release))
            throw new IOException("This KernelSU module requires SM-S901B / S901BXXSNGZD7. Detected: " + model + " / " + release);
        File module = File.createTempFile("kernelsu-", ".ko", cache);
        try {
            Network.download(URL, module, null);
            PayloadStore.verify(module, 432728, SHA);
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Cancelled");
            RootShell.run("insmod " + RootShell.quote(module.getAbsolutePath())
                    + " && test -d /sys/module/kernelsu", cache);
            return "KernelSU loaded and verified for this boot";
        } finally { module.delete(); }
    }
}
