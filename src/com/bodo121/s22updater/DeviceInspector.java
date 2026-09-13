package com.bodo121.s22updater;

import android.content.*;
import android.os.*;
import java.io.*;
import java.security.MessageDigest;

/** All shell/file observations are collected on the workflow worker. */
final class DeviceInspector {
    static final class Snapshot {
        String manufacturer, model, codename, android, oneUi, firmware, fingerprint, kernel,
                kernelFull, patch, abi, selinux, bootId;
        long freeBytes;
        int battery;
        boolean supported() { return Compatibility.supported(manufacturer, model, firmware, kernel, abi); }
        String report() {
            return "Device: " + manufacturer + " " + model + " (" + codename + ")\n"
                    + "Compatibility: " + (supported() ? "SUPPORTED" : "UNSUPPORTED BUILD") + "\n"
                    + "Firmware: " + firmware + "\nKernel: " + kernel + "\nKernel full: " + kernelFull
                    + "\nAndroid: " + android + "\nOne UI property: " + oneUi
                    + "\nFingerprint: " + fingerprint + "\nSecurity patch: " + patch
                    + "\nArchitecture: " + abi + "\nSELinux: " + selinux
                    + "\nFree storage: " + freeBytes / (1024 * 1024) + " MiB " + (freeBytes < 64 * 1024 * 1024 ? "WARNING" : "SUPPORTED")
                    + "\nBattery: " + battery + "% " + (battery < 20 ? "WARNING" : "SUPPORTED")
                    + "\nBoot session hash: " + bootHash(bootId);
        }
    }
    static Snapshot inspect(Context context) {
        Snapshot d = new Snapshot();
        d.manufacturer = Build.MANUFACTURER; d.model = Build.MODEL; d.codename = Build.DEVICE;
        d.android = Build.VERSION.RELEASE; d.fingerprint = Build.FINGERPRINT;
        d.patch = Build.VERSION.SECURITY_PATCH; d.abi = Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0];
        File cache = context.getCacheDir();
        d.firmware = command(cache, "getprop ro.build.PDA");
        if (d.firmware.equals("unknown")) d.firmware = Build.DISPLAY;
        d.oneUi = command(cache, "getprop ro.build.version.oneui");
        d.kernel = command(cache, "uname -r"); d.kernelFull = command(cache, "uname -a");
        d.selinux = command(cache, "getenforce"); d.bootId = BootSessionStore.current(context);
        d.freeBytes = context.getFilesDir().getUsableSpace();
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int scale = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
        d.battery = scale <= 0 ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale;
        return d;
    }
    static String command(File scratch, String command) {
        CommandResult r = CommandRunner.local(new String[]{"/system/bin/sh", "-c", command}, scratch, 5000);
        return r.succeeded() && !r.stdout.trim().isEmpty() ? r.stdout.trim() : "unknown";
    }
    static String readBootId() {
        try (BufferedReader in = new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))) {
            String id = in.readLine(); return id == null ? "unknown" : id.trim();
        } catch (IOException e) { return "unknown"; }
    }
    static String bootHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) b.append(String.format("%02x", hash[i] & 255));
            return b.toString();
        } catch (Exception e) { return "unknown"; }
    }
}
