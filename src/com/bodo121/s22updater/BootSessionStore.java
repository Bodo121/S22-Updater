package com.bodo121.s22updater;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/** Stable same-boot token cache. Never persists raw uptime or "unknown" as proof. */
final class BootSessionStore {
    private static String cached;

    static synchronized String current(Context context) {
        if (cached != null) return cached;
        String bootId = readBootId();
        if (validBootId(bootId)) return cached = "boot_id:" + bootId;
        String count = bootCount(context);
        if (count != null) return cached = "boot_count:" + count;
        return cached = "boot_epoch5m:" + bootEpochBucket();
    }

    static boolean sameBoot(String stored, String current) {
        return stored != null && current != null
                && !stored.isEmpty() && !current.isEmpty()
                && !"unknown".equals(stored) && !"unknown".equals(current)
                && stored.equals(current);
    }

    static boolean validBootId(String value) {
        return value != null && value.toLowerCase(Locale.ROOT)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    static String bootEpochBucketForTest(long wallMs, long elapsedMs) {
        long bootEpoch = Math.max(0L, wallMs - elapsedMs);
        return String.valueOf(bootEpoch / (5L * 60L * 1000L));
    }

    private static String bootEpochBucket() {
        return bootEpochBucketForTest(System.currentTimeMillis(), SystemClock.elapsedRealtime());
    }

    private static String readBootId() {
        try (BufferedReader in = new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))) {
            String line = in.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String bootCount(Context context) {
        try {
            int count = Settings.Global.getInt(context.getContentResolver(), "boot_count");
            return count >= 0 ? String.valueOf(count) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
