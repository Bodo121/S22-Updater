package com.bodo121.s22updater;
import java.io.File;
public final class CommandTest {
    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        CommandResult silent = CommandRunner.local(new String[]{"/bin/sh", "-c", "true"}, dir, 3000);
        VerdictTest.check(silent.succeeded() && silent.stdout.isEmpty() && silent.stderr.isEmpty(), "testSilentCommand");
        CommandResult separate = CommandRunner.local(new String[]{"/bin/sh", "-c", "printf out; printf err >&2; exit 7"}, dir, 3000);
        VerdictTest.check(separate.exitCode == 7 && separate.stdout.equals("out") && separate.stderr.equals("err"), "testSeparateStreams");
        CommandResult flood = CommandRunner.local(new String[]{"/bin/sh", "-c", "i=0; while [ $i -lt 10000 ]; do printf 'stdout line\\n'; printf 'stderr line\\n' >&2; i=$((i+1)); done"}, dir, 5000);
        VerdictTest.check(flood.succeeded() && flood.stdout.contains("truncated") && flood.stderr.contains("truncated"), "testPipeFloodNoDeadlock");
        CommandResult timeout = CommandRunner.local(new String[]{"/bin/sleep", "10"}, dir, 80);
        VerdictTest.check(timeout.timedOut, "testProcessTimeout");
        CommandResult missing = CommandRunner.local(new String[]{"/nonexistent-s22-command"}, dir, 1000);
        VerdictTest.check(missing.exception != null, "testExecutionException");
        Thread.currentThread().interrupt();
        CommandResult interrupted = CommandRunner.local(new String[]{"/bin/true"}, dir, 1000);
        VerdictTest.check(interrupted.exception instanceof InterruptedException && Thread.interrupted(), "testInterruption");
    }
}
