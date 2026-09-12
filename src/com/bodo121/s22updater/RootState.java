package com.bodo121.s22updater;

/** Observations, not preferences, determine current privileges. */
final class RootState {
    enum Status { NOT_ROOTED, TEMP_ROOT_ACTIVE, KSU_MODULE_LOADED, KSU_PERMISSION_PENDING, KSU_ROOT_ACTIVE }
    final boolean temporaryRoot, suGranted;
    final KernelSuController.Presence module;
    final String bootId;
    RootState(String bootId, boolean temporaryRoot, KernelSuController.Presence module, boolean suGranted) {
        this.bootId = bootId; this.temporaryRoot = temporaryRoot; this.module = module; this.suGranted = suGranted;
    }
    Status status() {
        if (module == KernelSuController.Presence.PRESENT)
            return suGranted ? Status.KSU_ROOT_ACTIVE : Status.KSU_PERMISSION_PENDING;
        return temporaryRoot || suGranted ? Status.TEMP_ROOT_ACTIVE : Status.NOT_ROOTED;
    }
    static boolean sameBoot(String stored, String actual) {
        return actual != null && !actual.isEmpty() && !actual.equals("unknown") && actual.equals(stored);
    }
    static boolean uidZero(String output) {
        if (output == null) return false;
        for (String line : output.split("\\n"))
            if (line.trim().equals("0") || line.trim().matches("uid=0(?:\\([^)]*\\))?(?:\\s.*)?")) return true;
        return false;
    }
}
