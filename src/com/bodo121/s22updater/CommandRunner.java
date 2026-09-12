package com.bodo121.s22updater;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Bounded concurrent pipe capture, with one deadline covering stdin and execution. */
final class CommandRunner {
    interface ProcessHandle {
        InputStream stdout();
        InputStream stderr();
        OutputStream stdin();
        boolean alive();
        int exitValue();
        void destroy();
    }
    interface Starter { ProcessHandle start() throws Exception; }
    static CommandResult local(String[] args, File dir, long timeout) {
        return run(() -> {
            Process p = new ProcessBuilder(args).directory(dir).start();
            return new ProcessHandle() {
                public InputStream stdout() { return p.getInputStream(); }
                public InputStream stderr() { return p.getErrorStream(); }
                public OutputStream stdin() { return p.getOutputStream(); }
                public boolean alive() { return p.isAlive(); }
                public int exitValue() { return p.exitValue(); }
                public void destroy() { p.destroyForcibly(); }
            };
        }, null, timeout);
    }
    static CommandResult run(Starter starter, File input, long timeout) {
        ProcessHandle p = null;
        Capture out = null, err = null;
        int exit = -1;
        Throwable problem = null;
        boolean expired = false;
        ExecutorService pipes = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "s22-command-pipe"); t.setDaemon(true); return t;
        });
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            p = starter.start();
            final ProcessHandle process = p;
            out = new Capture(p.stdout()); err = new Capture(p.stderr());
            Future<?> stdout = pipes.submit(out), stderr = pipes.submit(err);
            Future<?> stdin = pipes.submit(() -> {
                try (OutputStream target = process.stdin()) {
                    if (input != null) try (InputStream source = new FileInputStream(input)) {
                        byte[] bytes = new byte[8192]; int n;
                        while ((n = source.read(bytes)) != -1) target.write(bytes, 0, n);
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            while (p.alive()) {
                if (System.nanoTime() >= deadline) throw new TimeoutException();
                Thread.sleep(25);
            }
            exit = p.exitValue();
            for (Future<?> f : new Future<?>[]{stdin, stdout, stderr})
                f.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            expired = true;
        } catch (InterruptedException e) {
            problem = e; Thread.currentThread().interrupt();
        } catch (Exception e) {
            problem = e;
        } finally {
            if (p != null) {
                p.destroy();
                close(p.stdout()); close(p.stderr()); close(p.stdin());
            }
            pipes.shutdownNow();
        }
        return new CommandResult(exit, out == null ? "" : out.text(),
                err == null ? "" : err.text(), expired, problem);
    }
    private static void close(Closeable c) { try { if (c != null) c.close(); } catch (IOException ignored) { } }
    private static final class Capture implements Runnable {
        final InputStream input;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean truncated;
        Capture(InputStream input) { this.input = input; }
        public void run() {
            try {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) != -1) synchronized (this) {
                    int keep = Math.min(n, Math.max(0, 65536 - bytes.size()));
                    bytes.write(buffer, 0, keep); truncated |= keep < n;
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        synchronized String text() {
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8)
                    + (truncated ? "\n[output truncated at 64 KiB]" : "");
        }
    }
}
