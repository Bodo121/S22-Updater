package com.bodo121.s22updater;

import java.io.File;
import java.security.MessageDigest;

/**
 * Experimental late module activation through KernelSU su.
 *
 * <p>A real reboot wipes volatile root, so reboot-required KernelSU modules
 * can never see a real boot here. This replays the parts that matter late:
 * apply module mounts in init's mount namespace, run module scripts, then
 * restart Android userspace (zygote or full stop/start). The kernel — and
 * therefore root and the loaded KSU module — survives a userspace restart.
 *
 * <p>Must run over {@link Shell.Su} after the CVE helper has handed off to
 * KernelSU. Modules opt in with files under /data/adb/modules/&lt;name&gt;
 * and an allowlist at {@link #ALLOW_PATH} (one module name per line).
 */
final class LateActivate {
    static final String REMOTE_PATH = "/data/local/tmp/ksu-late-activate.sh";
    static final String LOG_PATH = "/data/local/tmp/ksu-late-activate.log";
    static final String ALLOW_PATH = "/data/adb/late-modules.allow";
    static final String MODULES_DIR = "/data/adb/modules";
    static final int STEPS = 6;
    static final String[] STEP_NAMES = {
            "KernelSU loaded", "KernelSU su", "Stage runner",
            "Apply module mounts", "Replay module scripts", "Restart + verify"};

    interface StepListener {
        /** state is run, done, skip or fail. */
        void onStep(int index, String state, String detail);
    }

    /** Embedded POSIX runner staged to /data/local/tmp and verified by hash. */
    static final String SCRIPT =
            "#!/system/bin/sh\n"
            + "# ksu-late-activate.sh - late module activation for volatile KernelSU.\n"
            + "# Runs as root (KernelSU su). Mounts go to init's namespace (pid 1)\n"
            + "# so the restarted system_server inherits them.\n"
            + "LOG=/data/local/tmp/ksu-late-activate.log\n"
            + "ALLOW=/data/adb/late-modules.allow\n"
            + "MODDIR=/data/adb/modules\n"
            + "say() { echo \"$1\"; echo \"$(date '+%H:%M:%S') $1\" >> \"$LOG\"; }\n"
            + "ksu_state() { if [ -d /sys/module/kernelsu ]; then echo loaded; else echo absent; fi; }\n"
            + "su_state() { id 2>/dev/null | grep -q 'uid=0' && echo granted || echo denied; }\n"
            + "allow_list() {\n"
            + "  [ -f \"$ALLOW\" ] || return 0\n"
            + "  grep -v '^[[:space:]]*#' \"$ALLOW\" 2>/dev/null | grep -v '^[[:space:]]*$' 2>/dev/null | tr -d ' \\t\\r'\n"
            + "}\n"
            + "cmd_probe() {\n"
            + "  echo \"KSU=$(ksu_state)\"\n"
            + "  echo \"SU=$(su_state)\"\n"
            + "  mods=\"\"; for m in $(allow_list); do\n"
            + "    if [ -d \"$MODDIR/$m\" ]; then mods=\"$mods${mods:+,}$m\"; fi\n"
            + "  done\n"
            + "  echo \"MODULES=$mods\"\n"
            + "  if [ -e /proc/1/mountinfo ]; then echo \"INITNS=yes\"; else echo \"INITNS=no\"; fi\n"
            + "  ov=$(grep ' overlay ' /proc/1/mountinfo 2>/dev/null | awk '{print $5}' | tr '\\n' ',' | sed 's/,$//')\n"
            + "  echo \"OVERLAYS=$ov\"\n"
            + "}\n"
            + "cmd_mounts() {\n"
            + "  fail=0; count=0\n"
            + "  for m in $(allow_list); do\n"
            + "    d=\"$MODDIR/$m\"\n"
            + "    [ -d \"$d\" ] || { say \"MOUNT-SKIP $m (not installed)\"; continue; }\n"
            + "    [ -f \"$d/disable\" ] && { say \"MOUNT-SKIP $m (disabled)\"; continue; }\n"
            + "    [ -f \"$d/late-mounts.sh\" ] || { say \"MOUNT-SKIP $m (no late-mounts.sh)\"; continue; }\n"
            + "    count=$((count + 1))\n"
            + "    if nsenter -t 1 -m sh \"$d/late-mounts.sh\" >>\"$LOG\" 2>&1; then\n"
            + "      say \"MOUNT-OK $m\"\n"
            + "    else\n"
            + "      say \"MOUNT-FAIL $m (see $LOG)\"; fail=1\n"
            + "    fi\n"
            + "  done\n"
            + "  [ \"$count\" = 0 ] && say \"MOUNT-SKIP (allowlist empty or no modules provide late-mounts.sh)\"\n"
            + "  [ \"$fail\" = 0 ] || return 1\n"
            + "}\n"
            + "cmd_scripts() {\n"
            + "  fail=0; count=0\n"
            + "  for m in $(allow_list); do\n"
            + "    d=\"$MODDIR/$m\"\n"
            + "    [ -d \"$d\" ] || continue\n"
            + "    [ -f \"$d/disable\" ] && continue\n"
            + "    [ -f \"$d/late-post.sh\" ] || continue\n"
            + "    count=$((count + 1))\n"
            + "    if sh \"$d/late-post.sh\" >>\"$LOG\" 2>&1; then\n"
            + "      say \"SCRIPT-OK $m\"\n"
            + "    else\n"
            + "      say \"SCRIPT-FAIL $m (see $LOG)\"; fail=1\n"
            + "    fi\n"
            + "  done\n"
            + "  [ \"$count\" = 0 ] && say \"SCRIPT-SKIP (no modules provide late-post.sh)\"\n"
            + "  [ \"$fail\" = 0 ] || return 1\n"
            + "}\n"
            + "cmd_restart() {\n"
            + "  case \"$1\" in\n"
            + "    zygote) setprop ctl.restart zygote; echo \"RESTART-ISSUED zygote\";;\n"
            + "    android) (stop; sleep 3; start) >/dev/null 2>&1 & echo \"RESTART-ISSUED android\";;\n"
            + "    *) echo \"RESTART-FAIL unknown mode: $1\"; return 1;;\n"
            + "  esac\n"
            + "}\n"
            + "case \"$1\" in\n"
            + "  probe|mounts|scripts) \"cmd_$1\";;\n"
            + "  restart) cmd_restart \"$2\";;\n"
            + "  *) echo \"usage: $0 {probe|mounts|scripts|restart <zygote|android>}\"; exit 2;;\n"
            + "esac\n";

    static String scriptSha256() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private static String value(String output, String key) {
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim();
        }
        return "";
    }

    /**
     * Runs phases 0-5. The restart command is fire-and-forget: a userspace
     * restart kills this app's process, so the caller must persist a pending
     * flag and verify on the next launch.
     */
    static void run(Shell.Transport transport, File scratch, String restartMode,
                    StepListener listener) throws Exception {
        if (!(transport instanceof Shell.Su))
            throw new java.io.IOException(
                    "Late activation needs KernelSU su. Load KernelSU first — "
                    + "the exploit helper hands off to KernelSU and steps aside.");
        listener.onStep(0, "run", "Checking /sys/module/kernelsu…");
        if (!"loaded".equals(transport.run(
                "if [ -d /sys/module/kernelsu ]; then printf loaded; else printf absent; fi",
                scratch).trim()))
            throw fail(listener, 0, "KernelSU module is not loaded for this boot");
        listener.onStep(0, "done", "KernelSU module is loaded");

        listener.onStep(1, "run", "Verifying KernelSU su…");
        if (!transport.run("id", scratch).contains("uid=0"))
            throw fail(listener, 1, "su did not grant uid 0");
        listener.onStep(1, "done", "KernelSU su grants uid 0");

        listener.onStep(2, "run", "Staging runner…");
        File local = File.createTempFile("late-", ".sh", scratch);
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(local);
            try {
                out.write(SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            String want = scriptSha256();
            String temp = REMOTE_PATH + ".s22-stage";
            transport.run("set -e; cp " + Shell.quote(local.getAbsolutePath()) + " "
                    + Shell.quote(temp) + " && chmod 755 " + Shell.quote(temp), scratch);
            String actual = transport.run("sha256sum " + Shell.quote(temp), scratch);
            actual = actual.split("\\s+")[0].trim();
            if (!want.equalsIgnoreCase(actual))
                throw new java.io.IOException("Runner staging hash mismatch");
            transport.run("mv -f " + Shell.quote(temp) + " " + Shell.quote(REMOTE_PATH), scratch);
        } finally {
            local.delete();
        }
        listener.onStep(2, "done", "Runner staged and hash-verified");

        listener.onStep(3, "run", "Applying module mounts…");
        String mounts = transport.run("sh " + Shell.quote(REMOTE_PATH) + " mounts", scratch);
        if (mounts.contains("MOUNT-FAIL"))
            throw fail(listener, 3, firstLine(mounts, "MOUNT-FAIL"));
        listener.onStep(3, mounts.contains("MOUNT-OK") ? "done" : "skip",
                mounts.contains("MOUNT-OK") ? "Mounts applied" : "Nothing to mount (see log)");

        listener.onStep(4, "run", "Replaying module scripts…");
        String scripts = transport.run("sh " + Shell.quote(REMOTE_PATH) + " scripts", scratch);
        if (scripts.contains("SCRIPT-FAIL"))
            throw fail(listener, 4, firstLine(scripts, "SCRIPT-FAIL"));
        listener.onStep(4, scripts.contains("SCRIPT-OK") ? "done" : "skip",
                scripts.contains("SCRIPT-OK") ? "Module scripts replayed" : "No module scripts");

        listener.onStep(5, "run", "Issuing userspace restart (" + restartMode + ")…");
        String restart = transport.run(
                "sh " + Shell.quote(REMOTE_PATH) + " restart " + restartMode, scratch);
        if (!restart.contains("RESTART-ISSUED"))
            throw fail(listener, 5, "Restart was not issued");
        listener.onStep(5, "done", "Restart issued — reopen the app to verify");
    }

    /** Post-restart verification: su, module, overlays, log tail. */
    static String verify(Shell.Transport transport, File scratch) throws Exception {
        StringBuilder report = new StringBuilder();
        String id = transport.run("id", scratch);
        report.append("su: ").append(id.replace("\n", " ")).append('\n');
        if (!id.contains("uid=0")) {
            report.append("RESULT=lost\n");
            return report.toString();
        }
        String probe;
        try {
            probe = transport.run("sh " + Shell.quote(REMOTE_PATH) + " probe", scratch);
        } catch (Exception e) {
            probe = "KSU=" + transport.run(
                    "if [ -d /sys/module/kernelsu ]; then printf loaded; else printf absent; fi",
                    scratch).trim() + "\nSU=granted\nMODULES=?\nOVERLAYS=?";
        }
        report.append("kernelsu: ").append(value(probe, "KSU")).append('\n');
        report.append("modules: ").append(value(probe, "MODULES")).append('\n');
        report.append("init overlays: ").append(value(probe, "OVERLAYS")).append('\n');
        String tail;
        try {
            tail = transport.run("tail -c 1500 " + Shell.quote(LOG_PATH) + " 2>/dev/null || true",
                    scratch);
        } catch (Exception e) {
            tail = "";
        }
        if (!tail.isEmpty()) report.append("--- runner log ---\n").append(tail).append('\n');
        boolean ok = id.contains("uid=0") && !"absent".equals(value(probe, "KSU"));
        report.append("RESULT=").append(ok ? "active" : "degraded").append('\n');
        return report.toString();
    }

    private static java.io.IOException fail(StepListener listener, int index, String detail) {
        try {
            listener.onStep(index, "fail", detail);
        } catch (Exception ignored) {
        }
        return new java.io.IOException(detail);
    }

    private static String firstLine(String output, String prefix) {
        for (String line : output.split("\n")) {
            if (line.contains(prefix)) return line.trim();
        }
        return prefix;
    }
}
