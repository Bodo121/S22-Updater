package com.bodo121.s22updater;

import java.io.IOException;

/** Output content is diagnostic data, never a success predicate. */
final class CommandResult {
    final int exitCode;
    final String stdout, stderr;
    final boolean timedOut;
    final Throwable exception;

    CommandResult(int exitCode, String stdout, String stderr, boolean timedOut, Throwable exception) {
        this.exitCode = exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.timedOut = timedOut;
        this.exception = exception;
    }
    boolean succeeded() { return exception == null && !timedOut && exitCode == 0; }
    String diagnostic() {
        return "exitCode=" + exitCode + " timedOut=" + timedOut
                + "\nstdout=" + (stdout.isEmpty() ? "<empty>" : stdout)
                + "\nstderr=" + (stderr.isEmpty() ? "<empty>" : stderr)
                + (exception == null ? "" : "\nexception=" + exception);
    }
    String requireSuccess() throws IOException, InterruptedException {
        if (exception instanceof InterruptedException || Thread.currentThread().isInterrupted())
            throw new InterruptedException("Command interrupted");
        if (!succeeded()) throw new IOException(diagnostic(), exception);
        return stdout.trim();
    }
}
