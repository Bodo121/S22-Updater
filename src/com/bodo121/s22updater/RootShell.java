package com.bodo121.s22updater;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class RootShell {
    static String quote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }

    static String run(String command, File cache) throws Exception {
        File output = File.createTempFile("root-", ".log", cache);
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true)
                    .redirectOutput(output).start();
            process.getOutputStream().close();
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IOException("Root request timed out");
            byte[] buffer = new byte[8192];
            String result;
            try (InputStream in = new FileInputStream(output)) {
                int n = in.read(buffer);
                result = n <= 0 ? "" : new String(buffer, 0, n, StandardCharsets.UTF_8).trim();
            }
            if (process.exitValue() != 0) throw new IOException("Root command failed: " + result);
            return result;
        } finally {
            if (process != null) process.destroyForcibly();
            output.delete();
        }
    }
}
