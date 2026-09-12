package com.bodo121.s22updater;

import static com.bodo121.s22updater.KernelSuController.*;

public final class VerdictTest {
    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        System.out.println(name + ": PASS");
    }
    static CommandResult cmd(int exit, String stdout, String stderr) {
        return new CommandResult(exit, stdout, stderr, false, null);
    }
    public static void main(String[] args) throws Exception {
        check(evaluate(cmd(0, "", ""), Presence.PRESENT).state == State.MODULE_LOADED, "testSilentInsmodSuccess");
        check(evaluate(cmd(0, "", ""), Presence.ABSENT).state == State.VERIFICATION_FAILED, "testMissingModuleAfterExitZero");
        check(evaluate(cmd(1, "", "EPERM"), Presence.ABSENT).state == State.LOAD_FAILED, "testRealLoadFailure");
        final int[] executions = {0};
        Result already = load(new Operations() {
            public Presence probe() { return Presence.PRESENT; }
            public CommandResult insmod() { executions[0]++; return cmd(0, "", ""); }
            public void pause() { }
        });
        check(already.loaded() && executions[0] == 0, "testAlreadyLoadedSkipsInsmod");
        check(evaluate(cmd(1, "", "File exists"), Presence.PRESENT).loaded(), "testAlreadyLoadedError");
        check(authorization(Presence.PRESENT, cmd(1, "", "denied")) == State.WAITING_FOR_MANAGER_PERMISSION, "testPermissionPending");
        check(authorization(Presence.PRESENT, cmd(0, "0\n", "")) == State.ROOT_GRANTED, "testFullRoot");
        check(!RootState.sameBoot("boot1", "boot2") && !RootState.sameBoot("unknown", "unknown"), "testBootReset");
        RootState restored = new RootState("boot1", false, Presence.PRESENT, false);
        check(restored.module == Presence.PRESENT && RootState.sameBoot("boot1", restored.bootId), "testRestartRediscovery");
        check(!RootState.uidZero("error uid=0 requested") && !RootState.uidZero("uid=01")
                && RootState.uidZero("uid=0(root) gid=0(root)"), "testUidParsing");
        check(evaluate(new CommandResult(-1, "", "", true, null), Presence.ABSENT).state == State.LOAD_FAILED, "testTimeout");
        check(evaluate(cmd(1, "", "permission denied"), Presence.UNKNOWN).state == State.VERIFICATION_FAILED, "testUnknownProbe");
        final int[] probes = {0};
        check(load(new Operations() {
            public Presence probe() { return ++probes[0] < 3 ? Presence.ABSENT : Presence.PRESENT; }
            public CommandResult insmod() { return cmd(0, "", ""); }
            public void pause() { }
        }).loaded(), "testDelayedModuleVisibility");
        System.out.println("PASS: functional KSU verdict mapping");
    }
}
