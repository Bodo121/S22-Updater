package com.bodo121.s22updater;

import java.util.EnumMap;

/** Explicit state/step outcomes. Mutated on worker, immutable reports consumed by UI. */
final class RootWorkflowController {
    enum Step { IDLE, CHECKING_DEVICE, CHECKING_COMPATIBILITY, FETCHING_FEED, CHECKING_CACHE,
        DOWNLOADING_PAYLOAD, VERIFYING_PAYLOAD, STAGING_PAYLOAD, CHECKING_ROOT, RUNNING_EXPLOIT,
        VERIFYING_TEMP_ROOT, CHECKING_KSU, LOADING_KSU, VERIFYING_KSU, WAITING_KSU_PERMISSION,
        VERIFYING_KSU_ROOT, COMPLETE, FAILED }
    enum Outcome { NOT_STARTED, RUNNING, SUCCESS, WARNING, FAILURE }
    enum Action { CHECK_DEVICE, UNSUPPORTED, FETCH_FEED, DOWNLOAD, AUTHORIZE_SHELL, RUN_ROOT,
        LOAD_KSU, OPEN_MANAGER, COMPLETE }
    private final EnumMap<Step, Outcome> outcomes = new EnumMap<>(Step.class);
    private Step current = Step.IDLE;
    private Step lastFailure;
    private String detail = "";
    synchronized void enter(Step next) {
        if (outcomes.get(current) == Outcome.RUNNING) outcomes.put(current, Outcome.SUCCESS);
        current = next; outcomes.put(next, Outcome.RUNNING);
    }
    synchronized void finish(boolean warning) { outcomes.put(current, warning ? Outcome.WARNING : Outcome.SUCCESS); }
    synchronized void fail(Throwable e) {
        lastFailure = current; outcomes.put(current, Outcome.FAILURE);
        detail = e.toString(); current = Step.FAILED;
    }
    synchronized String report() {
        return "Workflow: " + current + "\nSteps: " + outcomes + (lastFailure == null ? "" : "\nLast failure: " + lastFailure + " " + detail);
    }
    static Action next(boolean checked, boolean compatible, boolean feed, boolean cached,
                       boolean transport, RootState root) {
        if (!checked) return Action.CHECK_DEVICE;
        if (!compatible) return Action.UNSUPPORTED;
        if (root.module == KernelSuController.Presence.PRESENT)
            return root.suGranted ? Action.COMPLETE : Action.OPEN_MANAGER;
        if (root.temporaryRoot || root.suGranted) return Action.LOAD_KSU;
        if (!feed) return Action.FETCH_FEED;
        if (!cached) return Action.DOWNLOAD;
        if (!transport) return Action.AUTHORIZE_SHELL;
        return Action.RUN_ROOT;
    }
}
