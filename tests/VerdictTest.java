package com.bodo121.s22updater;

import java.io.File;

/**
 * Host-runnable assertions for the functional KSU verdict: module state plus
 * su decides, never the insmod exit code. Runs on a plain JVM (no Android).
 */
public final class VerdictTest {
    private static void check(boolean cond, String name) {
        if (!cond) throw new AssertionError("Verdict: " + name);
        System.out.println("Verdict OK: " + name);
    }

    public static void main(String[] args) {
        // Loader exit codes never decide: ignored whenever the module is live.
        check(KernelSetup.verdict(true, true, "Exit 1: File exists")
                .startsWith("KernelSU loaded"), "live+su ignores loader error");
        check(KernelSetup.verdict(true, true, "").contains("working"), "live+su is working");
        check(!KernelSetup.verdict(true, false, "Exit 1: boom").contains("failed"),
                "live without su is not failure");
        check(KernelSetup.verdict(true, false, "").contains("Manager"),
                "live without su guides to Manager");
        check(KernelSetup.verdict(false, false, "Exit 1: EPERM")
                .startsWith("KernelSU load failed"), "absent is failure");
        check(KernelSetup.verdict(false, false, "Exit 1: EPERM").contains("EPERM"),
                "failure keeps loader detail");
        check(KernelSetup.verdict(false, false, "").contains("not present"),
                "empty error fallback");
        check(KernelSetup.alreadyVerdict(true).startsWith("KernelSU is"),
                "already prefix routes to adopt");
        check(KernelSetup.alreadyVerdict(false).contains("Manager"),
                "already without su guides to Manager");

        // Presence tolerance through a fake transport.
        check(KernelSetup.isLoaded(new FakeTransport("loaded"), new File("/tmp")),
                "isLoaded exact");
        check(KernelSetup.isLoaded(new FakeTransport("loaded plus extra words"), new File("/tmp")),
                "isLoaded tolerant");
        check(!KernelSetup.isLoaded(new FakeTransport("absent"), new File("/tmp")),
                "isLoaded absent");
        check(!KernelSetup.isLoaded(new FakeTransport(null, true), new File("/tmp")),
                "isLoaded throwing transport");

        System.out.println("PASS: functional KSU verdict mapping");
    }

    static final class FakeTransport implements Shell.Transport {
        private final String out;
        private final boolean fail;
        FakeTransport(String out) { this(out, false); }
        FakeTransport(String out, boolean fail) { this.out = out; this.fail = fail; }
        @Override public String name() { return "fake"; }
        @Override public String run(String command, File scratch) throws Exception {
            if (fail) throw new java.io.IOException("fake transport down");
            return out;
        }
        @Override public void writeFile(File source, String remotePath, String mode) { }
        @Override public Shell.Proc start(String[] cmd, String[] env) { return null; }
    }
}
