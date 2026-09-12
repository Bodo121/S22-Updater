package com.bodo121.s22updater;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

/** Privileged command execution behind either root (su) or a Shizuku shell. */
final class Shell {
    interface Transport {
        String name();
        String run(String command, File scratch) throws Exception;
        default CommandResult execute(String command, File scratch, long timeout) {
            return CommandRunner.run(() -> start(new String[]{"/system/bin/sh", "-c", command}, null), null, timeout);
        }
        void writeFile(File source, String remotePath, String mode) throws Exception;
        Proc start(String[] cmd, String[] env) throws Exception;
    }

    interface Proc extends CommandRunner.ProcessHandle {
        InputStream stdout();
        InputStream stderr();
        OutputStream stdin();
        boolean alive();
        int waitFor(long timeoutMs) throws Exception;
        int exitValue();
        void destroy();
    }

    static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    // ---------- su transport ----------

    static final class Su implements Transport {
        @Override public CommandResult execute(String command, File scratch, long timeout) {
            return CommandRunner.local(new String[]{"su", "-c", command}, scratch, timeout);
        }
        @Override public String name() {
            return "root";
        }

        @Override public String run(String command, File scratch) throws Exception {
            return runLocal(new String[]{"su", "-c", command}, scratch, 30000);
        }

        @Override public void writeFile(File source, String remotePath, String mode) throws Exception {
            run("cat " + quote(source.getAbsolutePath()) + " > " + quote(remotePath)
                    + " && chmod " + mode + " " + quote(remotePath), source.getParentFile());
        }

        @Override public Proc start(String[] cmd, String[] env) throws Exception {
            throw new UnsupportedOperationException("direct exec is root-only via helper");
        }
    }

    static String runLocal(String[] command, File scratch, long timeoutMs) throws Exception {
        File output = File.createTempFile("cmd-", ".log", scratch);
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output).start();
            try {
                process.getOutputStream().close();
            } catch (Exception ignored) {
            }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS))
                throw new java.io.IOException("Command timed out");
            byte[] buffer = new byte[8192];
            String result;
            try (InputStream in = new FileInputStream(output)) {
                int n = in.read(buffer);
                result = n <= 0 ? "" : new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            if (process.exitValue() != 0)
                throw new java.io.IOException("Exit " + process.exitValue() + ": " + result);
            return result;
        } finally {
            if (process != null) process.destroyForcibly();
            output.delete();
        }
    }

    static boolean suGrantsRoot(File scratch) {
        try {
            return outputGrantsRoot(suRootProbe(scratch));
        } catch (Exception e) {
            return false;
        }
    }

    static String suRootProbe(File scratch) throws Exception {
        return runLocal(new String[]{"su", "-c",
                "id -u 2>/dev/null; id; [ -d /sys/module/kernelsu ] && echo KSU=loaded || true"},
                scratch, 15000);
    }

    static boolean outputGrantsRoot(String output) {
        if (output == null) return false;
        for (String line : output.split("\\n")) {
            if (line.trim().equals("0") || line.trim().matches("uid=0(?:\\([^)]*\\))?(?:\\s.*)?")) return true;
        }
        return false;
    }

    // ---------- Shizuku transport ----------

    static final class ShizukuShell implements Transport {
        private final IShizukuService service;

        ShizukuShell(IShizukuService service) {
            this.service = service;
        }

        @Override public String name() {
            return "shizuku";
        }

        @Override public String run(String command, File scratch) throws Exception {
            Proc proc = start(new String[]{"/system/bin/sh", "-c", command}, null);
            try {
                ByteArrayOutputStream captured = new ByteArrayOutputStream();
                drain(proc, captured);
                int exit = proc.waitFor(60000);
                String out = new String(captured.toByteArray(),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (exit != 0) throw new java.io.IOException("Exit " + exit + ": " + out);
                return out;
            } finally {
                proc.destroy();
            }
        }

        @Override public void writeFile(File source, String remotePath, String mode) throws Exception {
            Proc proc = start(new String[]{"/system/bin/sh", "-c",
                    "cat > " + quote(remotePath) + " && chmod " + mode + " " + quote(remotePath)}, null);
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = proc.stdin()) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            drain(proc, captured);
            int exit = proc.waitFor(120000);
            if (exit != 0)
                throw new java.io.IOException("Stage failed (exit " + exit + "): "
                        + new String(captured.toByteArray(),
                                java.nio.charset.StandardCharsets.UTF_8).trim());
        }

        @Override public Proc start(String[] cmd, String[] env) throws Exception {
            if (service == null)
                throw new java.io.IOException("Shizuku service is gone; re-authorize and retry");
            IRemoteProcess remote;
            try {
                remote = service.newProcess(cmd, env, null);
            } catch (android.os.RemoteException e) {
                throw new java.io.IOException("Shizuku exec failed: " + e.getMessage());
            } catch (SecurityException e) {
                throw new java.io.IOException(
                        "Shizuku denied the request: authorize this app first");
            } catch (RuntimeException e) {
                throw new java.io.IOException("Shizuku exec failed (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage())
                        + "); the Shizuku version may no longer support shell processes");
            }
            if (remote == null)
                throw new java.io.IOException("Shizuku refused the process (empty reply). "
                        + "Grant permission in Shizuku, or update Shizuku: newProcess "
                        + "support varies by server version.");
            try {
                return new RemoteProc(remote);
            } catch (android.os.RemoteException e) {
                throw new java.io.IOException("Shizuku process has no usable streams: "
                        + e.getMessage());
            }
        }

        private static void drain(Proc proc, ByteArrayOutputStream captured) throws Exception {
            byte[] buffer = new byte[8192];
            long deadline = System.currentTimeMillis() + 60000;
            while (proc.alive() && System.currentTimeMillis() < deadline) {
                boolean progress = false;
                while (proc.stdout().available() > 0) {
                    int n = proc.stdout().read(buffer);
                    if (n <= 0) break;
                    captured.write(buffer, 0, n);
                    progress = true;
                }
                while (proc.stderr().available() > 0) {
                    int n = proc.stderr().read(buffer);
                    if (n <= 0) break;
                    captured.write(buffer, 0, n);
                    progress = true;
                }
                if (!progress) Thread.sleep(50);
            }
            while (proc.stdout().available() > 0) {
                int n = proc.stdout().read(buffer);
                if (n <= 0) break;
                captured.write(buffer, 0, n);
            }
            while (proc.stderr().available() > 0) {
                int n = proc.stderr().read(buffer);
                if (n <= 0) break;
                captured.write(buffer, 0, n);
            }
        }
    }

    static final class RemoteProc implements Proc {
        private final IRemoteProcess remote;
        private final InputStream in;
        private final InputStream err;
        private final OutputStream out;

        RemoteProc(IRemoteProcess remote) throws Exception {
            if (remote == null)
                throw new java.io.IOException("Shizuku returned no remote process");
            this.remote = remote;
            try {
                in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream());
                err = new android.os.ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream());
                out = new android.os.ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream());
            } catch (NullPointerException e) {
                destroy();
                throw new java.io.IOException(
                        "Shizuku returned a process without streams", e);
            } catch (android.os.RemoteException e) {
                destroy();
                throw new java.io.IOException("Shizuku stream failed: " + e.getMessage());
            }
        }

        @Override public InputStream stdout() {
            return in;
        }

        @Override public InputStream stderr() {
            return err;
        }

        @Override public OutputStream stdin() {
            return out;
        }

        @Override public boolean alive() {
            try {
                return remote.alive();
            } catch (android.os.RemoteException e) {
                return false;
            }
        }

        @Override public int waitFor(long timeoutMs) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (alive()) {
                if (System.currentTimeMillis() > deadline)
                    throw new java.io.IOException("Process timed out");
                Thread.sleep(100);
            }
            return exitValue();
        }

        @Override public int exitValue() {
            try {
                return remote.exitValue();
            } catch (android.os.RemoteException e) {
                return -1;
            }
        }

        @Override public void destroy() {
            try {
                remote.destroy();
            } catch (android.os.RemoteException ignored) {
            }
            try {
                in.close();
            } catch (Exception ignored) {
            }
            try {
                err.close();
            } catch (Exception ignored) {
            }
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- Shizuku availability / permission ----------

    static boolean shizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    static void requestShizukuBinder(android.content.Context context) {
        try {
            ShizukuProvider.requestBinderForNonProviderProcess(
                    context.getApplicationContext());
        } catch (Throwable ignored) {
        }
    }

    static boolean awaitShizukuRunning(android.content.Context context, long timeoutMs) {
        requestShizukuBinder(context);
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        do {
            if (shizukuRunning()) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return shizukuRunning();
    }

    static boolean shizukuGranted() {
        try {
            return shizukuRunning()
                    && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static IShizukuService shizukuService() {
        try {
            android.os.IBinder binder = Shizuku.getBinder();
            return IShizukuService.Stub.asInterface(binder);
        } catch (Throwable t) {
            return null;
        }
    }

    interface PermissionCallback {
        void onResult(boolean granted);
    }

    static void requestShizukuPermission(final PermissionCallback callback) {
        final AtomicInteger requestCode = new AtomicInteger(0x5352);
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};
        final Shizuku.OnRequestPermissionResultListener[] holder =
                new Shizuku.OnRequestPermissionResultListener[1];
        holder[0] = new Shizuku.OnRequestPermissionResultListener() {
            @Override public void onRequestPermissionResult(int code, int grantResult) {
                if (code == requestCode.get()) {
                    try {
                        Shizuku.removeRequestPermissionResultListener(holder[0]);
                    } catch (Throwable ignored) {
                    }
                    result[0] = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED;
                    latch.countDown();
                }
            }
        };
        try {
            Shizuku.addRequestPermissionResultListener(holder[0]);
        } catch (Throwable t) {
            callback.onResult(false);
            return;
        }
        try {
            Shizuku.requestPermission(requestCode.get());
        } catch (Throwable t) {
            try {
                Shizuku.removeRequestPermissionResultListener(holder[0]);
            } catch (Throwable ignored) {
            }
            callback.onResult(false);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    latch.await(120, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    Shizuku.removeRequestPermissionResultListener(holder[0]);
                } catch (Throwable ignored) {
                }
                callback.onResult(result[0]);
            }
        }).start();
    }

    static String describeShizukuState() {
        if (!shizukuRunning())
            return "Shizuku is not running (install Shizuku and start it via wireless debugging)";
        if (!shizukuGranted()) return "Shizuku is running but this app is not authorized";
        return "Shizuku shell ready";
    }
}
