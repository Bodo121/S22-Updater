package com.bodo121.s22updater;

import java.io.IOException;

/** Deliberately one exact supported profile, not a device-targeting framework. */
final class Compatibility {
    static final String TARGET = "r0s-S901BXXSNGZD7";
    static final String FIRMWARE = "S901BXXSNGZD7";
    static final String KERNEL = "5.10.237-android12-9-31999025-abS901BXXSNGZD7";
    static final String EXPLOIT_URL = "https://raw.githubusercontent.com/Bodo121/IONSTACK-S22/main/artifacts/r0s-S901BXXSNGZD7/cve-2026-43499-app.so";
    static final long EXPLOIT_SIZE = 1761728;
    static final String EXPLOIT_SHA = "290edd6f18b468030dcbe73e5b0541c7c3aa6a3ace8670d3aba1935179f99bd8";
    static final String HELPER_URL = "https://raw.githubusercontent.com/Bodo121/IONSTACK-S22/main/artifacts/r0s-S901BXXSNGZD7/cve-2026-43499-root";
    static final long HELPER_SIZE = 13960;
    static final String HELPER_SHA = "f28e778ac47a38826ccdfed8d49aaf40670a4af74ef10ac512414f844f9bdd6e";
    static boolean supported(String manufacturer, String model, String firmware, String kernel, String abi) {
        return "samsung".equalsIgnoreCase(manufacturer) && "SM-S901B".equals(model)
                && FIRMWARE.equals(firmware) && KERNEL.equals(kernel) && "arm64-v8a".equals(abi);
    }
    static void require(DeviceInspector.Snapshot device) throws IOException {
        if (device == null || !device.supported()) throw new IOException("UNSUPPORTED BUILD — requires Samsung SM-S901B / "
                + FIRMWARE + " / " + KERNEL + " / arm64-v8a. Run Device Doctor for detected values.");
    }
    static void requirePinned(String id, String url, long size, String sha,
                              String helperUrl, long helperSize, String helperSha) throws IOException {
        if (!TARGET.equals(id) || !EXPLOIT_URL.equals(url) || EXPLOIT_SIZE != size
                || !EXPLOIT_SHA.equalsIgnoreCase(sha) || !HELPER_URL.equals(helperUrl)
                || HELPER_SIZE != helperSize || !HELPER_SHA.equalsIgnoreCase(helperSha))
            throw new IOException("Unsigned compatibility feed is only accepted for the pinned v5.0-known S901BXXSNGZD7 payload");
    }
}
