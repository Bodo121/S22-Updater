package com.bodo121.s22updater;

/** Module installation and per-app authorization are independent. No Android dependencies. */
final class KernelSuController {
    enum State { NOT_LOADED, LOADING, MODULE_LOADED, WAITING_FOR_MANAGER_PERMISSION,
        ROOT_GRANTED, LOAD_FAILED, VERIFICATION_FAILED }
    enum Presence { PRESENT, ABSENT, UNKNOWN }
    interface Operations {
        Presence probe() throws Exception;
        CommandResult insmod() throws Exception;
        void pause() throws InterruptedException;
    }
    static final class Result {
        final State state;
        final CommandResult command;
        final Presence presence;
        final boolean alreadyLoaded;
        Result(State state, CommandResult command, Presence presence, boolean alreadyLoaded) {
            this.state = state; this.command = command; this.presence = presence; this.alreadyLoaded = alreadyLoaded;
        }
        boolean loaded() { return presence == Presence.PRESENT; }
        String message() {
            if (loaded()) return alreadyLoaded ? "KernelSU is already active." : "KernelSU module loaded successfully.";
            if (state == State.VERIFICATION_FAILED)
                return "KernelSU module could not be verified. Recheck module state before retrying.";
            return "KernelSU load failed. See command diagnostics.";
        }
    }
    static Result load(Operations ops) throws Exception {
        Presence before = ops.probe();
        if (before == Presence.PRESENT) return new Result(State.MODULE_LOADED, null, before, true);
        if (before == Presence.UNKNOWN) return new Result(State.VERIFICATION_FAILED, null, before, false);
        CommandResult result = ops.insmod();
        if (result.exception instanceof InterruptedException || Thread.currentThread().isInterrupted())
            throw new InterruptedException("KernelSU load interrupted");
        Presence after = Presence.UNKNOWN;
        for (int i = 0; i < 4; i++) {
            after = ops.probe();
            if (after == Presence.PRESENT) break;
            if (i < 3) ops.pause();
        }
        return evaluate(result, after);
    }
    static Result evaluate(CommandResult result, Presence after) {
        // Kernel state wins even for EEXIST, EPERM or a loader timeout after activation.
        State state = after == Presence.PRESENT ? State.MODULE_LOADED
                : result.succeeded() || after == Presence.UNKNOWN ? State.VERIFICATION_FAILED : State.LOAD_FAILED;
        return new Result(state, result, after, false);
    }
    static State authorization(Presence module, CommandResult su) {
        if (module != Presence.PRESENT) return module == Presence.ABSENT ? State.NOT_LOADED : State.VERIFICATION_FAILED;
        return su.succeeded() && RootState.uidZero(su.stdout) ? State.ROOT_GRANTED : State.WAITING_FOR_MANAGER_PERMISSION;
    }
}
