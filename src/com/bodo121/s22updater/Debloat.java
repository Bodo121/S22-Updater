package com.bodo121.s22updater;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/** Session-only system-app removal built on KernelSU su + overlay whiteouts. */
final class Debloat {
    static final String REMOTE_PATH = "/data/local/tmp/s22-debloat.sh";
    static final String LOG_PATH = "/data/local/tmp/s22-debloat.log";

    private static final String SCRIPT =
            "#!/system/bin/sh\n"
            + "LOG=/data/local/tmp/s22-debloat.log\n"
            + "STATE=/data/local/tmp/s22-debloat.state\n"
            + "BASE=/data/local/tmp/s22-debloat\n"
            + "say() { echo \"$1\"; echo \"$(date '+%H:%M:%S') $1\" >> \"$LOG\"; }\n"
            + "valid_pkg() { case \"$1\" in ''|android|com.android.systemui|com.android.settings|com.android.providers.settings|com.android.shell|com.google.android.packageinstaller|com.android.packageinstaller|com.bodo121.s22updater|*[!A-Za-z0-9._-]*) return 1;; *) return 0;; esac; }\n"
            + "root_check() { case \"$(id 2>/dev/null)\" in *uid=0*) return 0;; *) say 'ERROR not root'; return 1;; esac; }\n"
            + "paths_for() { cmd package path \"$1\" 2>/dev/null | while IFS= read -r l; do case \"$l\" in package:/system/*|package:/system_ext/*|package:/product/*|package:/vendor/*|package:/odm/*) echo \"${l#package:}\";; esac; done; }\n"
            + "overlay_mounted() { t=\"$1\"; while IFS= read -r line; do case \"$line\" in *\" $t \"*' - overlay '*) return 0;; esac; done < /proc/1/mountinfo 2>/dev/null; return 1; }\n"
            + "mount_overlay() {\n"
            + "  t=\"$1\"; key=\"$2\"; upper=\"$BASE/$key/upper\"; work=\"$BASE/$key/work\"\n"
            + "  overlay_mounted \"$t\" && return 0\n"
            + "  mkdir -p \"$upper\" \"$work\" || return 1\n"
            + "  nsenter -t 1 -m mount -t overlay overlay -o lowerdir=\"$t\",upperdir=\"$upper\",workdir=\"$work\" \"$t\" >>\"$LOG\" 2>&1\n"
            + "}\n"
            + "whiteout() {\n"
            + "  p=\"$1\"; case \"$p\" in\n"
            + "    /system_ext/*) t=/system_ext; key=system_ext; rel=\"${p#/system_ext/}\";;\n"
            + "    /system/*) t=/system; key=system; rel=\"${p#/system/}\";;\n"
            + "    /product/*) t=/product; key=product; rel=\"${p#/product/}\";;\n"
            + "    /vendor/*) t=/vendor; key=vendor; rel=\"${p#/vendor/}\";;\n"
            + "    /odm/*) t=/odm; key=odm; rel=\"${p#/odm/}\";;\n"
            + "    *) say \"WHITEOUT-SKIP unsupported path $p\"; return 1;;\n"
            + "  esac\n"
            + "  mount_overlay \"$t\" \"$key\" || { say \"WHITEOUT-FAIL mount $t\"; return 1; }\n"
            + "  up=\"$BASE/$key/upper/$rel\"; parent=\"${up%/*}\"\n"
            + "  mkdir -p \"$parent\" || return 1\n"
            + "  rm -f \"$up\"\n"
            + "  if mknod \"$up\" c 0 0 >>\"$LOG\" 2>&1; then say \"WHITEOUT-OK $p\"; else say \"WHITEOUT-FAIL $p\"; return 1; fi\n"
            + "}\n"
            + "unwhiteout() {\n"
            + "  p=\"$1\"; case \"$p\" in\n"
            + "    /system_ext/*) key=system_ext; rel=\"${p#/system_ext/}\";;\n"
            + "    /system/*) key=system; rel=\"${p#/system/}\";;\n"
            + "    /product/*) key=product; rel=\"${p#/product/}\";;\n"
            + "    /vendor/*) key=vendor; rel=\"${p#/vendor/}\";;\n"
            + "    /odm/*) key=odm; rel=\"${p#/odm/}\";;\n"
            + "    *) return 0;;\n"
            + "  esac\n"
            + "  rm -f \"$BASE/$key/upper/$rel\" && say \"RESTORE-WHITEOUT $p\"\n"
            + "}\n"
            + "restart_mode() { case \"$1\" in zygote) setprop ctl.restart zygote; say 'RESTART zygote';; android) (stop; sleep 3; start) >/dev/null 2>&1 & say 'RESTART android';; none|'') say 'RESTART none';; *) say \"ERROR bad restart mode $1\"; return 1;; esac; }\n"
            + "inspect() { pkg=\"$1\"; valid_pkg \"$pkg\" || { say 'ERROR invalid or protected package'; return 2; }; echo \"PACKAGE=$pkg\"; echo \"PATHS=$(paths_for \"$pkg\" | tr '\\n' ',' )\"; cmd package list packages --user 0 \"$pkg\" 2>/dev/null | while IFS= read -r l; do echo \"INSTALLED=$l\"; done; }\n"
            + "remove_pkg() {\n"
            + "  root_check || return 1; pkg=\"$1\"; mode=\"$2\"; valid_pkg \"$pkg\" || { say 'ERROR invalid or protected package'; return 2; }\n"
            + "  say \"REMOVE-START $pkg\"; paths=\"$(paths_for \"$pkg\")\"; [ -n \"$paths\" ] || say \"PATHS none visible for $pkg\"\n"
            + "  am force-stop --user 0 \"$pkg\" >>\"$LOG\" 2>&1 || true\n"
            + "  cmd appops set \"$pkg\" RUN_IN_BACKGROUND ignore >>\"$LOG\" 2>&1 || true\n"
            + "  cmd appops set \"$pkg\" RUN_ANY_IN_BACKGROUND ignore >>\"$LOG\" 2>&1 || true\n"
            + "  cmd package suspend --user 0 \"$pkg\" >>\"$LOG\" 2>&1 || true\n"
            + "  cmd package disable-user --user 0 \"$pkg\" >>\"$LOG\" 2>&1 || true\n"
            + "  cmd package uninstall --user 0 \"$pkg\" >>\"$LOG\" 2>&1 && say \"PM-UNINSTALL-OK $pkg\" || say \"PM-UNINSTALL-WARN $pkg\"\n"
            + "  fail=0; for p in $paths; do echo \"$pkg $p\" >> \"$STATE\"; whiteout \"$p\" || fail=1; done\n"
            + "  cmd package uninstall --user 0 \"$pkg\" >>\"$LOG\" 2>&1 || true\n"
            + "  restart_mode \"$mode\" || return 1\n"
            + "  [ \"$fail\" = 0 ] && say \"REMOVE-DONE $pkg\" || { say \"REMOVE-DEGRADED $pkg\"; return 1; }\n"
            + "}\n"
            + "restore_pkg() {\n"
            + "  root_check || return 1; pkg=\"$1\"; mode=\"$2\"; valid_pkg \"$pkg\" || { say 'ERROR invalid or protected package'; return 2; }\n"
            + "  say \"RESTORE-START $pkg\"\n"
            + "  [ -f \"$STATE\" ] && while IFS=' ' read -r sp p; do [ \"$sp\" = \"$pkg\" ] && unwhiteout \"$p\"; done < \"$STATE\"\n"
            + "  cmd package install-existing --user 0 \"$pkg\" >>\"$LOG\" 2>&1 || true\n"
            + "  cmd package unsuspend --user 0 \"$pkg\" >>\"$LOG\" 2>&1 || true\n"
            + "  cmd package enable --user 0 \"$pkg\" >>\"$LOG\" 2>&1 || true\n"
            + "  cmd appops set \"$pkg\" RUN_IN_BACKGROUND allow >>\"$LOG\" 2>&1 || true\n"
            + "  cmd appops set \"$pkg\" RUN_ANY_IN_BACKGROUND allow >>\"$LOG\" 2>&1 || true\n"
            + "  restart_mode \"$mode\" || return 1\n"
            + "  say \"RESTORE-DONE $pkg\"\n"
            + "}\n"
            + "verify_pkg() { pkg=\"$1\"; valid_pkg \"$pkg\" || { say 'ERROR invalid or protected package'; return 2; }; echo \"PACKAGE=$pkg\"; out=\"$(cmd package list packages --user 0 \"$pkg\" 2>/dev/null)\"; [ -z \"$out\" ] && echo USER0=removed || echo USER0=present; echo \"PATHS=$(paths_for \"$pkg\" | tr '\\n' ',' )\"; tail -c 1200 \"$LOG\" 2>/dev/null || true; }\n"
            + "case \"$1\" in inspect) inspect \"$2\";; remove) remove_pkg \"$2\" \"$3\";; restore) restore_pkg \"$2\" \"$3\";; verify) verify_pkg \"$2\";; *) echo \"usage: $0 inspect|remove|restore|verify <package> [zygote|android|none]\"; exit 2;; esac\n";

    static void requirePackage(String pkg) throws Exception {
        if (pkg == null || !pkg.matches("[A-Za-z0-9._-]{3,180}"))
            throw new java.io.IOException("Enter a valid package name, e.g. com.samsung.android.app.foo");
        if (pkg.equals("android") || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.settings")
                || pkg.equals("com.android.providers.settings")
                || pkg.equals("com.bodo121.s22updater"))
            throw new java.io.IOException("Refusing to debloat a critical package: " + pkg);
    }

    static void stage(Shell.Transport transport, File scratch) throws Exception {
        if (!(transport instanceof Shell.Su))
            throw new java.io.IOException("Debloat needs KernelSU su");
        File local = File.createTempFile("debloat-", ".sh", scratch);
        try {
            try (FileOutputStream out = new FileOutputStream(local)) {
                out.write(SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            String temp = REMOTE_PATH + ".s22-stage";
            transport.run("set -e; cp " + Shell.quote(local.getAbsolutePath()) + " "
                    + Shell.quote(temp) + "; chmod 755 " + Shell.quote(temp), scratch);
            String actual = transport.run("sha256sum " + Shell.quote(temp), scratch)
                    .split("\\s+")[0].trim();
            if (!scriptSha256().equalsIgnoreCase(actual))
                throw new java.io.IOException("Debloat runner hash mismatch");
            transport.run("mv -f " + Shell.quote(temp) + " " + Shell.quote(REMOTE_PATH), scratch);
        } finally {
            local.delete();
        }
    }

    static String inspect(Shell.Transport transport, File scratch, String pkg) throws Exception {
        requirePackage(pkg);
        stage(transport, scratch);
        return transport.run("sh " + Shell.quote(REMOTE_PATH) + " inspect " + Shell.quote(pkg), scratch);
    }

    static String remove(Shell.Transport transport, File scratch, String pkg, String restartMode)
            throws Exception {
        requirePackage(pkg);
        stage(transport, scratch);
        return transport.run("sh " + Shell.quote(REMOTE_PATH) + " remove " + Shell.quote(pkg)
                + " " + restartMode, scratch);
    }

    static String restore(Shell.Transport transport, File scratch, String pkg, String restartMode)
            throws Exception {
        requirePackage(pkg);
        stage(transport, scratch);
        return transport.run("sh " + Shell.quote(REMOTE_PATH) + " restore " + Shell.quote(pkg)
                + " " + restartMode, scratch);
    }

    static String verify(Shell.Transport transport, File scratch, String pkg) throws Exception {
        requirePackage(pkg);
        stage(transport, scratch);
        return transport.run("sh " + Shell.quote(REMOTE_PATH) + " verify " + Shell.quote(pkg), scratch);
    }

    private static String scriptSha256() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }
}
